package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Resultado de uma transferência (compra ou venda).
 * Espelha `TransferResult` no `contract/openapi.yaml`.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface TransferResult {

    @Serializable
    @SerialName("Success")
    data class Success(val club: Club, val player: Player) : TransferResult

    @Serializable
    @SerialName("InsufficientFunds")
    data class InsufficientFunds(val available: Long, val required: Long) : TransferResult

    @Serializable
    @SerialName("SquadFull")
    data class SquadFull(val maxSize: Int) : TransferResult

    @Serializable
    @SerialName("PlayerNotAvailable")
    data class PlayerNotAvailable(val playerId: PlayerId) : TransferResult
}
