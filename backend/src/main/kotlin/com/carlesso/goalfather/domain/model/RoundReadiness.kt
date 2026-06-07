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
 */
@Serializable
data class ReadinessStatus(
    val roundNumber: Int,
    val readyCount: Int,
    val totalCount: Int,
    val pendingUsernames: List<String>,
) {
    val allReady: Boolean
        get() = totalCount > 0 && readyCount >= totalCount
}
