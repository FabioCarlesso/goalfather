package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.test.SERIE_A
import com.carlesso.goalfather.test.SERIE_A_NAME
import com.carlesso.goalfather.test.SERIE_B
import com.carlesso.goalfather.test.SERIE_B_NAME
import com.carlesso.goalfather.test.leagueCompetition
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.md
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Os jogos de uma rodada de liga num encaixe (issues #47/#125/#131): nomes,
 * competição do catálogo, divisão = tier da liga e `matchId` pelo slot da liga.
 */
class RoundFixturesGeneratorTest {

    private val serieA = leagueCompetition(SERIE_A, SERIE_A_NAME, Countries.BRAZIL, 1)
    private val serieB = leagueCompetition(SERIE_B, SERIE_B_NAME, Countries.BRAZIL, 2)
    private val premier = leagueCompetition(CompetitionId("eng-premier-league"), "Premier League", Countries.ENGLAND, 1)

    private val sixClubs = (1L..6L).map { makeClub(id = it, name = "C$it") }
    private val byId = sixClubs.associateBy { it.id }
    private val berger = roundRobinSchedule(sixClubs.map { it.id }, legs = 2)

    @Test
    fun `rodada vira partidas com a competicao do catalogo e a rodada da liga`() {
        val matches = leagueMatches(md(1), 3, serieA, slot = 1, pairings = berger[2], clubsById = byId)

        assertEquals(3, matches.size)
        assertTrue(matches.all { it.competition == SERIE_A && it.competitionName == SERIE_A_NAME })
        assertTrue(matches.all { it.stage == RoundStage.League(3) && it.division == Division(1) })
        assertEquals(berger[2].map { it.home to it.away }, matches.map { it.homeClubId to it.awayClubId })
        assertEquals(matches.map { "C${it.homeClubId.value}" }, matches.map { it.homeClubName })
    }

    @Test
    fun `divisao da partida e o tier da liga`() {
        val matches = leagueMatches(md(1), 1, serieB, slot = 2, pairings = berger[0], clubsById = byId)
        assertTrue(matches.all { it.division == Division(2) })
    }

    @Test
    fun `matchId segue o padrao encaixe 1000 + slot 100 + indice`() {
        val matches = leagueMatches(md(2), 1, serieA, slot = 4, pairings = berger[0], clubsById = byId)
        val base = md(2).serial * Matchday.MATCH_IDS_PER_MATCHDAY + 400
        assertEquals(listOf(base + 1, base + 2, base + 3), matches.map { it.matchId })
        assertTrue(matches.all { Matchday.ofMatchId(it.matchId) == md(2) })
    }

    @Test
    fun `ligas de divisao 1 de paises diferentes nao colidem no mesmo encaixe (issue 131)`() {
        val a = leagueMatches(md(3), 1, serieA, slot = 1, pairings = berger[0], clubsById = byId)
        val p = leagueMatches(md(3), 1, premier, slot = 4, pairings = berger[0], clubsById = byId)
        assertTrue((a.map { it.matchId } intersect p.map { it.matchId }.toSet()).isEmpty())
    }

    @Test
    fun `slot fora da faixa de matchId e rejeitado`() {
        assertThrows<IllegalArgumentException> { leagueMatches(md(1), 1, serieA, slot = 0, pairings = berger[0], clubsById = byId) }
        assertThrows<IllegalArgumentException> {
            leagueMatches(md(1), 1, serieA, slot = MAX_LEAGUE_SLOTS + 1, pairings = berger[0], clubsById = byId)
        }
    }

    @Test
    fun `turno e returno cobre cada confronto uma vez em cada mando`() {
        val all = berger.flatten()
        assertEquals(30, all.size) // 6 × 5
        assertEquals(30, all.map { it.home to it.away }.toSet().size)
        assertTrue(all.all { p -> all.count { it.home == p.away && it.away == p.home } == 1 })
    }
}
