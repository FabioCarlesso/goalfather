package com.carlesso.goalfather.domain.event

import com.carlesso.goalfather.domain.model.RoundFinance
import com.carlesso.goalfather.domain.model.Standings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Mensagem do stream de rodada (WS `/ws/round/{n}`).
 *
 * Multiplexa eventos de várias partidas + sinal de fim de rodada.
 * Mapeia o schema `RoundEvent` no `contract/openapi.yaml` (oneOf +
 * discriminator `type`).
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface RoundEvent {

    @Serializable
    @SerialName("MatchUpdate")
    data class MatchUpdate(
        val matchId: Long,
        val event: MatchEvent,
    ) : RoundEvent

    @Serializable
    @SerialName("RoundFinished")
    data class RoundFinished(
        val standings: Standings,
        val finances: List<RoundFinance> = emptyList(),
    ) : RoundEvent
}
