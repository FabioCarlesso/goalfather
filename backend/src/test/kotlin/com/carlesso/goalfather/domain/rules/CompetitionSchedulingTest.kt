package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.KnockoutStage
import com.carlesso.goalfather.domain.model.LegScore
import com.carlesso.goalfather.domain.model.Legs
import com.carlesso.goalfather.domain.model.Pairing
import com.carlesso.goalfather.domain.model.SwissPlacement
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompetitionSchedulingTest {

    private fun clubs(n: Int) = (1..n).map { ClubId(it.toLong()) }

    /** Toda rodada: nenhum clube aparece duas vezes. */
    private fun assertNoClubTwicePerRound(schedule: List<List<Pairing>>) {
        schedule.forEachIndexed { r, round ->
            val ids = round.flatMap { listOf(it.home, it.away) }
            assertEquals(ids.size, ids.toSet().size, "Rodada ${r + 1} repete clube: $round")
        }
    }

    // ─── Pontos corridos ──────────────────────────────────────────────

    @Test
    fun `turno e returno com 20 clubes da 38 rodadas e cada par se enfrenta uma vez em cada mando`() {
        val schedule = roundRobinSchedule(clubs(20), legs = 2)

        assertEquals(38, schedule.size)
        assertTrue(schedule.all { it.size == 10 })
        assertNoClubTwicePerRound(schedule)
        val all = schedule.flatten()
        assertEquals(380, all.toSet().size, "Algum jogo (mesmo mando) se repete")
        // Cada clube: 19 em casa e 19 fora.
        clubs(20).forEach { club ->
            assertEquals(19, all.count { it.home == club })
            assertEquals(19, all.count { it.away == club })
        }
    }

    @Test
    fun `returno espelha o turno com o mando invertido`() {
        val schedule = roundRobinSchedule(clubs(6), legs = 2)
        val turn = schedule.take(5)
        val returno = schedule.drop(5)
        assertEquals(turn.map { r -> r.map(Pairing::reversed) }, returno)
    }

    @Test
    fun `numero impar de clubes gera uma folga por rodada`() {
        val schedule = roundRobinSchedule(clubs(5), legs = 1)

        assertEquals(5, schedule.size)
        assertTrue(schedule.all { it.size == 2 })
        assertNoClubTwicePerRound(schedule)
        // Cada clube folga exatamente uma vez e enfrenta os outros 4.
        clubs(5).forEach { club -> assertEquals(4, schedule.flatten().count { club == it.home || club == it.away }) }
    }

    @Test
    fun `ordem de entrada nao muda a tabela`() {
        assertEquals(roundRobinSchedule(clubs(8), 2), roundRobinSchedule(clubs(8).shuffled(Random(7)), 2))
    }

    // ─── Mata-mata ────────────────────────────────────────────────────

    @Test
    fun `sorteio de mata-mata com a mesma seed e deterministico e usa todos os clubes`() {
        val a = drawKnockoutPairings(clubs(16), Random(42))
        val b = drawKnockoutPairings(clubs(16).reversed(), Random(42))

        assertEquals(a, b)
        assertEquals(8, a.size)
        assertEquals(clubs(16).toSet(), a.flatMap { listOf(it.home, it.away) }.toSet())
    }

    @Test
    fun `ida e volta inverte o mando e jogo unico tem uma rodada so`() {
        val pairings = listOf(Pairing(ClubId(1), ClubId(2)))
        assertEquals(listOf(pairings), knockoutLegs(pairings, Legs.SINGLE))
        assertEquals(listOf(pairings, listOf(Pairing(ClubId(2), ClubId(1)))), knockoutLegs(pairings, Legs.TWO_LEGGED))
    }

    @Test
    fun `agregado empatado na volta vai aos penaltis, sem gol fora de casa`() {
        // Ida 2×1 para o A; volta 1×0 para o B (B fez o gol em casa): agregado 2×2.
        assertTrue(secondLegNeedsShootout(LegScore(2, 1), LegScore(1, 0)))
        // Ida 1×1, volta 2×2: agregado 3×3 — com gol fora valeria para o A; aqui vai aos pênaltis.
        assertTrue(secondLegNeedsShootout(LegScore(1, 1), LegScore(2, 2)))
        assertFalse(secondLegNeedsShootout(LegScore(2, 0), LegScore(1, 0)))
    }

    // ─── Grupos ───────────────────────────────────────────────────────

    private val libertadores = CompetitionFormat.GroupsThenKnockout(
        groups = 8,
        perGroup = 4,
        qualifiersPerGroup = 2,
        knockout = CompetitionFormat.Knockout(
            listOf(
                KnockoutStage("Oitavas", 16, Legs.TWO_LEGGED, newEntrants = 16),
                KnockoutStage("Quartas", 8, Legs.TWO_LEGGED),
                KnockoutStage("Semifinal", 4, Legs.TWO_LEGGED),
                KnockoutStage("Final", 2, Legs.SINGLE),
            ),
        ),
    )

    @Test
    fun `grupos de 4 por potes, cada clube faz 6 jogos e cada grupo tem um de cada pote`() {
        val seeded = clubs(32)
        val groups = drawGroups(seeded, libertadores, Random(1))

        assertEquals(8, groups.size)
        assertTrue(groups.all { it.size == 4 })
        assertEquals(seeded.toSet(), groups.flatten().toSet())
        // Pote k = posições 8k..8k+7 do ranking: um por grupo.
        groups.forEach { group ->
            assertEquals(listOf(0, 1, 2, 3), group.map { (seeded.indexOf(it)) / 8 })
        }

        val schedule = groupStageSchedule(groups, legs = 2)
        assertEquals(6, schedule.size)
        assertNoClubTwicePerRound(schedule)
        seeded.forEach { club -> assertEquals(6, schedule.flatten().count { club == it.home || club == it.away }) }
    }

    // ─── Fase de liga suíça ───────────────────────────────────────────

    private val champions = CompetitionFormat.SwissLeague(
        teams = 36,
        matchesPerTeam = 8,
        directQualifiers = 8,
        playoffRange = 9..24,
    )

    @Test
    fun `suico da 8 jogos por clube, 4 em casa e 4 fora, sem repetir adversario`() {
        val schedule = swissLeagueSchedule(clubs(36), champions, Random(2024))

        assertEquals(8, schedule.size)
        assertTrue(schedule.all { it.size == 18 })
        assertNoClubTwicePerRound(schedule)
        val all = schedule.flatten()
        clubs(36).forEach { club ->
            val games = all.filter { club == it.home || club == it.away }
            assertEquals(8, games.size)
            assertEquals(4, games.count { it.home == club })
            val opponents = games.map { if (it.home == club) it.away else it.home }
            assertEquals(8, opponents.toSet().size, "Clube ${club.value} repete adversário")
        }
    }

    @Test
    fun `suico e deterministico por seed e muda com a seed`() {
        assertEquals(swissLeagueSchedule(clubs(36), champions, Random(1)), swissLeagueSchedule(clubs(36), champions, Random(1)))
        assertTrue(swissLeagueSchedule(clubs(36), champions, Random(1)) != swissLeagueSchedule(clubs(36), champions, Random(2)))
    }

    @Test
    fun `posicao na fase de liga decide oitavas, playoff ou eliminacao`() {
        assertEquals(SwissPlacement.DIRECT_TO_KNOCKOUT, swissPlacement(champions, 1))
        assertEquals(SwissPlacement.DIRECT_TO_KNOCKOUT, swissPlacement(champions, 8))
        assertEquals(SwissPlacement.PLAYOFF, swissPlacement(champions, 9))
        assertEquals(SwissPlacement.PLAYOFF, swissPlacement(champions, 24))
        assertEquals(SwissPlacement.ELIMINATED, swissPlacement(champions, 25))
        assertEquals(SwissPlacement.ELIMINATED, swissPlacement(champions, 36))
    }
}
