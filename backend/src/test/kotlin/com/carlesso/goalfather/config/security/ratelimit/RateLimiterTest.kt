package com.carlesso.goalfather.config.security.ratelimit

import com.github.benmanes.caffeine.cache.Ticker
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testa o [RateLimiter] sem subir Spring nem `Thread.sleep`: um [Ticker] falso
 * avança o tempo de forma determinística (mesmo espírito do `Random` com seed
 * da engine). Cobre a semântica de janela fixa: bloqueia após N, reseta ao
 * expirar a janela e `reset()` limpa na hora.
 */
class RateLimiterTest {

    /** Ticker controlável: `read()` devolve os nanos atuais; testes avançam à mão. */
    private class FakeTicker : Ticker {
        private val nanos = AtomicLong(0)
        override fun read(): Long = nanos.get()
        fun advance(duration: Duration) = nanos.addAndGet(duration.toNanos())
    }

    private val window = Duration.ofMinutes(1)

    @Test
    fun `bloqueia somente apos atingir o limite`() {
        val limiter = RateLimiter(maxAttempts = 3, window = window, ticker = FakeTicker())
        val key = "ip|user"

        repeat(3) {
            assertFalse(limiter.isBlocked(key), "não deve bloquear antes de esgotar o limite")
            limiter.record(key)
        }
        assertTrue(limiter.isBlocked(key), "deve bloquear após a 3ª tentativa")
    }

    @Test
    fun `contador reseta quando a janela expira`() {
        val ticker = FakeTicker()
        val limiter = RateLimiter(maxAttempts = 3, window = window, ticker = ticker)
        val key = "ip|user"

        repeat(3) { limiter.record(key) }
        assertTrue(limiter.isBlocked(key))

        ticker.advance(window.plusSeconds(1))
        assertFalse(limiter.isBlocked(key), "após a janela expirar o contador zera")
    }

    @Test
    fun `reset limpa o contador imediatamente`() {
        val limiter = RateLimiter(maxAttempts = 3, window = window, ticker = FakeTicker())
        val key = "ip|user"

        repeat(3) { limiter.record(key) }
        assertTrue(limiter.isBlocked(key))

        limiter.reset(key)
        assertFalse(limiter.isBlocked(key), "reset (ex.: login OK) libera na hora")
    }

    @Test
    fun `chaves diferentes tem janelas independentes`() {
        val limiter = RateLimiter(maxAttempts = 2, window = window, ticker = FakeTicker())

        repeat(2) { limiter.record("ip|alice") }
        assertTrue(limiter.isBlocked("ip|alice"))
        assertFalse(limiter.isBlocked("ip|bob"), "o limite de alice não afeta bob")
    }
}
