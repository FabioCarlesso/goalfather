package com.carlesso.goalfather.application.service

/**
 * Tentativas de um read-modify-write de clube antes de desistir (issue #89).
 * Conflito é raro (dois comandos no mesmo clube no mesmo instante), então três
 * tentativas cobrem a corrida real sem transformar contenção patológica num
 * loop infinito.
 */
internal const val MAX_CLUB_WRITE_ATTEMPTS: Int = 3

/**
 * Reexecuta [attempt] enquanto o desfecho for conflito de lock otimista, até
 * [maxAttempts] vezes, e devolve o último desfecho — que pode continuar sendo
 * o conflito, para o chamador tratar como valor.
 *
 * Só serve para comandos que podem ser REPETIDOS do zero: cada tentativa tem
 * de reler o clube e revalidar as regras, porque a anterior não gravou nada.
 *
 * `inline` não é só desempenho aqui: o corpo dos lambdas é copiado no ponto
 * de chamada, então [attempt] pode chamar funções `suspend` (o chamador é
 * `suspend`) sem que este helper precise ser `suspend` — e um `return` dentro
 * do lambda sai da função que chamou o helper (*non-local return*).
 *
 * @param attempt recebe o índice da tentativa (0 = primeira).
 */
internal inline fun <R> retryOnConflict(
    maxAttempts: Int = MAX_CLUB_WRITE_ATTEMPTS,
    isConflict: (R) -> Boolean,
    attempt: (attemptIndex: Int) -> R,
): R {
    require(maxAttempts >= 1) { "maxAttempts deve ser >= 1: $maxAttempts" }
    var result = attempt(0)
    for (index in 1 until maxAttempts) {
        if (!isConflict(result)) break
        result = attempt(index)
    }
    return result
}
