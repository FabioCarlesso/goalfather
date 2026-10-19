package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.OfferId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.TransferOffer

/**
 * Port de saída das propostas de transferência entre técnicos (issue #63).
 *
 * Toda gravação de proposta existente é CONDICIONAL a ela ainda estar
 * `Pending` — o compare-and-set da máquina de estados. Como todo estado além
 * de `Pending` é terminal, "ainda pendente" é a única versão que importa: duas
 * escritas que partiram da mesma leitura (aceite × recusa, aceite × prazo
 * vencendo na virada da rodada) não podem ambas valer, e a segunda recebe
 * `false` em vez de sobrescrever a primeira.
 */
interface TransferOfferRepository {

    /** Grava uma proposta nova e a devolve com o id gerado (o de [offer] é ignorado). */
    suspend fun create(offer: TransferOffer): TransferOffer

    suspend fun findById(id: OfferId): TransferOffer?

    /**
     * Propostas em que [clubId] é comprador OU vendedor: TODAS as pendentes,
     * seguidas do histórico recente (limitado), cada grupo da mais nova para a
     * mais antiga. Pendente nunca fica de fora da lista por causa do histórico.
     */
    suspend fun findByClub(clubId: ClubId): List<TransferOffer>

    /** Todas as propostas aguardando resposta — o relógio do prazo (virada da rodada). */
    suspend fun findPending(): List<TransferOffer>

    /** Propostas pendentes pelo mesmo jogador — as rivais de um aceite. */
    suspend fun findPendingForPlayer(playerId: PlayerId): List<TransferOffer>

    /**
     * Grava o estado novo de [offer] (status e prazo) SÓ se a proposta ainda
     * está pendente no banco. `false` = outra escrita a resolveu antes.
     */
    suspend fun saveIfPending(offer: TransferOffer): Boolean

    /**
     * Executa a transferência de uma proposta aceita numa ÚNICA transação: grava
     * [seller] e [buyer] (caixa e elenco já movidos) sob o lock otimista de cada
     * clube, marca [accepted] como aceita e expira as [rivals] — ou nada disso.
     */
    suspend fun completeTransfer(
        accepted: TransferOffer,
        seller: Club,
        buyer: Club,
        rivals: List<TransferOffer>,
    ): TransferCompletion
}

/**
 * Desfecho de [TransferOfferRepository.completeTransfer]. Em qualquer variante
 * que não [Completed] a transação foi desfeita inteira: nenhum clube, nenhuma
 * proposta mudou.
 */
sealed interface TransferCompletion {
    /** Jogador e dinheiro mudaram de mãos; as rivais expiraram. */
    data object Completed : TransferCompletion

    /** A proposta deixou de estar pendente antes do commit (recusada, retirada, expirada). */
    data object OfferNoLongerPending : TransferCompletion

    /** Outra escrita alterou [clubId] depois da leitura (issue #89). */
    data class Conflict(val clubId: ClubId) : TransferCompletion
}
