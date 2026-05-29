package com.carlesso.goalfather.domain.event

import com.carlesso.goalfather.domain.model.PlayerId

/**
 * Eventos de uma partida — `sealed interface` Kotlin.
 *
 * Espelha o schema `MatchEvent` no `contract/openapi.yaml`
 * (`oneOf` + `discriminator: type`). Quando serializado via
 * kotlinx.serialization com `@JsonClassDiscriminator("type")`, cada
 * variante vira `{"type": "Goal", "minute": 23, ...}` — exatamente o
 * formato que os mocks MSW já produzem e que o frontend já consome.
 *
 * Consumidores usam `when (event) is ...` EXAUSTIVO (sem `else`).
 * O compilador recusa código que não cobre todas as variantes — esse
 * é o ponto de aprendizado central do projeto.
 */
sealed interface MatchEvent {
    val minute: Int

    /** Primeiro evento do stream. Carrega metadados dos times. */
    data class KickOff(
        override val minute: Int = 0,
        val homeClubName: String,
        val awayClubName: String,
        val homeStrength: Double,
        val awayStrength: Double,
    ) : MatchEvent

    data class Goal(
        override val minute: Int,
        val scorerId: PlayerId,
        val home: Boolean,
    ) : MatchEvent

    data class Card(
        override val minute: Int,
        val playerId: PlayerId,
        val red: Boolean,
    ) : MatchEvent

    data class Injury(
        override val minute: Int,
        val playerId: PlayerId,
    ) : MatchEvent

    data class Save(override val minute: Int) : MatchEvent

    /** Último evento do stream. Carrega o placar final. */
    data class FullTime(
        override val minute: Int = 90,
        val homeGoals: Int,
        val awayGoals: Int,
    ) : MatchEvent
}
