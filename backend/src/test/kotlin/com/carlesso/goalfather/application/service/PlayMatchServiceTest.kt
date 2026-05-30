package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.test.makeClub
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlayMatchServiceTest {

    private val clubRepo: ClubRepository = mockk()
    private val leagueRepo: LeagueRepository = mockk()
    private val service = PlayMatchService(clubRepo, leagueRepo)

    private val round = Round(
        number = 1,
        season = 2026,
        matches = listOf(
            RoundMatch(1001, ClubId(1), ClubId(2), "Home FC", "Away FC"),
        ),
    )

    @Test
    fun `stream da partida vai de KickOff a FullTime`() = runTest {
        coEvery { leagueRepo.findLatest() } returns round
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub(id = 1, overall = 80)
        coEvery { clubRepo.findById(ClubId(2)) } returns makeClub(id = 2, overall = 70)

        val events = service.stream(1001).toList()

        assertIs<MatchEvent.KickOff>(events.first())
        assertIs<MatchEvent.FullTime>(events.last())
        val kickOff = events.first() as MatchEvent.KickOff
        assertEquals("Home FC", kickOff.homeClubName)
    }

    @Test
    fun `mesma partida produz a mesma sequencia (determinismo por matchId)`() = runTest {
        coEvery { leagueRepo.findLatest() } returns round
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub(id = 1, overall = 80)
        coEvery { clubRepo.findById(ClubId(2)) } returns makeClub(id = 2, overall = 70)

        assertEquals(service.stream(1001).toList(), service.stream(1001).toList())
    }

    @Test
    fun `partida fora da rodada corrente lanca IllegalArgumentException`() = runTest {
        coEvery { leagueRepo.findLatest() } returns round

        assertThrows<IllegalArgumentException> { service.stream(9999).toList() }
    }
}
