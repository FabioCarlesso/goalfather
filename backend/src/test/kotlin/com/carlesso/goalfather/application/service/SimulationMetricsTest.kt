package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.metrics.GoalfatherMetrics
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.domain.engine.MatchSimulator
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.test.InMemoryCupRepository
import com.carlesso.goalfather.test.makeClub
import io.micrometer.core.instrument.MockClock
import io.micrometer.core.instrument.simple.SimpleConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifica que o timer de simulação da rodada (issue #44) é registrado e
 * incrementado no [SimpleMeterRegistry] após exercitar o fluxo — o teste
 * "leve" pedido nos critérios de aceite, sem subir contexto Spring — e que
 * ele mede só a engine, sem o I/O de repositório (issue #51).
 */
class SimulationMetricsTest {

    private val clubRepo: ClubRepository = mockk()
    private val leagueRepo: LeagueRepository = mockk()
    private val readinessRepo: RoundReadinessRepository = mockk(relaxed = true)
    private val registry = SimpleMeterRegistry()
    private val service = PlayRoundService(
        clubRepo,
        leagueRepo,
        readinessRepo,
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        InMemoryCupRepository(),
        meterRegistry = registry,
    )

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
            // Segunda partida (mando invertido) para o timer ter o que agregar.
            RoundMatch(
                matchId = 1002,
                homeClubId = ClubId(2),
                awayClubId = ClubId(1),
                homeClubName = "Away FC",
                awayClubName = "Home FC",
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

    private fun stubRepos() {
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns awayClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.finishRound(any()) } returns true
        coEvery { leagueRepo.saveStandings(any()) } just Runs
    }

    @Test
    fun `simular uma rodada registra o timer goalfather_round_simulation`() = runTest {
        stubRepos()

        service.stream(1).toList()

        val timer = registry.find(GoalfatherMetrics.ROUND_SIMULATION).timer()
        assertNotNull(timer, "Timer da simulação de rodada deve existir no registry")
        assertEquals(1, timer.count(), "Uma rodada simulada = uma amostra no timer")
        assertTrue(timer.totalTime(TimeUnit.NANOSECONDS) >= 0)
    }

    @Test
    fun `timer da rodada mede so a engine, sem o IO de findById`() = runTest {
        // `MockClock` é o relógio que o `Timer.start(registry)` lê: só anda quando
        // mandamos. Assim o teste é determinístico — nada de comparar wall time
        // real com tolerância — e "custo" vira um valor exato que podemos somar.
        val clock = MockClock()
        val clockedRegistry = SimpleMeterRegistry(SimpleConfig.DEFAULT, clock)

        // Engine real (eventos e placar de verdade), mas cada partida "custa"
        // ENGINE_COST_MS no relógio. `onStart` dispara quando o `toList()` coleta o
        // Flow — o momento em que a simulação de fato acontece.
        val realSimulator = MatchSimulator()
        val simulator: MatchSimulator = mockk {
            every { simulate(any(), any()) } answers {
                realSimulator.simulate(firstArg(), secondArg())
                    .onStart { clock.add(ENGINE_COST_MS, TimeUnit.MILLISECONDS) }
            }
        }
        val clockedService = PlayRoundService(
            clubRepo,
            leagueRepo,
            readinessRepo,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            InMemoryCupRepository(),
            simulator = simulator,
            meterRegistry = clockedRegistry,
        )

        stubRepos()
        // Repositório "lento": cada `findById` avança o relógio 1s, ordens de
        // grandeza acima da engine. Se o timer englobasse o I/O, apareceria aqui.
        coEvery { clubRepo.findById(ClubId(1)) } answers { clock.add(DB_COST_MS, TimeUnit.MILLISECONDS); homeClub }
        coEvery { clubRepo.findById(ClubId(2)) } answers { clock.add(DB_COST_MS, TimeUnit.MILLISECONDS); awayClub }

        clockedService.stream(1).toList()

        val timer = assertNotNull(clockedRegistry.find(GoalfatherMetrics.ROUND_SIMULATION).timer())
        assertEquals(1, timer.count())
        assertEquals(
            ENGINE_COST_MS.toDouble() * round.matches.size,
            timer.totalTime(TimeUnit.MILLISECONDS),
            "Timer deve somar só o custo da engine por partida, sem os ${DB_COST_MS}ms de cada findById",
        )
    }

    private companion object {
        const val ENGINE_COST_MS = 10L
        const val DB_COST_MS = 1_000L
    }
}
