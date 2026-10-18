package com.carlesso.goalfather.application.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Helper de retry do lock otimista do clube (issue #89). `currentTime` (o
 * relógio virtual do `runTest`) ainda é API experimental — daí o opt-in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClubWriteRetryTest {

    @Test
    fun `sem conflito executa uma unica vez e nao espera`() = runTest {
        var calls = 0
        val result = retryOnConflict(isConflict = { it == "conflict" }) { calls++; "ok" }

        assertEquals("ok", result)
        assertEquals(1, calls)
        assertEquals(0, currentTime, "sem conflito, sem backoff")
    }

    @Test
    fun `repete enquanto houver conflito e devolve o primeiro desfecho que nao e conflito`() = runTest {
        val seen = mutableListOf<Int>()
        val result = retryOnConflict(isConflict = { it == "conflict" }) { attempt ->
            seen += attempt
            if (attempt < 2) "conflict" else "ok"
        }

        assertEquals("ok", result)
        assertEquals(listOf(0, 1, 2), seen)
    }

    @Test
    fun `esgotadas as tentativas devolve o conflito como valor`() = runTest {
        var calls = 0
        val result = retryOnConflict(maxAttempts = 4, isConflict = { it == "conflict" }) { calls++; "conflict" }

        assertEquals("conflict", result)
        assertEquals(4, calls)
    }

    @Test
    fun `espera o backoff de cada repeticao antes de tentar de novo`() = runTest {
        // Tempo virtual do `runTest`: o `delay` não dorme de verdade, mas o
        // relógio avança exatamente o que o backoff pediu (10 + 20 + 40).
        val asked = mutableListOf<Int>()
        retryOnConflict(
            maxAttempts = 4,
            backoff = { retry -> asked += retry; 10L shl (retry - 1) },
            isConflict = { true },
        ) { "conflict" }

        assertEquals(listOf(1, 2, 3), asked)
        assertEquals(70, currentTime)
    }

    @Test
    fun `backoff padrao e exponencial com jitter dentro da janela`() {
        val random = Random(42)
        for (retry in 1..4) {
            val window = CLUB_WRITE_BACKOFF_BASE_MS shl (retry - 1)
            repeat(50) {
                val wait = clubWriteBackoff(retry, random)
                assertTrue(wait in window until 2 * window, "retry=$retry wait=$wait")
            }
        }
    }

    @Test
    fun `parametros invalidos sao erro de programacao`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            retryOnConflict(maxAttempts = 0, isConflict = { true }) { "x" }
        }
        assertFailsWith<IllegalArgumentException> { clubWriteBackoff(0) }
    }
}
