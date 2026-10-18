package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Resultado de ampliar o estádio (issue #79).
 *
 * `BelowMinimumSeats` e `InsufficientFunds` são DESFECHOS do comando — vêm do
 * número que o técnico digitou —, então são variantes e não exceptions: o
 * `when` do controller fica exaustivo e o compilador cobra o tratamento de
 * qualquer variante nova (regra do CLAUDE.md).
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ExpandStadiumResult {

    @Serializable
    @SerialName("Success")
    data class Success(val club: Club) : ExpandStadiumResult

    @Serializable
    @SerialName("ClubNotFound")
    data class ClubNotFound(val clubId: ClubId) : ExpandStadiumResult

    @Serializable
    @SerialName("NotOwner")
    data class NotOwner(val clubId: ClubId) : ExpandStadiumResult

    @Serializable
    @SerialName("BelowMinimumSeats")
    data class BelowMinimumSeats(val requested: Int, val minimum: Int) : ExpandStadiumResult

    @Serializable
    @SerialName("InsufficientFunds")
    data class InsufficientFunds(val available: Long, val required: Long) : ExpandStadiumResult

    /**
     * Outra escrita alterou o clube entre a leitura e a gravação (lock otimista,
     * issue #89) — nada foi aplicado: nem caixa debitado, nem assentos.
     * Controller mapeia 409 `CLUB_WRITE_CONFLICT`.
     */
    @Serializable
    @SerialName("Conflict")
    data class Conflict(val clubId: ClubId) : ExpandStadiumResult
}
