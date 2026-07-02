package com.carlesso.goalfather.config.security.ratelimit

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Contador de tentativas com janela fixa, sobre Caffeine (issue #43).
 *
 * Não trouxe dependência nova (Bucket4j): o Caffeine já está no projeto e um
 * contador por chave com expiração resolve o problema de forma enxuta.
 *
 * Semântica de **janela fixa**: a entrada é criada na primeira tentativa via
 * `get(key) { AtomicInteger(0) }` (única escrita → fixa o `expireAfterWrite`);
 * os incrementos seguintes mutam o `AtomicInteger` em memória, então NÃO
 * renovam o TTL. A janela expira `window` após a 1ª tentativa, independente de
 * quantas vieram no meio, e o contador zera sozinho. Chaves distintas (ex.:
 * `IP + username`) têm janelas independentes.
 *
 * O [ticker] é injetável para teste determinístico do tempo (mesmo espírito do
 * `Random` injetável da engine): produção usa o relógio do sistema, testes
 * avançam um ticker falso sem `Thread.sleep`.
 */
class RateLimiter(
    private val maxAttempts: Int,
    private val window: Duration,
    ticker: Ticker = Ticker.systemTicker(),
) {
    private val counters: Cache<String, AtomicInteger> =
        Caffeine.newBuilder()
            .expireAfterWrite(window)
            // Teto alto de chaves: um flood de chaves distintas não deve despejar
            // (evicção por tamanho) o contador de uma vítima antes de a janela
            // fechar — o que zeraria o limite cedo e abriria brecha de bypass.
            .maximumSize(MAX_KEYS)
            .ticker(ticker)
            .build()

    /** `true` se a chave já atingiu o limite dentro da janela atual. */
    fun isBlocked(key: String): Boolean =
        (counters.getIfPresent(key)?.get() ?: 0) >= maxAttempts

    /** Registra uma tentativa contra a janela (criando a entrada se for a 1ª). */
    fun record(key: String) {
        counters.get(key) { AtomicInteger(0) }?.incrementAndGet()
    }

    /** Zera o contador (ex.: login bem-sucedido). */
    fun reset(key: String) = counters.invalidate(key)

    /**
     * Segundos até a janela da chave expirar — valor do header `Retry-After`.
     * Calcula o TEMPO RESTANTE real (janela − idade da entrada) via
     * `Policy.ageOf`, em vez de devolver sempre a janela inteira. Cai para a
     * janela cheia se a chave não existir; nunca abaixo de 1s.
     */
    fun retryAfterSeconds(key: String): Long {
        val remaining = counters.policy().expireAfterWrite()
            .flatMap { it.ageOf(key) }
            .map { age -> window.minus(age) }
            .orElse(window)
        return remaining.coerceAtLeast(Duration.ofSeconds(1)).seconds
    }

    private companion object {
        const val MAX_KEYS = 100_000L
    }
}
