package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.entity.TransferOfferEntity
import com.carlesso.goalfather.adapter.out.persistence.mapper.column
import com.carlesso.goalfather.adapter.out.persistence.mapper.expiryReasonColumn
import com.carlesso.goalfather.adapter.out.persistence.mapper.toDomain
import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.TransferOfferJpaRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.TransferCompletion
import com.carlesso.goalfather.application.port.out.TransferOfferRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.OfferId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.TransferOffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport

/**
 * Implementação do port [TransferOfferRepository] sobre Spring Data JPA
 * (issue #63). Mesma ponte dos outros adapters: a coroutine só leva a chamada
 * ao pool de I/O, e o que precisa ser atômico roda num método síncrono de
 * [TransferOfferTransaction] — `@Transactional` sobre `suspend` é frágil,
 * porque o `withContext` troca de thread e a transação é thread-bound.
 */
@Repository
class TransferOfferPersistenceAdapter(
    private val offerRepo: TransferOfferJpaRepository,
    private val tx: TransferOfferTransaction,
) : TransferOfferRepository {

    override suspend fun create(offer: TransferOffer): TransferOffer = withContext(Dispatchers.IO) {
        offerRepo.save(offer.toEntity().apply { id = 0 }).toDomain()
    }

    override suspend fun findById(id: OfferId): TransferOffer? = withContext(Dispatchers.IO) {
        offerRepo.findById(id.value).orElse(null)?.toDomain()
    }

    override suspend fun findByClub(clubId: ClubId): List<TransferOffer> = withContext(Dispatchers.IO) {
        offerRepo.findTop50ByBuyerClubIdOrSellerClubIdOrderByIdDesc(clubId.value, clubId.value).map { it.toDomain() }
    }

    override suspend fun findPending(): List<TransferOffer> = withContext(Dispatchers.IO) {
        offerRepo.findAllByStatus(TransferOfferEntity.PENDING).map { it.toDomain() }
    }

    override suspend fun findPendingForPlayer(playerId: PlayerId): List<TransferOffer> =
        withContext(Dispatchers.IO) {
            offerRepo.findAllByPlayerIdAndStatus(playerId.value, TransferOfferEntity.PENDING).map { it.toDomain() }
        }

    override suspend fun saveIfPending(offer: TransferOffer): Boolean = withContext(Dispatchers.IO) {
        try {
            tx.saveIfPending(offer)
        } catch (_: ConcurrencyFailureException) {
            // Timeout de lock de linha: outra transação segura a proposta e vai
            // resolvê-la — para quem chegou depois, ela já não está pendente.
            false
        }
    }

    /**
     * O `catch` é a mesma rede de segurança do `ClubPersistenceAdapter.save`:
     * o compare-and-set já devolve `Conflict` no caso comum, mas o banco ainda
     * pode recusar a escrita concorrente por outra via (timeout de lock de
     * linha enquanto a outra transferência não comita). A transação inteira já
     * foi desfeita quando a exception chega aqui — vira valor, como o resto.
     */
    override suspend fun completeTransfer(
        accepted: TransferOffer,
        seller: Club,
        buyer: Club,
        rivals: List<TransferOffer>,
    ): TransferCompletion = withContext(Dispatchers.IO) {
        try {
            tx.completeTransfer(accepted, seller, buyer, rivals)
        } catch (_: ConcurrencyFailureException) {
            TransferCompletion.Conflict(seller.id)
        }
    }
}

/**
 * Transações síncronas das propostas (issue #63). Bean separado para que
 * `@Transactional` passe pelo proxy do Spring — self-invocation não passaria.
 */
@Component
class TransferOfferTransaction(
    private val offerRepo: TransferOfferJpaRepository,
    private val clubSaveTx: ClubSaveTransaction,
) {

    @Transactional
    fun saveIfPending(offer: TransferOffer): Boolean = offerRepo.saveIfPending(offer) == 1

    /**
     * O aceite inteiro numa transação só: os dois clubes, a proposta e as
     * rivais caem ou ficam JUNTOS. É isto que garante "sem jogador duplicado,
     * sem caixa negativo" quando dois aceites pelo mesmo jogador correm juntos.
     *
     * **A ordem dos locks é regra, não estilo.** Os clubes são travados
     * primeiro (pelo compare-and-set de versão do [ClubSaveTransaction]) e
     * sempre em ordem crescente de id; só depois as propostas. Assim:
     * - dois aceites pelo mesmo jogador disputam a linha do MESMO vendedor
     *   antes de qualquer outra — o segundo espera, encontra a versão nova e
     *   perde com `Conflict` sem ter travado nada que o primeiro precise;
     * - A vendendo para B enquanto B vende para A travam os dois clubes na
     *   mesma ordem, em vez de cada um segurar o seu e esperar o do outro
     *   (deadlock).
     *
     * O `ClubSaveTransaction.save` participa DESTA transação (propagação
     * `REQUIRED`), então o seu "gravou" só vale no commit daqui. Nos desfechos
     * de falha, `setRollbackOnly` desfaz o que já foi gravado e o método
     * devolve o valor — sem exception para fluxo de controle.
     */
    @Transactional
    fun completeTransfer(
        accepted: TransferOffer,
        seller: Club,
        buyer: Club,
        rivals: List<TransferOffer>,
    ): TransferCompletion {
        for (club in listOf(seller, buyer).sortedBy { it.id.value }) {
            when (val saved = clubSaveTx.save(club)) {
                is ClubSaveResult.Saved -> Unit
                is ClubSaveResult.Conflict -> return rollback(TransferCompletion.Conflict(saved.clubId))
            }
        }
        if (offerRepo.saveIfPending(accepted) == 0) return rollback(TransferCompletion.OfferNoLongerPending)

        // Rival que já não estava pendente (recusada, retirada) fica como está:
        // a única que precisava mudar de estado com garantia era a aceita.
        rivals.forEach { offerRepo.saveIfPending(it) }
        return TransferCompletion.Completed
    }

    /** Marca a transação corrente para rollback e devolve [outcome] — o commit vira rollback. */
    private fun rollback(outcome: TransferCompletion): TransferCompletion {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
        return outcome
    }
}

/** UPDATE condicional da proposta com o estado de [offer]. */
private fun TransferOfferJpaRepository.saveIfPending(offer: TransferOffer): Int =
    saveIfPending(
        id = offer.id.value,
        status = offer.status.column(),
        expiryReason = offer.status.expiryReasonColumn(),
        roundsLeft = offer.roundsLeft,
    )
