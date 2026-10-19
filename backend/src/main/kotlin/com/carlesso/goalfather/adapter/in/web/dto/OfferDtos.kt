package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.ExpiryReason
import com.carlesso.goalfather.domain.model.OfferStatus
import com.carlesso.goalfather.domain.model.TransferOffer
import com.carlesso.goalfather.domain.result.OfferResult
import com.carlesso.goalfather.domain.rules.SquadViolation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * DTOs das propostas entre técnicos (issue #63) — contrato `TransferOffer`,
 * `CreateOfferRequest` e `TransferOfferResult`.
 *
 * `OfferStatus`, `ExpiryReason` e `SquadViolation` são reusados do domínio,
 * como a `Availability` em `Responses.kt`: a forma JSON deles já é EXATAMENTE
 * a do contrato (o `sealed` sai com o `type` discriminador).
 */

/** Proposta nova. Sem defaults: cada campo é uma decisão explícita do comprador. */
@Serializable
data class CreateOfferRequest(
    val buyerClubId: Long,
    val sellerClubId: Long,
    val playerId: Long,
    val amount: Long,
)

@Serializable
data class TransferOfferDto(
    val id: Long,
    val buyerClubId: Long,
    val buyerClubName: String,
    val sellerClubId: Long,
    val sellerClubName: String,
    val playerId: Long,
    val playerName: String,
    val amount: Long,
    val roundsLeft: Int,
    val status: OfferStatus,
)

/**
 * Espelha o `oneOf` + `discriminator` de `TransferOfferResult` no contrato. Só
 * as variantes que viajam no 200: os desfechos de 403/404 do domínio
 * (`NotOwner`, `ClubNotFound`, `OfferNotFound`) viram status HTTP no
 * controller e por isso nem existem aqui.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface TransferOfferResultDto {

    @Serializable
    @SerialName("Success")
    data class Success(val offer: TransferOfferDto) : TransferOfferResultDto

    @Serializable
    @SerialName("InvalidAmount")
    data class InvalidAmount(val minimum: Long) : TransferOfferResultDto

    @Serializable
    @SerialName("InsufficientFunds")
    data class InsufficientFunds(val available: Long, val required: Long) : TransferOfferResultDto

    @Serializable
    @SerialName("SquadFull")
    data class SquadFull(val maxSize: Int) : TransferOfferResultDto

    @Serializable
    @SerialName("PlayerNotAvailable")
    data class PlayerNotAvailable(val playerId: Long) : TransferOfferResultDto

    @Serializable
    @SerialName("SellerNotHuman")
    data class SellerNotHuman(val clubId: Long) : TransferOfferResultDto

    @Serializable
    @SerialName("SellerSquadInvalid")
    data class SellerSquadInvalid(val violation: SquadViolation, val minSquadSize: Int) : TransferOfferResultDto

    @Serializable
    @SerialName("OfferAlreadyPending")
    data class OfferAlreadyPending(val offerId: Long) : TransferOfferResultDto

    @Serializable
    @SerialName("OfferNotPending")
    data class OfferNotPending(val offerId: Long, val status: OfferStatus) : TransferOfferResultDto

    @Serializable
    @SerialName("OfferExpired")
    data class OfferExpired(val offerId: Long, val reason: ExpiryReason) : TransferOfferResultDto

    @Serializable
    @SerialName("RoundInProgress")
    data class RoundInProgress(val roundNumber: Int) : TransferOfferResultDto

    @Serializable
    @SerialName("Conflict")
    data class Conflict(val clubId: Long) : TransferOfferResultDto
}

fun TransferOffer.toDto(): TransferOfferDto = TransferOfferDto(
    id = id.value,
    buyerClubId = buyerClubId.value,
    buyerClubName = buyerClubName,
    sellerClubId = sellerClubId.value,
    sellerClubName = sellerClubName,
    playerId = playerId.value,
    playerName = playerName,
    amount = amount,
    roundsLeft = roundsLeft,
    status = status,
)

/**
 * `when` sobre a sub-hierarquia `Rejection`: exaustivo sem `else`, porque ela
 * também é `sealed`. Um erro de negócio novo no domínio não compila até ganhar
 * a sua forma no contrato.
 */
fun OfferResult.Rejection.toDto(): TransferOfferResultDto = when (this) {
    is OfferResult.InvalidAmount -> TransferOfferResultDto.InvalidAmount(minimum)
    is OfferResult.InsufficientFunds -> TransferOfferResultDto.InsufficientFunds(available, required)
    is OfferResult.SquadFull -> TransferOfferResultDto.SquadFull(maxSize)
    is OfferResult.PlayerNotAvailable -> TransferOfferResultDto.PlayerNotAvailable(playerId.value)
    is OfferResult.SellerNotHuman -> TransferOfferResultDto.SellerNotHuman(clubId.value)
    is OfferResult.SellerSquadInvalid -> TransferOfferResultDto.SellerSquadInvalid(violation, minSquadSize)
    is OfferResult.OfferAlreadyPending -> TransferOfferResultDto.OfferAlreadyPending(offerId.value)
    is OfferResult.OfferNotPending -> TransferOfferResultDto.OfferNotPending(offerId.value, status)
    is OfferResult.OfferExpired -> TransferOfferResultDto.OfferExpired(offerId.value, reason)
    is OfferResult.RoundInProgress -> TransferOfferResultDto.RoundInProgress(roundNumber)
    is OfferResult.Conflict -> TransferOfferResultDto.Conflict(clubId.value)
}
