package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Resultado de uma sessão do departamento médico (issue #54).
 * Espelha `MedicalResult` no `contract/openapi.yaml`.
 *
 * Caixa insuficiente é uma VARIANTE, não uma exception: erro de negócio como
 * valor é a regra do CLAUDE.md, e deixa o `when` do controller exaustivo.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface MedicalResult {

    @Serializable
    @SerialName("Success")
    data class Success(val club: Club) : MedicalResult

    @Serializable
    @SerialName("ClubNotFound")
    data class ClubNotFound(val clubId: ClubId) : MedicalResult

    @Serializable
    @SerialName("NotOwner")
    data class NotOwner(val clubId: ClubId) : MedicalResult

    @Serializable
    @SerialName("InsufficientFunds")
    data class InsufficientFunds(val available: Long, val required: Long) : MedicalResult
}
