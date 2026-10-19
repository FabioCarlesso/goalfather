package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.TransferOfferEntity
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.ExpiryReason
import com.carlesso.goalfather.domain.model.OfferId
import com.carlesso.goalfather.domain.model.OfferStatus
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.TransferOffer

/**
 * Tradução proposta ↔ linha (issue #63). O `sealed OfferStatus` vira duas
 * colunas de texto e volta; os `when` são exaustivos nos dois sentidos, então
 * um estado novo no domínio não compila até ganhar sua forma no banco.
 */
fun TransferOfferEntity.toDomain(): TransferOffer = TransferOffer(
    id = OfferId(id),
    buyerClubId = ClubId(buyerClubId),
    buyerClubName = buyerClubName,
    sellerClubId = ClubId(sellerClubId),
    sellerClubName = sellerClubName,
    playerId = PlayerId(playerId),
    playerName = playerName,
    amount = amount,
    roundsLeft = roundsLeft,
    status = offerStatusOf(status, expiryReason),
)

fun TransferOffer.toEntity(): TransferOfferEntity = TransferOfferEntity(
    // id = 0 sinaliza insert (IDENTITY gera o real), como no `UserMapper`.
    id = id.value,
    buyerClubId = buyerClubId.value,
    buyerClubName = buyerClubName,
    sellerClubId = sellerClubId.value,
    sellerClubName = sellerClubName,
    playerId = playerId.value,
    playerName = playerName,
    amount = amount,
    roundsLeft = roundsLeft,
    status = status.column(),
    expiryReason = status.expiryReasonColumn(),
)

/** Texto da coluna `status`. */
fun OfferStatus.column(): String = when (this) {
    OfferStatus.Pending -> TransferOfferEntity.PENDING
    OfferStatus.Accepted -> "ACCEPTED"
    OfferStatus.Rejected -> "REJECTED"
    OfferStatus.Withdrawn -> "WITHDRAWN"
    is OfferStatus.Expired -> "EXPIRED"
}

/** Texto da coluna `expiry_reason` — só existe para [OfferStatus.Expired]. */
fun OfferStatus.expiryReasonColumn(): String? = (this as? OfferStatus.Expired)?.reason?.name

/**
 * Linha → `sealed`. Um EXPIRED sem motivo legível (linha escrita à mão, motivo
 * removido do enum) cai em `DEADLINE` em vez de derrubar a leitura: o fato que
 * importa — a proposta morreu — continua verdadeiro. Status desconhecido é
 * dado corrompido, e aí sim falha alto.
 */
private fun offerStatusOf(status: String, expiryReason: String?): OfferStatus = when (status) {
    TransferOfferEntity.PENDING -> OfferStatus.Pending
    "ACCEPTED" -> OfferStatus.Accepted
    "REJECTED" -> OfferStatus.Rejected
    "WITHDRAWN" -> OfferStatus.Withdrawn
    "EXPIRED" -> OfferStatus.Expired(
        ExpiryReason.entries.firstOrNull { it.name == expiryReason } ?: ExpiryReason.DEADLINE,
    )
    else -> error("Status de proposta desconhecido no banco: $status")
}
