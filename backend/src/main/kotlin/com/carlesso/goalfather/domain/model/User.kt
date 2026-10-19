package com.carlesso.goalfather.domain.model

/**
 * Identificador tipado de usuário. Mesma técnica do [ClubId]: `value class`
 * dá type-safety em compilação sem custo de boxing em runtime.
 */
@JvmInline
value class UserId(val value: Long)

/**
 * Usuário do sistema — dono (em potencial) de um clube.
 *
 * `clubId == null` enquanto o usuário ainda não reivindicou um clube
 * (fluxo de seleção da issue #19). O `passwordHash` é mantido aqui porque
 * a verificação de login pertence à regra de autenticação; ainda assim é
 * só uma `String` (BCrypt), sem qualquer dependência de framework — o
 * domínio segue puro. Nunca é exposto na API (DTOs separam o que sai).
 *
 * `dismissal` (issue #62) é a demissão mais recente: preenchida quando a
 * diretoria demite o técnico (e o clube volta para a IA), apagada quando ele
 * reivindica um clube novo. É o que leva a UI à tela de "game over".
 */
data class User(
    val id: UserId,
    val username: String,
    val passwordHash: String,
    val clubId: ClubId? = null,
    val dismissal: Dismissal? = null,
)
