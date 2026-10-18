package com.carlesso.goalfather.application.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Helper de retry do lock otimista do clube (issue #89). */
class ClubWriteRetryTest {

    @Test
    fun `sem conflito executa uma unica vez`() {
        var calls = 0
        val result = retryOnConflict(isConflict = { it == "conflict" }) { calls++; "ok" }

        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `repete enquanto houver conflito e devolve o primeiro desfecho que nao e conflito`() {
        val seen = mutableListOf<Int>()
        val result = retryOnConflict(isConflict = { it == "conflict" }) { attempt ->
            seen += attempt
            if (attempt < 2) "conflict" else "ok"
        }

        assertEquals("ok", result)
        assertEquals(listOf(0, 1, 2), seen)
    }

    @Test
    fun `esgotadas as tentativas devolve o conflito como valor`() {
        var calls = 0
        val result = retryOnConflict(maxAttempts = 4, isConflict = { it == "conflict" }) { calls++; "conflict" }

        assertEquals("conflict", result)
        assertEquals(4, calls)
    }

    @Test
    fun `maxAttempts menor que um e erro de programacao`() {
        assertFailsWith<IllegalArgumentException> {
            retryOnConflict(maxAttempts = 0, isConflict = { true }) { "x" }
        }
    }
}
