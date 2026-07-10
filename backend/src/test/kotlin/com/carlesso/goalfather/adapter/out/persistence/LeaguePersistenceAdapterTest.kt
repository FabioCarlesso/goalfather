package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.repository.RoundJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.StandingsJpaRepository
import com.carlesso.goalfather.application.metrics.GoalfatherMetrics
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.springframework.dao.OptimisticLockingFailureException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Cobre — sem Spring — a tradução que é a razão de existir do PR (issue #46): a
 * `OptimisticLockingFailureException` lançada no commit da transição vira `false`
 * no adapter, e o conflito é contado. O teste de integração dispara a exceção de
 * verdade só quando duas transações colidem (inerentemente não-determinístico);
 * aqui forçamos a exceção com mock para exercitar o `catch` de forma estável.
 */
class LeaguePersistenceAdapterTest {

    private val roundRepo: RoundJpaRepository = mockk()
    private val standingsRepo: StandingsJpaRepository = mockk()
    private val transition: RoundTransition = mockk()
    private val registry = SimpleMeterRegistry()
    private val adapter = LeaguePersistenceAdapter(roundRepo, standingsRepo, Json, transition, registry)

    @Test
    fun `finishRound traduz OptimisticLockException em false e conta o conflito`() = runBlocking {
        val round = Round(number = 1, season = 2026, matches = emptyList(), status = RoundStatus.Finished)
        every { transition.finish(round) } throws OptimisticLockingFailureException("perdeu a corrida")

        assertFalse(adapter.finishRound(round), "perdedor do lock otimista recebe false")
        assertEquals(
            1.0,
            registry.counter(GoalfatherMetrics.ROUND_CLAIM_CONFLICTS, "phase", "finish").count(),
        )
    }

    @Test
    fun `startRound traduz OptimisticLockException em false e conta o conflito`() = runBlocking {
        every { transition.start(1) } throws OptimisticLockingFailureException("perdeu a corrida")

        assertFalse(adapter.startRound(1), "perdedor do lock otimista recebe false")
        assertEquals(
            1.0,
            registry.counter(GoalfatherMetrics.ROUND_CLAIM_CONFLICTS, "phase", "start").count(),
        )
    }

    @Test
    fun `sem conflito o resultado da transicao passa direto e nada e contado`() = runBlocking {
        val round = Round(number = 1, season = 2026, matches = emptyList(), status = RoundStatus.Finished)
        every { transition.finish(round) } returns true

        assertEquals(true, adapter.finishRound(round))
        assertEquals(
            0.0,
            registry.counter(GoalfatherMetrics.ROUND_CLAIM_CONFLICTS, "phase", "finish").count(),
        )
    }
}
