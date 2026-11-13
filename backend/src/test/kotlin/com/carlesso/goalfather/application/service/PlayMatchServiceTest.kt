package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.MatchStream
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.engine.MatchSetup
import com.carlesso.goalfather.domain.engine.MatchSimulator
import com.carlesso.goalfather.domain.engine.QuickMatchSimulator
import com.carlesso.goalfather.domain.engine.kickOff
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.KnockoutPhase
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.md
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlayMatchServiceTest {

    private val clubRepo: ClubRepository = mockk()
    private val leagueRepo: LeagueRepository = mockk()
    private val service = PlayMatchService(clubRepo, leagueRepo)

    /** `matchId` pela fórmula real (`serial * 1000 + n`): o service acha o encaixe por ele. */
    private fun matchIdIn(matchday: Matchday, n: Int = 101): Long = matchday.serial * Matchday.MATCH_IDS_PER_MATCHDAY + n

    private val matchId = matchIdIn(md(1))

    // `InProgress`: liberado pelo `play`. `Scheduled` não transmite (issue #151).
    private val round = Round(
        matchday = md(1),
        season = 2026,
        status = RoundStatus.InProgress,
        matches = listOf(
            RoundMatch(matchId, ClubId(1), ClubId(2), "Home FC", "Away FC"),
        ),
    )

    private fun stubClubs(homeOverall: Int = 80, awayOverall: Int = 70) {
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub(id = 1, overall = homeOverall)
        coEvery { clubRepo.findById(ClubId(2)) } returns makeClub(id = 2, overall = awayOverall)
    }

    private suspend fun events(id: Long = matchId): List<MatchEvent> =
        assertIs<MatchStream.Live>(service.open(id)).events.toList()

    @Test
    fun `stream da partida vai de KickOff a FullTime`() = runTest {
        coEvery { leagueRepo.findRound(md(1)) } returns round
        stubClubs()

        val events = events()

        assertIs<MatchEvent.KickOff>(events.first())
        assertIs<MatchEvent.FullTime>(events.last())
        val kickOff = events.first() as MatchEvent.KickOff
        assertEquals("Home FC", kickOff.homeClubName)
    }

    @Test
    fun `mesma partida produz a mesma sequencia (determinismo por matchId)`() = runTest {
        coEvery { leagueRepo.findRound(md(1)) } returns round
        stubClubs()

        assertEquals(events(), events())
    }

    @Test
    fun `encaixe Scheduled - NotStarted, sem ler clube nem simular (issue 151)`() = runTest {
        // Sem a recusa, o drill-down era uma prévia do resultado: mesma seed da
        // rodada, então dava para trocar a escalação até ver uma vitória.
        coEvery { leagueRepo.findRound(md(1)) } returns round.copy(status = RoundStatus.Scheduled)

        assertEquals(MatchStream.NotStarted, service.open(matchId))
        coVerify(exactly = 0) { clubRepo.findById(any()) }
    }

    @Test
    fun `encaixe Finished - acha a partida no encaixe dela, nao no ultimo (issue 151)`() = runTest {
        // Depois de encerrada, `findLatest` já aponta o encaixe seguinte: o
        // drill-down do jogo recém-jogado precisa buscar pelo próprio encaixe.
        coEvery { leagueRepo.findRound(md(1)) } returns round.copy(status = RoundStatus.Finished)
        stubClubs()

        assertIs<MatchEvent.FullTime>(events().last())
        coVerify(exactly = 0) { leagueRepo.findLatest() }
    }

    @Test
    fun `partida fora do encaixe ou encaixe inexistente lanca IllegalArgumentException`() = runTest {
        coEvery { leagueRepo.findRound(md(1)) } returns round
        coEvery { leagueRepo.findRound(md(2)) } returns null

        assertThrows<IllegalArgumentException> { service.open(matchIdIn(md(1), n = 999)) }
        assertThrows<IllegalArgumentException> { service.open(matchIdIn(md(2))) }
        assertThrows<IllegalArgumentException> { service.open(0) }
    }

    @Test
    fun `drill-down de jogo de copa simula em modo mata-mata (issue 61)`() = runTest {
        // Mesmo matchId, mesma seed — o drill-down só bate com a rodada se
        // também souber que é mata-mata, senão um empate perderia os pênaltis.
        val cupId = matchIdIn(md(2), n = 1)
        val cupDate = round.copy(
            matchday = md(2),
            matches = round.matches.map { it.copy(matchId = cupId, stage = RoundStage.Cup(KnockoutPhase.FINAL)) },
        )
        // Com dono: jogo de técnico humano passa pela engine completa (issue #129).
        val home = makeClub(id = 1, overall = 75, ownerId = 9)
        val away = makeClub(id = 2, overall = 75)
        coEvery { leagueRepo.findRound(md(2)) } returns cupDate
        coEvery { clubRepo.findById(ClubId(1)) } returns home
        coEvery { clubRepo.findById(ClubId(2)) } returns away

        val expected = MatchSimulator().simulate(
            MatchSetup(
                home.startingLineup(),
                away.startingLineup(),
                "Home FC",
                "Away FC",
                knockout = true,
                homeClubId = home.id,
                awayClubId = away.id,
            ),
            Random(cupId),
        ).toList()
        assertEquals(expected, events(cupId))
    }

    @Test
    fun `drill-down de jogo so da IA reproduz a simulacao rapida da rodada (issue 129)`() = runTest {
        // A rodada decidiu este jogo pela rápida; re-rodar a completa aqui
        // mostraria outro placar. O resumo sai da MESMA rápida, mesma seed.
        coEvery { leagueRepo.findRound(md(1)) } returns round
        stubClubs()

        val setup = MatchSetup(
            makeClub(id = 1, overall = 80).startingLineup(),
            makeClub(id = 2, overall = 70).startingLineup(),
            "Home FC",
            "Away FC",
            homeClubId = ClubId(1),
            awayClubId = ClubId(2),
        )
        val quick = QuickMatchSimulator().simulate(setup, Random(matchId))
        assertEquals(listOf(setup.kickOff()) + quick.summary(), events())
    }
}
