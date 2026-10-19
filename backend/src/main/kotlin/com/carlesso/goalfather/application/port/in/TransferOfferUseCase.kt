package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.OfferId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.TransferOffer
import com.carlesso.goalfather.domain.result.OfferResult

/**
 * Propostas de transferência entre técnicos humanos (issue #63). Uma porta
 * para o ciclo de vida inteiro do agregado — como a `RoundReadinessUseCase` —,
 * porque os comandos compartilham as mesmas regras e o mesmo repositório.
 *
 * `requesterId` é o usuário autenticado: cada comando confere se ele é o dono
 * do lado certo da proposta (comprador propõe e retira; vendedor aceita e
 * recusa) e devolve [OfferResult.NotOwner] caso contrário.
 */
interface TransferOfferUseCase {

    /** Propostas em que [clubId] é comprador ou vendedor, da mais nova para a mais antiga. */
    suspend fun offersOf(clubId: ClubId): List<TransferOffer>

    suspend fun create(
        buyerClubId: ClubId,
        sellerClubId: ClubId,
        playerId: PlayerId,
        amount: Long,
        requesterId: Long,
    ): OfferResult

    suspend fun accept(offerId: OfferId, requesterId: Long): OfferResult

    suspend fun reject(offerId: OfferId, requesterId: Long): OfferResult

    suspend fun withdraw(offerId: OfferId, requesterId: Long): OfferResult
}
