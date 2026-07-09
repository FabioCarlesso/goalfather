package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

/**
 * Foto da prontidão de uma rodada em liga compartilhada (issue #20).
 *
 * Em multiplayer "jogar rodada" deixa de ser um clique solitário: a simulação
 * só dispara quando TODOS os técnicos humanos (donos de clube) sinalizaram
 * prontos — garantindo a todos tempo de escalar.
 *
 * `allReady` é uma propriedade computada (sem backing field, logo ignorada
 * pela serialização): a condição de destravamento é
 * `readyCount == totalCount`, com `totalCount > 0` para nunca liberar uma
 * liga sem nenhum técnico.
 *
 * `pendingUsernames` alimenta o 409 ("faltam fulano, beltrano") e o card de
 * lobby da UI — o cliente sabe se é a sua vez checando se o próprio username
 * está na lista.
 *
 * Escape hatch para técnico ausente (issue #45): um único dono offline não
 * pode mais travar a liga indefinidamente. Uma vez que o PRIMEIRO técnico
 * sinaliza pronto, começa a correr um timeout; ao expirar, a rodada destrava
 * mesmo com pendentes (ausentes entram com a última escalação salva). O
 * serviço preenche os campos abaixo — o domínio só os carrega, sem tocar no
 * relógio (fonte de tempo fica na camada de aplicação, testável com `Clock`).
 *
 * - `secondsRemaining`: segundos até o auto-start; `null` quando o relógio
 *   ainda não começou (ninguém pronto) ou já não importa (todos prontos).
 * - `timedOut`: o timeout expirou — a rodada pode iniciar apesar dos pendentes.
 * - `canStart`: gate efetivo do `start()` — todos prontos OU timeout estourado.
 */
@Serializable
data class ReadinessStatus(
    val roundNumber: Int,
    val readyCount: Int,
    val totalCount: Int,
    val pendingUsernames: List<String>,
    val secondsRemaining: Int? = null,
    val timedOut: Boolean = false,
) {
    val allReady: Boolean
        get() = totalCount > 0 && readyCount >= totalCount

    val canStart: Boolean
        get() = allReady || timedOut
}
