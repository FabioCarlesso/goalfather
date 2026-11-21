package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.ContractOffer
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.rules.PlayerResponse
import com.carlesso.goalfather.domain.rules.RefusalReason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * DTOs da negociação de termos pessoais (issue #138) — contrato
 * `ContractOffer`, `PlayerRefused` e `RenewalResult`.
 */

/**
 * Termos oferecidos ao jogador: no corpo da compra, da proposta e da
 * renovação, e de volta na `TransferOffer`. Valores em centavos.
 */
@Serializable
data class ContractOfferDto(
    val salary: Long,
    val years: Int,
    val releaseClause: Long? = null,
) {
    /**
     * Para o domínio, ou `null` quando o pedido não faz sentido (salário
     * negativo, cláusula não positiva) — o controller responde 400 sem deixar
     * o `require` do [ContractOffer] virar um 500. A duração não é validada
     * aqui: fora do que o jogador topa, é a RESPOSTA dele (`TOO_SHORT`/`TOO_LONG`).
     */
    fun toDomain(): ContractOffer? =
        if (salary < 0 || (releaseClause != null && releaseClause <= 0)) null
        else ContractOffer(Money(salary), years, releaseClause?.let(::Money))
}

fun ContractOffer.toDto(): ContractOfferDto =
    ContractOfferDto(salary = salary.cents, years = years, releaseClause = releaseClause?.cents)

/** Corpo do 400 de termos inválidos — o mesmo nas três rotas. */
val INVALID_CONTRACT_OFFER: ErrorResponse = ErrorResponse(
    code = "INVALID_CONTRACT_OFFER",
    message = "Termos inválidos: salário não pode ser negativo e a cláusula, se houver, deve ser positiva",
)

/**
 * Resultado da renovação — espelha o `oneOf` + `discriminator` de
 * `RenewalResult` no contrato.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface RenewalResultDto {

    @Serializable
    @SerialName("Renewed")
    data class Renewed(val player: PlayerDto) : RenewalResultDto

    /** Mesmo schema `PlayerRefused` da compra e da proposta, com o esfriamento. */
    @Serializable
    @SerialName("PlayerRefused")
    data class PlayerRefused(
        val playerId: Long,
        val reason: RefusalReason,
        val demandedSalary: Long,
        val minYears: Int,
        val maxYears: Int,
        val cooldownUntil: String? = null,
    ) : RenewalResultDto

    @Serializable
    @SerialName("TalksCoolingDown")
    data class TalksCoolingDown(val playerId: Long, val until: String) : RenewalResultDto

    @Serializable
    @SerialName("PlayerNotAvailable")
    data class PlayerNotAvailable(val playerId: Long) : RenewalResultDto

    @Serializable
    @SerialName("Conflict")
    data class Conflict(val clubId: Long) : RenewalResultDto
}

/** A recusa como a API a descreve nas três rotas: a faixa de durações em duas pontas. */
fun PlayerResponse.Refuses.toRenewalDto(playerId: Long, cooldownUntil: String?): RenewalResultDto.PlayerRefused =
    RenewalResultDto.PlayerRefused(playerId, reason, demandedSalary.cents, minYears, maxYears, cooldownUntil)
