package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.rules.PlayerResponse
import com.carlesso.goalfather.domain.rules.RegistrationLimit
import com.carlesso.goalfather.domain.serialization.LocalDateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import java.time.LocalDate

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
    data class InsufficientFunds(val available: Money, val required: Money) : TransferResult

    @Serializable
    @SerialName("SquadFull")
    data class SquadFull(val maxSize: Int) : TransferResult

    @Serializable
    @SerialName("PlayerNotAvailable")
    data class PlayerNotAvailable(val playerId: PlayerId) : TransferResult

    /**
     * Outra escrita alterou o clube entre a leitura e a gravação (lock otimista,
     * issue #89) — caixa, elenco e mercado ficam como estavam.
     * Vai no payload do 200, como as demais variantes (contrato `TransferConflict`).
     */
    @Serializable
    @SerialName("Conflict")
    data class Conflict(val clubId: ClubId) : TransferResult

    /**
     * Janela de transferência fechada para o clube (issue #137): o do
     * comprador na compra, o do vendedor na venda ao mercado. [nextOpens] é a
     * próxima abertura (`null` = federação sem calendário). `@Serializable(with
     * = ...)` no PARÂMETRO troca o serializer só deste campo — o `LocalDate`
     * do JDK não tem um nativo no kotlinx.
     */
    @Serializable
    @SerialName("TransferWindowClosed")
    data class TransferWindowClosed(
        @Serializable(with = LocalDateSerializer::class) val nextOpens: LocalDate?,
    ) : TransferResult

    /** O jogador já esgotou o limite de registros da janela ou da temporada (issue #137). */
    @Serializable
    @SerialName("TransferLimitReached")
    data class TransferLimitReached(val playerId: PlayerId, val limit: RegistrationLimit) : TransferResult

    /**
     * O jogador recusou os termos pessoais oferecidos na compra (issue #138):
     * nada foi aplicado — caixa, elenco e mercado ficam como estavam. A recusa
     * traz o motivo e o que ele aceitaria.
     */
    @Serializable
    @SerialName("PlayerRefused")
    data class PlayerRefused(val playerId: PlayerId, val refusal: PlayerResponse.Refuses) : TransferResult
}
