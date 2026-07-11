package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cobre a persistência de estatísticas de jogador (issue #2). Usa ids de
 * jogador DISTINTOS entre mandante e visitante para que a soma de gols
 * persistidos possa ser comparada com o total de eventos de gol da rodada.
 */
class PlayerStatsPersistenceTest {

    private val clubRepo: ClubRepository = mockk()
    private val leagueRepo: LeagueRepository = mockk()
    private val readinessRepo: RoundReadinessRepository = mockk(relaxed = true)
    private val service = PlayRoundService(clubRepo, leagueRepo, readinessRepo)

    private val home = makeClub(id = 1, name = "Home FC")
        .copy(squad = (1L..11L).map { makePlayer(it, overall = 85) })
    private val away = makeClub(id = 2, name = "Away FC")
        .copy(squad = (101L..111L).map { makePlayer(it, overall = 70) })

    private val round = Round(
        number = 1,
        season = 2026,
        matches = listOf(
            RoundMatch(1001, ClubId(1), ClubId(2), "Home FC", "Away FC"),
        ),
    )

    private val standings = Standings(
        season = 2026,
        round = 0,
        rows = listOf(
            StandingRow(1, ClubId(1), "Home FC"),
            StandingRow(2, ClubId(2), "Away FC"),
        ),
    )

    @Test
    fun `gols e cartoes dos eventos sao acumulados no elenco e persistidos`() = runTest {
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(ClubId(1)) } returns home
        coEvery { clubRepo.findById(ClubId(2)) } returns away
        coEvery { clubRepo.findAll() } returns listOf(home, away)
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.finishRound(any()) } returns true
        coEvery { leagueRepo.saveStandings(any()) } just Runs
        val savedClubs = mutableListOf<Club>()
        coEvery { clubRepo.save(capture(savedClubs)) } answers { firstArg() }

        val events = service.stream(1).toList()
        val matchEvents = events.filterIsInstance<RoundEvent.MatchUpdate>().map { it.event }
        val goalEvents = matchEvents.count { it is MatchEvent.Goal }
        val yellowEvents = matchEvents.count { it is MatchEvent.Card && !it.red }
        val redEvents = matchEvents.count { it is MatchEvent.Card && it.red }

        // Invariante: todo gol/cartão de evento foi atribuído ao clube que
        // contém o jogador, então a soma persistida bate com os eventos.
        assertEquals(goalEvents, savedClubs.sumOf { c -> c.squad.sumOf { it.goals } })
        assertEquals(yellowEvents, savedClubs.sumOf { c -> c.squad.sumOf { it.yellowCards } })
        assertEquals(redEvents, savedClubs.sumOf { c -> c.squad.sumOf { it.redCards } })
    }
}
