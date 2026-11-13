package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.DisciplineRules
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.SeasonCompetitions
import com.carlesso.goalfather.domain.model.Slot
import com.carlesso.goalfather.domain.model.Suspension
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Suspensão por cartões e limite de estrangeiros (issue #130) — funções puras,
 * sem Spring.
 */
class SuspensionRulesTest {

    private val league = CompetitionId("liga-d1")
    private val cup = CompetitionId("copa")
    private val rules = DisciplineRules.DEFAULT
    private val yellow = MatchCards(yellow = 1)

    /** Joga [times] partidas de [competition] com um amarelo em cada. */
    private fun Player.yellowsIn(competition: CompetitionId, times: Int): Player =
        (1..times).fold(this) { p, _ -> p.afterMatchIn(competition, rules, yellow) }

    @Test
    fun `o 3o amarelo numa competicao suspende o proximo jogo DAQUELA competicao e nao de outra`() {
        val player = makePlayer(1).yellowsIn(league, 3)

        assertEquals(Suspension(league, 1), player.suspensionIn(league))
        assertFalse(player.isSuspendedIn(cup))
        // O acumulado "pago" com a suspensão zera.
        assertNull(player.pendingYellows[league])
    }

    @Test
    fun `amarelos de competicoes diferentes nao se somam`() {
        val player = makePlayer(1).yellowsIn(league, 2).yellowsIn(cup, 2)

        assertTrue(player.suspensions.isEmpty())
        assertEquals(mapOf(league to 2, cup to 2), player.pendingYellows)
    }

    @Test
    fun `suspenso cumpre o gancho no jogo seguinte da competicao`() {
        val suspended = makePlayer(1).yellowsIn(league, 3)

        val served = suspended.afterMatchIn(league, rules, MatchCards())

        assertTrue(served.suspensions.isEmpty())
    }

    @Test
    fun `jogo de outra competicao nao conta para o gancho`() {
        // O gancho conta jogos DO CLUBE naquela competição: a copa no meio não o cumpre.
        val suspended = makePlayer(1).yellowsIn(league, 3)

        val afterCup = suspended.afterMatchIn(cup, rules, MatchCards())

        assertEquals(Suspension(league, 1), afterCup.suspensionIn(league))
    }

    @Test
    fun `vermelho direto e segundo amarelo suspendem pelo gancho da regra sem somar amarelos`() {
        val strict = DisciplineRules(matchesPerRedCard = 2)
        val red = makePlayer(1).afterMatchIn(league, strict, MatchCards(red = 1))
        val twoYellows = makePlayer(2).afterMatchIn(league, strict, MatchCards(yellow = 2))

        assertEquals(Suspension(league, 2), red.suspensionIn(league))
        assertEquals(Suspension(league, 2), twoYellows.suspensionIn(league))
        // O amarelo que antecede a expulsão é absorvido por ela.
        assertTrue(twoYellows.pendingYellows.isEmpty())
    }

    @Test
    fun `gancho de varios jogos desconta um por jogo`() {
        val suspended = makePlayer(1).copy(suspensions = listOf(Suspension(league, 2)))

        val once = suspended.afterMatchIn(league, rules, MatchCards())

        assertEquals(Suspension(league, 1), once.suspensionIn(league))
    }

    @Test
    fun `regra configuravel - 2 amarelos suspendem e o mata-mata zera os pendentes`() {
        val continental = DisciplineRules(yellowsForSuspension = 2, resetsYellowsAtKnockout = true)
        val twice = (1..2).fold(makePlayer(1)) { p, _ -> p.afterMatchIn(cup, continental, yellow) }
        assertTrue(twice.isSuspendedIn(cup))

        val pending = makePlayer(2).afterMatchIn(cup, continental, yellow)
        val knockout = listOf(pending).enteringKnockoutOf(cup, continental).single()
        assertTrue(knockout.pendingYellows.isEmpty())
        // Sem a flag, os pendentes atravessam a fase.
        assertEquals(pending, listOf(pending).enteringKnockoutOf(cup, rules).single())
    }

    @Test
    fun `elenco aplica os cartoes de cada jogador`() {
        val squad = listOf(makePlayer(1), makePlayer(2))

        val after = squad.afterMatchIn(league, rules, mapOf(PlayerId(2) to MatchCards(red = 1)))

        assertFalse(after[0].isSuspendedIn(league))
        assertTrue(after[1].isSuspendedIn(league))
    }

    // ─── Apito inicial ────────────────────────────────────────────────────

    private fun clubWithSavedLineup(squadSize: Int = 14): Club {
        val base = makeClub(squadSize = squadSize)
        return base.copy(lineup = Lineup(players = base.squad.take(11), formation = Formation.F_4_4_2))
    }

    @Test
    fun `suspenso nao entra em campo mesmo estando na escalacao salva`() {
        val base = clubWithSavedLineup()
        val club = base.copy(
            squad = base.squad.map { if (it.id == PlayerId(1)) it.copy(suspensions = listOf(Suspension(league, 1))) else it },
        )

        val inLeague = club.startingLineupIn(league).players.map { it.id }
        val inCup = club.startingLineupIn(cup).players.map { it.id }

        assertFalse(PlayerId(1) in inLeague, "suspenso na liga fica fora do jogo de liga")
        assertEquals(11, inLeague.size, "reserva apto completa o time")
        assertTrue(PlayerId(1) in inCup, "suspenso na liga joga a copa")
    }

    @Test
    fun `escalacao automatica respeita o limite de estrangeiros`() {
        // Elenco de 14 com 12 estrangeiros: o limite de 9 deixa o time com 9
        // estrangeiros + os 2 brasileiros, e não completa com o 10º gringo.
        val base = makeClub(squadSize = 14)
        val club = base.copy(
            squad = base.squad.mapIndexed { i, p -> if (i < 12) p.copy(nationality = Countries.ARGENTINA) else p },
        )

        val onField = club.startingLineupIn(SeasonCompetitions.leagueId(club.division)).players

        assertEquals(SeasonCompetitions.NATIONAL_FOREIGN_PLAYER_LIMIT, club.foreignersAmong(onField).size)
        assertEquals(11, onField.size)
    }

    @Test
    fun `segunda nacionalidade do pais do clube nao ocupa vaga de estrangeiro`() {
        val club = makeClub(squadSize = 11)
        val dual = club.squad.first().copy(nationality = Countries.ARGENTINA, secondNationality = Countries.BRAZIL)

        assertTrue(club.foreignersAmong(listOf(dual)).isEmpty())
    }

    @Test
    fun `cada clube do encaixe joga a competicao da sua partida`() {
        val round = Round(
            matchday = Matchday.of(LocalDate.of(2026, 3, 1), Slot.WEEKEND),
            season = 2026,
            matches = listOf(
                RoundMatch(1, ClubId(1), ClubId(2), "A", "B"),
                RoundMatch(2, ClubId(3), ClubId(4), "C", "D", competition = cup, competitionName = "Copa"),
            ),
        )

        assertEquals(
            mapOf(ClubId(1) to league, ClubId(2) to league, ClubId(3) to cup, ClubId(4) to cup),
            round.competitionByClub(),
        )
    }
}
