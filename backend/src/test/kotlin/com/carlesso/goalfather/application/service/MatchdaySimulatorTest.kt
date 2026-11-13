package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.metrics.GoalfatherMetrics
import com.carlesso.goalfather.domain.engine.MatchSetup
import com.carlesso.goalfather.domain.engine.MatchSimulator
import com.carlesso.goalfather.domain.engine.QuickMatchSimulator
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.test.makeClub
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Separação rápida/completa do encaixe (issue #129), sem Spring: modo por
 * posse do clube, ordem e determinismo sob paralelismo, propagação de falha e
 * o tempo de 60 jogos rápidos medido pela métrica.
 */
class MatchdaySimulatorTest {

    private val human = makeClub(id = 1, name = "Humano FC", overall = 80, ownerId = 7)
    private val ai = makeClub(id = 2, name = "IA FC", overall = 70)

    private fun fixture(matchId: Long, mode: SimulationMode) = MatchFixture(
        match = RoundMatch(
            matchId = matchId,
            homeClubId = ClubId(1),
            awayClubId = ClubId(2),
            homeClubName = "A",
            awayClubName = "B",
        ),
        setup = MatchSetup(
            home = human.startingLineup(),
            away = ai.startingLineup(),
            homeName = "A",
            awayName = "B",
        ),
        mode = mode,
    )

    @Test
    fun `modo segue a posse - humano em qualquer lado vai para a completa`() {
        assertEquals(SimulationMode.FULL, simulationModeOf(human, ai))
        assertEquals(SimulationMode.FULL, simulationModeOf(ai, human))
        assertEquals(SimulationMode.QUICK, simulationModeOf(ai, ai))
        // Clube ausente conta como IA.
        assertEquals(SimulationMode.QUICK, simulationModeOf(null, ai))
    }

    @Test
    fun `completa transmite o feed inteiro, rapida so o desfecho`() = runTest {
        val sim = MatchdaySimulator(meterRegistry = SimpleMeterRegistry())
        val (full, quick) = sim.simulate(listOf(fixture(10, SimulationMode.FULL), fixture(11, SimulationMode.QUICK)))

        assertIs<SimulatedMatch.Full>(full)
        assertIs<MatchEvent.KickOff>(full.broadcast.first())
        assertEquals(full.events, MatchSimulator().simulate(fixture(10, SimulationMode.FULL).setup, Random(10)).toList())

        assertIs<SimulatedMatch.Quick>(quick)
        assertEquals(listOf(MatchEvent.FullTime::class), quick.broadcast.map { it::class })
        assertEquals(QuickMatchSimulator().simulate(fixture(11, SimulationMode.QUICK).setup, Random(11)), quick.result)
        // A súmula (efeitos) leva os destaques; o placar bate com o FullTime.
        assertEquals(quick.result.summary(), quick.sheet)
        assertEquals(quick.result.homeGoals to quick.result.awayGoals, quick.score)
    }

    @Test
    fun `paralelo devolve na ordem do encaixe e igual ao sequencial`() = runTest {
        // Modos intercalados: a reordenação final é que devolve a ordem original.
        val fixtures = (1L..40L).map { fixture(it, if (it % 5 == 0L) SimulationMode.FULL else SimulationMode.QUICK) }

        val parallel = MatchdaySimulator(meterRegistry = SimpleMeterRegistry()).simulate(fixtures)
        // Dispatcher de teste de UMA thread: o mesmo cálculo, sem paralelismo.
        val sequential = MatchdaySimulator(
            meterRegistry = SimpleMeterRegistry(),
            dispatcher = StandardTestDispatcher(testScheduler),
        ).simulate(fixtures)

        assertEquals(fixtures.map { it.match.matchId }, parallel.map { it.match.matchId })
        assertEquals(sequential, parallel)
    }

    @Test
    fun `falha num jogo rapido cancela o lote e chega a quem chamou`() = runTest {
        // Concorrência estruturada: o `coroutineScope` não devolve com um jogo
        // pendurado — a exceção de um filho cancela os irmãos e sobe.
        val real = QuickMatchSimulator()
        val calls = AtomicInteger()
        val exploding = mockk<QuickMatchSimulator> {
            every { simulate(any(), any()) } answers {
                if (calls.incrementAndGet() == 5) throw IllegalStateException("boom")
                real.simulate(firstArg(), secondArg())
            }
        }
        val fixtures = (1L..20L).map { fixture(it, SimulationMode.QUICK) }
        val sim = MatchdaySimulator(quickEngine = exploding, meterRegistry = SimpleMeterRegistry())
        assertFailsWith<IllegalStateException> { sim.simulate(fixtures) }
    }

    @Test
    fun `60 jogos rapidos terminam em menos de 1 s, medido pela metrica`() = runTest {
        val fixtures = (1L..60L).map { fixture(it, SimulationMode.QUICK) }
        // Aquece o JIT num simulador à parte: a 1ª chamada mede classloading e
        // interpretação, não a engine — e não deve contaminar a amostra.
        MatchdaySimulator(meterRegistry = SimpleMeterRegistry()).simulate(fixtures)

        val registry = SimpleMeterRegistry()
        MatchdaySimulator(meterRegistry = registry, dispatcher = Dispatchers.Default).simulate(fixtures)

        val timer = assertNotNull(registry.find(GoalfatherMetrics.QUICK_SIMULATION).timer())
        assertEquals(1, timer.count(), "uma amostra por encaixe")
        val lastMs = timer.totalTime(TimeUnit.MILLISECONDS)
        println("[#129] 60 jogos rápidos: ${"%.2f".format(lastMs)} ms")
        assertTrue(lastMs < 1_000.0, "60 jogos rápidos levaram $lastMs ms")
    }
}
