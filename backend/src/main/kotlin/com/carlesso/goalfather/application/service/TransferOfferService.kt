package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.TransferOfferUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.TransferCompletion
import com.carlesso.goalfather.application.port.out.TransferOfferRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.ExpiryReason
import com.carlesso.goalfather.domain.model.OfferId
import com.carlesso.goalfather.domain.model.OfferStatus
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.TransferOffer
import com.carlesso.goalfather.domain.result.OfferResult
import com.carlesso.goalfather.domain.rules.OFFER_DEADLINE_ROUNDS
import com.carlesso.goalfather.domain.rules.offerBlocker

/**
 * Implementação de [TransferOfferUseCase] (issue #63). As regras — o que
 * bloqueia uma transferência, quais transições existem — são do domínio
 * (`offerBlocker`, `TransferOffer.accept()`...); aqui só se orquestra leitura,
 * posse e gravação.
 */
class TransferOfferService(
    private val offerRepo: TransferOfferRepository,
    private val clubRepo: ClubRepository,
    private val leagueRepo: LeagueRepository,
) : TransferOfferUseCase {

    override suspend fun offersOf(clubId: ClubId): List<TransferOffer> = offerRepo.findByClub(clubId)

    override suspend fun create(
        buyerClubId: ClubId,
        sellerClubId: ClubId,
        playerId: PlayerId,
        amount: Long,
        requesterId: Long,
    ): OfferResult {
        val buyer = clubRepo.findById(buyerClubId) ?: return OfferResult.ClubNotFound(buyerClubId)
        if (buyer.ownerId != requesterId) return OfferResult.NotOwner
        val seller = clubRepo.findById(sellerClubId) ?: return OfferResult.ClubNotFound(sellerClubId)

        // Mesmas regras do aceite, rodadas já na criação: o comprador fica
        // sabendo agora que o vendedor não pode abrir mão do goleiro, em vez de
        // descobrir rodadas depois, quando o vendedor tentar aceitar.
        offerBlocker(buyer, seller, playerId, amount)?.let { return it }
        // Uma pendente por comprador × jogador (revisão do PR #94). É checagem
        // de leitura: dois cliques simultâneos ainda podem gravar duas — o que
        // não corrompe nada (o aceite de uma expira a outra como rival).
        offerRepo.findPendingForPlayer(playerId)
            .firstOrNull { it.buyerClubId == buyer.id }
            ?.let { return OfferResult.OfferAlreadyPending(it.id) }
        // `offerBlocker` garante que o jogador está no elenco do vendedor.
        val player = seller.squad.first { it.id == playerId }

        val offer = offerRepo.create(
            TransferOffer(
                id = NEW_OFFER,
                buyerClubId = buyer.id,
                buyerClubName = buyer.name,
                sellerClubId = seller.id,
                sellerClubName = seller.name,
                playerId = player.id,
                playerName = player.name,
                amount = amount,
                roundsLeft = OFFER_DEADLINE_ROUNDS,
            ),
        )
        return OfferResult.Success(offer)
    }

    /**
     * Aceite = a transferência de fato. Repetido do zero no conflito de lock
     * otimista de um dos clubes (issue #89), como a escalação: cada tentativa
     * relê proposta e clubes e revalida TUDO — a escrita concorrente pode ter
     * sido outro aceite pelo mesmo jogador, e aí a releitura encontra esta
     * proposta já expirada (`OfferExpired`), que é o desfecho certo do perdedor.
     */
    override suspend fun accept(offerId: OfferId, requesterId: Long): OfferResult =
        retryOnConflict(isConflict = { it is OfferResult.Conflict }) {
            attemptAccept(offerId, requesterId)
        }

    private suspend fun attemptAccept(offerId: OfferId, requesterId: Long): OfferResult {
        val offer = offerRepo.findById(offerId) ?: return OfferResult.OfferNotFound(offerId)
        val seller = clubRepo.findById(offer.sellerClubId) ?: return OfferResult.ClubNotFound(offer.sellerClubId)
        if (seller.ownerId != requesterId) return OfferResult.NotOwner
        val accepted = offer.accept() ?: return closed(offer)

        // Guarda da rodada (issue #63): durante a simulação o jogador não troca
        // de clube. É uma checagem de leitura, não um lock — se a rodada começar
        // entre ela e o commit, o lock otimista dos clubes ainda impede que a
        // finalização da rodada e a transferência se pisem (a rodada relê e
        // reaplica, issue #89); o que a guarda evita é o caso comum, o técnico
        // aceitando com a rodada já rolando na tela dele.
        leagueRepo.findLatest()
            ?.takeIf { it.status == RoundStatus.InProgress }
            ?.let { return OfferResult.RoundInProgress(it.number) }

        val buyer = clubRepo.findById(offer.buyerClubId) ?: return OfferResult.ClubNotFound(offer.buyerClubId)
        when (val blocker = offerBlocker(buyer, seller, offer.playerId, offer.amount)) {
            null -> Unit
            // O jogador saiu do elenco do vendedor desde a proposta (venda ao
            // mercado, aposentadoria): nenhum aceite futuro daria certo, então
            // a proposta morre agora em vez de seguir pendente com um "Aceitar"
            // fadado a falhar (revisão do PR #94).
            is OfferResult.PlayerNotAvailable -> return expireOrphan(offer)
            // Os demais podem mudar (caixa do comprador, elenco do vendedor):
            // a proposta segue pendente e o vendedor pode tentar de novo.
            else -> return blocker
        }
        val player = seller.squad.first { it.id == offer.playerId }

        // Quem disputava o mesmo jogador expira junto, na mesma transação.
        // `mapNotNull`: a transição devolve `null` para quem já não estava
        // pendente — e esse não precisa mudar.
        val rivals = offerRepo.findPendingForPlayer(offer.playerId)
            .filter { it.id != offer.id }
            .mapNotNull { it.expire(ExpiryReason.PLAYER_TRANSFERRED) }

        val completion = offerRepo.completeTransfer(
            accepted = accepted,
            seller = seller.copy(
                cash = seller.cash + offer.amount,
                squad = seller.squad.filterNot { it.id == player.id },
            ),
            buyer = buyer.copy(
                cash = buyer.cash - offer.amount,
                squad = buyer.squad + player,
            ),
            rivals = rivals,
        )
        return when (completion) {
            TransferCompletion.Completed -> OfferResult.Success(accepted)
            // Recusada/retirada/expirada entre a leitura e o commit: relê para
            // contar ao vendedor o que de fato aconteceu com ela.
            TransferCompletion.OfferNoLongerPending -> current(offerId)
            is TransferCompletion.Conflict -> OfferResult.Conflict(completion.clubId)
        }
    }

    override suspend fun reject(offerId: OfferId, requesterId: Long): OfferResult =
        resolve(offerId, requesterId, side = { it.sellerClubId }, transition = TransferOffer::reject)

    override suspend fun withdraw(offerId: OfferId, requesterId: Long): OfferResult =
        resolve(offerId, requesterId, side = { it.buyerClubId }, transition = TransferOffer::withdraw)

    /**
     * Recusa e retirada: só a proposta muda, nenhum clube. [side] diz qual
     * clube o usuário precisa possuir; [transition] é a transição do domínio —
     * passada como *function reference* (`TransferOffer::reject`), o idioma
     * Kotlin para "estratégia" sem interface de uma função só.
     */
    private suspend fun resolve(
        offerId: OfferId,
        requesterId: Long,
        side: (TransferOffer) -> ClubId,
        transition: (TransferOffer) -> TransferOffer?,
    ): OfferResult {
        val offer = offerRepo.findById(offerId) ?: return OfferResult.OfferNotFound(offerId)
        val club = clubRepo.findById(side(offer)) ?: return OfferResult.ClubNotFound(side(offer))
        if (club.ownerId != requesterId) return OfferResult.NotOwner
        val next = transition(offer) ?: return closed(offer)
        return if (offerRepo.saveIfPending(next)) OfferResult.Success(next) else current(offerId)
    }

    /** Expira [offer] por jogador transferido e conta isso ao vendedor. */
    private suspend fun expireOrphan(offer: TransferOffer): OfferResult {
        val expired = offer.expire(ExpiryReason.PLAYER_TRANSFERRED) ?: return closed(offer)
        return if (offerRepo.saveIfPending(expired)) closed(expired) else current(offer.id)
    }

    /** Relê a proposta que deixou de estar pendente e explica por quê. */
    private suspend fun current(offerId: OfferId): OfferResult =
        offerRepo.findById(offerId)?.let(::closed) ?: OfferResult.OfferNotFound(offerId)

    /**
     * Erro de quem tentou agir sobre uma proposta que já não está pendente.
     * `Pending` só chega aqui se a releitura correu com outra escrita que ainda
     * não comitou — "não está mais pendente" continua sendo o que o comando
     * encontrou, então vai junto do status lido.
     */
    private fun closed(offer: TransferOffer): OfferResult.Rejection = when (val status = offer.status) {
        is OfferStatus.Expired -> OfferResult.OfferExpired(offer.id, status.reason)
        OfferStatus.Pending, OfferStatus.Accepted, OfferStatus.Rejected, OfferStatus.Withdrawn ->
            OfferResult.OfferNotPending(offer.id, status)
    }

    private companion object {
        /** Id de proposta ainda não gravada — o repositório gera o real. */
        val NEW_OFFER = OfferId(0)
    }
}
