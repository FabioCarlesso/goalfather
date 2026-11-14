package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.engine.MatchSetup
import com.carlesso.goalfather.domain.engine.QuickMatchSimulator
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.test.InMemoryCupRepository
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import com.carlesso.goalfather.test.md
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Encaixe com um jogo de técnico humano e um só da IA (issue #129), pelo fluxo
 * inteiro do `PlayRoundService`: o que vai para o stream, o placar gravado e
 * os gols na ficha dos jogadores.
 */
class QuickRoundFlowTest {

    private val clubRepo: ClubRepository = mockk()
    private val leagueRepo: LeagueRepository = mockk()
    private val service = PlayRoundService(
        clubRepo,
        leagueRepo,
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        InMemoryCupRepository(),
        mockk(relaxed = true),
        mockk(relaxed = true),
    )

    // Ids de jogador distintos por clube: a ficha de cada um é inequívoca.
    private fun club(id: Long, ownerId: Long? = null, overall: Int = 75): Club =
        makeClub(id = id, name = "C$id", ownerId = ownerId)
            .copy(squad = (1L..11L).map { makePlayer(id * 100 + it, overall = overall) })

    private val humanHome = club(1, ownerId = 42, overall = 82)
    private val aiAway = club(2)
    private val aiHome = club(3, overall = 85)
    private val aiAway2 = club(4, overall = 65)
    private val clubs = listOf(humanHome, aiAway, aiHome, aiAway2)

    private val humanMatch = RoundMatch(2001, ClubId(1), ClubId(2), "C1", "C2")
    private val aiMatch = RoundMatch(2002, ClubId(3), ClubId(4), "C3", "C4")
    private val round = Round(md(1), 2026, RoundStatus.InProgress, listOf(humanMatch, aiMatch))

    private val standings = Standings(
        season = 2026,
        round = 0,
        rows = clubs.mapIndexed { i, c -> StandingRow(i + 1, c.id, c.name) },
    )

    @Test
    fun `jogo com humano e transmitido inteiro, jogo da IA so com o desfecho`() = runTest {
        coEvery { leagueRepo.findRound(md(1)) } returns round
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        clubs.forEach { c -> coEvery { clubRepo.findById(c.id) } returns c }
        coEvery { clubRepo.findAll() } returns clubs
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.saveStandings(any()) } just Runs
        val finished = slot<Round>()
        coEvery { leagueRepo.finishRound(capture(finished)) } returns true
        val saved = mutableListOf<Club>()
        coEvery { clubRepo.save(capture(saved)) } answers { ClubSaveResult.Saved(firstArg()) }

        val updates = service.stream(md(1)).toList().filterIsInstance<RoundEvent.MatchUpdate>()

        val humanFeed = updates.filter { it.matchId == humanMatch.matchId }.map { it.event }
        assertIs<MatchEvent.KickOff>(humanFeed.first())
        assertIs<MatchEvent.FullTime>(humanFeed.last())

        val aiFeed = updates.filter { it.matchId == aiMatch.matchId }.map { it.event }
        val aiFullTime = assertIs<MatchEvent.FullTime>(aiFeed.single(), "jogo da IA só transmite o apito final")

        // O placar gravado é o da simulação rápida com seed = matchId.
        val expected = QuickMatchSimulator().simulate(
            MatchSetup(aiHome.startingLineup(), aiAway2.startingLineup(), "C3", "C4"),
            Random(aiMatch.matchId),
        )
        assertEquals(expected.homeGoals to expected.awayGoals, aiFullTime.homeGoals to aiFullTime.awayGoals)
        val recorded = finished.captured.matches.single { it.matchId == aiMatch.matchId }
        assertEquals(RoundStatus.Finished, recorded.status)
        assertEquals(expected.homeGoals to expected.awayGoals, recorded.homeGoals to recorded.awayGoals)

        // E os gols do jogo rápido entram na ficha, mesmo sem ir para o stream.
        val goalsOnFile = saved.filter { it.id == aiHome.id || it.id == aiAway2.id }
            .sumOf { c -> c.squad.sumOf { it.goals } }
        assertEquals(expected.homeGoals + expected.awayGoals, goalsOnFile)
        assertTrue(updates.zipWithNext().all { (a, b) -> a.event.minute <= b.event.minute })
    }
}
