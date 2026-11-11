package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.SeasonCompetitions
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.md
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoundFixturesGeneratorTest {

    private val sixClubs = (1L..6L).map { makeClub(id = it, name = "Club $it") }

    @Test
    fun `6 clubes geram 3 partidas por rodada`() {
        val round = generateRound(matchday = md(1), season = 2026, clubs = sixClubs)
        assertEquals(3, round.matches.size)
        assertEquals(RoundStatus.Scheduled, round.status)
        assertEquals(md(1), round.matchday)
        assertEquals(2026, round.season)
    }

    @Test
    fun `turno de 5 rodadas cobre cada confronto exatamente uma vez`() {
        // Round-robin de turno único: C(6,2) = 15 confrontos em 5 rodadas de 3 jogos.
        val seenPairs = mutableSetOf<Set<Long>>()
        for (n in 1..5) {
            val round = generateRound(md(n), season = 2026, clubs = sixClubs, leagueRound = n)
            for (m in round.matches) {
                val pair = setOf(m.homeClubId.value, m.awayClubId.value)
                assertTrue(seenPairs.add(pair), "Par repetido no turno: $pair (rodada $n)")
                assertTrue(m.homeClubId != m.awayClubId, "Clube enfrentando a si mesmo")
            }
        }
        assertEquals(15, seenPairs.size, "Turno deve ter 15 confrontos distintos")
    }

    @Test
    fun `clube de menor id permanece fixo (Berger)`() {
        // O clube 1 aparece em todas as rodadas (nunca folga num turno par).
        for (n in 1..5) {
            val round = generateRound(md(n), season = 2026, clubs = sixClubs, leagueRound = n)
            val clubsInRound = round.matches.flatMap { listOf(it.homeClubId.value, it.awayClubId.value) }
            assertTrue(1L in clubsInRound, "Clube 1 deveria jogar na rodada $n")
        }
    }

    @Test
    fun `mesmo input produz exatamente o mesmo schedule (determinismo)`() {
        val a = generateRound(md(3), 2026, sixClubs, leagueRound = 3)
        val b = generateRound(md(3), 2026, sixClubs.shuffled(), leagueRound = 3) // ordem de entrada não importa
        assertEquals(a, b)
    }

    @Test
    fun `matchId segue o padrao encaixe 1000 + divisao 100 + indice`() {
        val round = generateRound(md(2), 2026, sixClubs)
        val base = md(2).serial * 1000L
        assertEquals(listOf(base + 101, base + 102, base + 103), round.matches.map { it.matchId })
    }

    @Test
    fun `numero impar de clubes e rejeitado`() {
        val fiveClubs = (1L..5L).map { makeClub(id = it) }
        assertThrows<IllegalArgumentException> { generateRound(md(1), 2026, fiveClubs) }
    }

    // ─── Múltiplas divisões (issue #47) ──────────────────────────────────

    private val twoDivisions =
        (1L..6L).map { makeClub(id = it, name = "D1 Club $it") } +
            (7L..12L).map { makeClub(id = it, name = "D2 Club $it", division = Division(2)) }

    @Test
    fun `cada divisao gera as proprias partidas na mesma rodada`() {
        val round = generateRound(md(1), 2026, twoDivisions)

        assertEquals(6, round.matches.size)
        val byDivision = round.matches.groupBy { it.division }
        assertEquals(setOf(Division(1), Division(2)), byDivision.keys)
        assertEquals(3, byDivision.getValue(Division(1)).size)
        assertEquals(3, byDivision.getValue(Division(2)).size)
        // Partidas nunca cruzam divisões: os 6 clubes de cada bloco jogam entre si.
        for (m in byDivision.getValue(Division(2))) {
            assertTrue(m.homeClubId.value in 7L..12L && m.awayClubId.value in 7L..12L)
        }
    }

    @Test
    fun `matchId nao colide entre divisoes da mesma rodada`() {
        val round = generateRound(md(3), 2026, twoDivisions, leagueRound = 3)
        assertEquals(round.matches.size, round.matches.map { it.matchId }.toSet().size)
        val base = md(3).serial * 1000L
        assertEquals(listOf(101L, 102L, 103L, 201L, 202L, 203L).map { base + it }, round.matches.map { it.matchId })
    }

    @Test
    fun `divisao menor folga quando o proprio turno termina`() {
        // Divisão 1 com 6 clubes (5 rodadas), divisão 2 com 4 (3 rodadas):
        // na rodada 4 só a divisão 1 tem jogos.
        val unbalanced =
            (1L..6L).map { makeClub(id = it) } +
                (7L..10L).map { makeClub(id = it, division = Division(2)) }

        assertEquals(5, seasonRounds(unbalanced))
        val round4 = generateRound(md(4), 2026, unbalanced, leagueRound = 4)
        assertEquals(3, round4.matches.size)
        assertTrue(round4.matches.all { it.division == Division(1) })
    }

    @Test
    fun `divisao com numero impar de clubes e rejeitada`() {
        val clubs = (1L..6L).map { makeClub(id = it) } +
            (7L..9L).map { makeClub(id = it, division = Division(2)) }
        assertThrows<IllegalArgumentException> { generateRound(md(1), 2026, clubs) }
        assertTrue(!canScheduleSeason(clubs))
    }

    @Test
    fun `encaixe do calendario e rodada da liga se descolam (issues 61 e 125)`() {
        // Encaixe da semana 9, rodada de liga 3: os pareamentos são os da rodada
        // 3; o matchId (seed da simulação) é o do encaixe, único no jogo.
        val round = generateRound(md(9), season = 2026, clubs = sixClubs, leagueRound = 3)
        val berger3 = generateRound(md(3), season = 2026, clubs = sixClubs, leagueRound = 3)

        assertEquals(md(9), round.matchday)
        assertTrue(round.matches.all { it.stage == RoundStage.League(3) })
        assertTrue(round.matches.all { it.competition == SeasonCompetitions.leagueId(Division.FIRST) })
        assertEquals(
            berger3.matches.map { it.homeClubId to it.awayClubId },
            round.matches.map { it.homeClubId to it.awayClubId },
        )
        assertTrue(round.matches.zip(berger3.matches).all { (a, b) -> a.matchId != b.matchId })
    }
}
