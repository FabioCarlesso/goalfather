package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.metrics.GoalfatherMetrics
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.test.makeClub
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifica que o timer de simulação da rodada (issue #44) é registrado e
 * incrementado no [SimpleMeterRegistry] após exercitar o fluxo — o teste
 * "leve" pedido nos critérios de aceite, sem subir contexto Spring.
 */
class SimulationMetricsTest {

    private val clubRepo: ClubRepository = mockk()
    private val leagueRepo: LeagueRepository = mockk()
    private val readinessRepo: RoundReadinessRepository = mockk(relaxed = true)
    private val registry = SimpleMeterRegistry()
    private val service = PlayRoundService(clubRepo, leagueRepo, readinessRepo, meterRegistry = registry)

    private val homeClub = makeClub(id = 1, name = "Home FC", squadSize = 11, overall = 80)
    private val awayClub = makeClub(id = 2, name = "Away FC", squadSize = 11, overall = 70)

    private val round = Round(
        number = 1,
        season = 2026,
        matches = listOf(
            RoundMatch(
                matchId = 1001,
                homeClubId = ClubId(1),
                awayClubId = ClubId(2),
                homeClubName = "Home FC",
                awayClubName = "Away FC",
            ),
        ),
    )

    private val standings = Standings(
        season = 2026,
        round = 0,
        rows = listOf(
            StandingRow(position = 1, clubId = ClubId(1), clubName = "Home FC"),
            StandingRow(position = 2, clubId = ClubId(2), clubName = "Away FC"),
        ),
    )

    @Test
    fun `simular uma rodada registra o timer goalfather_round_simulation`() = runTest {
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns standings
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns awayClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.saveStandings(any()) } just Runs

        service.stream(1).toList()

        val timer = registry.find(GoalfatherMetrics.ROUND_SIMULATION).timer()
        assertNotNull(timer, "Timer da simulação de rodada deve existir no registry")
        assertEquals(1, timer.count(), "Uma rodada simulada = uma amostra no timer")
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS) >= 0)
    }
}
