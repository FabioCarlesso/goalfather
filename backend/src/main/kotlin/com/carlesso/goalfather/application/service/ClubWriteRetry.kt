package com.carlesso.goalfather.application.service

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Tentativas de um read-modify-write de clube antes de desistir (issue #89).
 * Conflito é raro (dois comandos no mesmo clube no mesmo instante), então
 * poucas tentativas — espaçadas pelo [clubWriteBackoff] — cobrem a corrida real
 * sem transformar contenção patológica num loop infinito.
 */
internal const val MAX_CLUB_WRITE_ATTEMPTS: Int = 4

/** Janela da 1ª espera entre tentativas; dobra a cada nova tentativa. */
internal const val CLUB_WRITE_BACKOFF_BASE_MS: Long = 10

/**
 * Espera antes da repetição número [retryIndex] (1 = a primeira): backoff
 * exponencial com *jitter* — `janela + aleatório em [0, janela)`, com a janela
 * dobrando a cada vez (10–20 ms, 20–40 ms, 40–80 ms).
 *
 * Por que (revisão do PR #92): sem espera, quem perde o lock relê e regrava no
 * mesmo instante e colide de novo com o mesmo escritor, esgotando as tentativas
 * sob contenção contínua. O exponencial dá tempo ao vencedor; o jitter
 * desencontra perdedores que, sem ele, voltariam juntos e colidiriam entre si.
 * O `Random` é só de temporização — não decide nada do jogo —, mas é injetável
 * mesmo assim, para teste com seed fixa.
 */
internal fun clubWriteBackoff(retryIndex: Int, random: Random = Random.Default): Long {
    require(retryIndex >= 1) { "retryIndex começa em 1: $retryIndex" }
    val window = CLUB_WRITE_BACKOFF_BASE_MS shl (retryIndex - 1)
    return window + random.nextLong(window)
}

/**
 * Reexecuta [attempt] enquanto o desfecho for conflito de lock otimista, até
 * [maxAttempts] vezes, esperando [backoff] entre uma e outra, e devolve o
 * último desfecho — que pode continuar sendo o conflito, para o chamador
 * tratar como valor.
 *
 * Só serve para comandos que podem ser REPETIDOS do zero: cada tentativa tem
 * de reler o clube e revalidar as regras, porque a anterior não gravou nada.
 *
 * `inline` não é só desempenho aqui: o corpo dos lambdas é copiado no ponto de
 * chamada, então [attempt] pode chamar funções `suspend` e um `return` dentro
 * dele sai da função que chamou o helper (*non-local return*). É `suspend`
 * por causa do `delay` do backoff — que, ao contrário de `Thread.sleep`, não
 * prende a thread enquanto espera (e, em `runTest`, avança em tempo virtual).
 *
 * @param attempt recebe o índice da tentativa (0 = primeira).
 */
internal suspend inline fun <R> retryOnConflict(
    maxAttempts: Int = MAX_CLUB_WRITE_ATTEMPTS,
    backoff: (retryIndex: Int) -> Long = { clubWriteBackoff(it) },
    isConflict: (R) -> Boolean,
    attempt: (attemptIndex: Int) -> R,
): R {
    require(maxAttempts >= 1) { "maxAttempts deve ser >= 1: $maxAttempts" }
    var result = attempt(0)
    for (index in 1 until maxAttempts) {
        if (!isConflict(result)) break
        delay(backoff(index))
        result = attempt(index)
    }
    return result
}
