package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.ExpiryReason
import com.carlesso.goalfather.domain.model.OfferId
import com.carlesso.goalfather.domain.model.OfferStatus
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.TransferOffer
import com.carlesso.goalfather.domain.rules.SquadViolation

/**
 * Desfecho de um comando sobre propostas de transferência (issue #63): criar,
 * aceitar, recusar ou retirar. Erros de negócio como VALOR, nunca exception.
 *
 * **Hierarquia `sealed` em dois níveis.** [Rejection] é ela mesma uma `sealed
 * interface` DENTRO de [OfferResult]: agrupa os erros de NEGÓCIO, que viajam no
 * payload do 200 (como o `TransferResult` do mercado), separados dos três que
 * viram status HTTP ([ClubNotFound], [OfferNotFound], [NotOwner]). O controller
 * faz `when` com um ramo `is Rejection` e continua exaustivo — sem `else` —,
 * porque o compilador sabe que `Rejection` também é fechada. Uma variante nova
 * em qualquer nível quebra a compilação no lugar certo.
 */
sealed interface OfferResult {

    /** Comando aplicado; [offer] traz o estado novo. */
    data class Success(val offer: TransferOffer) : OfferResult

    /** O clube do comando não existe → 404. */
    data class ClubNotFound(val clubId: ClubId) : OfferResult

    /** A proposta não existe → 404. */
    data class OfferNotFound(val offerId: OfferId) : OfferResult

    /**
     * O usuário não pode agir em nome deste lado da proposta → 403: só o dono
     * do comprador propõe e retira; só o dono do vendedor aceita e recusa.
     */
    data object NotOwner : OfferResult

    /** Erros de negócio — viajam no 200 (contrato `TransferOfferResult`). */
    sealed interface Rejection : OfferResult

    /** Valor da proposta abaixo do mínimo. */
    data class InvalidAmount(val minimum: Long) : Rejection

    /** O comprador não tem caixa para a proposta. */
    data class InsufficientFunds(val available: Long, val required: Long) : Rejection

    /** O elenco do comprador já está no limite. */
    data class SquadFull(val maxSize: Int) : Rejection

    /** O jogador não está (mais) no elenco do vendedor — ou é do próprio comprador. */
    data class PlayerNotAvailable(val playerId: PlayerId) : Rejection

    /** Clube da IA não negocia: a proposta é entre técnicos humanos. */
    data class SellerNotHuman(val clubId: ClubId) : Rejection

    /** Vender deixaria o elenco do vendedor inválido — ver [SquadViolation]. */
    data class SellerSquadInvalid(val violation: SquadViolation, val minSquadSize: Int) : Rejection

    /** A proposta já foi aceita, recusada ou retirada. */
    data class OfferNotPending(val offerId: OfferId, val status: OfferStatus) : Rejection

    /** A proposta expirou — prazo vencido ou jogador vendido a outro. */
    data class OfferExpired(val offerId: OfferId, val reason: ExpiryReason) : Rejection

    /**
     * Aceite bloqueado durante a simulação da rodada (issue #63): o jogador não
     * troca de clube no meio do processamento.
     */
    data class RoundInProgress(val roundNumber: Int) : Rejection

    /** Lock otimista de um dos clubes (issue #89): nada foi aplicado. */
    data class Conflict(val clubId: ClubId) : Rejection
}
