package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.mapper.encodeDismissal
import com.carlesso.goalfather.adapter.out.persistence.mapper.toDomain
import com.carlesso.goalfather.adapter.out.persistence.repository.ClubJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.UserJpaRepository
import com.carlesso.goalfather.application.port.out.ClubClaimRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Dismissal
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.ClaimResult
import com.carlesso.goalfather.domain.rules.INITIAL_BOARD_CONFIDENCE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Adapter do port [ClubClaimRepository]. A coroutine só faz a ponte para o
 * pool de I/O; a transação de fato roda na [ClubClaimTransaction] (método
 * **não-suspend**), porque `@Transactional` sobre função `suspend` é frágil
 * — o `withContext` troca de thread e a transação é thread-bound. Manter a
 * unidade transacional num método síncrono garante que o lock otimista
 * dispare de verdade no commit.
 *
 * A falha de lock ([OptimisticLockingFailureException], lançada no commit ao
 * sair do método transacional) é capturada aqui e vira
 * [ClaimResult.AlreadyClaimed] — o 409 da corrida (issue #19).
 */
@Repository
class ClubClaimPersistenceAdapter(
    private val tx: ClubClaimTransaction,
) : ClubClaimRepository {

    override suspend fun claim(clubId: ClubId, userId: UserId): ClaimResult =
        withContext(Dispatchers.IO) {
            try {
                tx.claim(clubId.value, userId.value)
            } catch (_: OptimisticLockingFailureException) {
                ClaimResult.AlreadyClaimed(clubId)
            }
        }

    override suspend fun release(clubId: ClubId, dismissal: Dismissal): User? =
        withContext(Dispatchers.IO) { tx.release(clubId.value, encodeDismissal(dismissal)) }
}

@Component
class ClubClaimTransaction(
    private val clubRepo: ClubJpaRepository,
    private val userRepo: UserJpaRepository,
) {
    @Transactional
    fun claim(clubId: Long, userId: Long): ClaimResult {
        // Valida o usuário ANTES de mexer no clube (issue #29). Se carregássemos
        // o usuário só depois de gravar o ownerId, um id fantasma deixaria o
        // clube preso a um dono inexistente e ainda devolveria AlreadyClaimed —
        // semanticamente errado. Carregar primeiro mantém a transação intacta
        // (nada foi alterado) e o resultado distinto faz o 401 certo lá em cima.
        val user = userRepo.findById(userId).orElse(null)
            ?: return ClaimResult.UserNotFound(UserId(userId))

        val club = clubRepo.findById(clubId).orElse(null)
            ?: return ClaimResult.ClubNotFound(ClubId(clubId))

        // Caminho comum (sem corrida): já tem dono → 409 sem mexer no banco.
        if (club.ownerId != null) return ClaimResult.AlreadyClaimed(ClubId(clubId))

        club.ownerId = userId
        // Técnico novo não herda a confiança da IA (issue #62): a meta é do
        // clube e fica; a confiança volta ao valor inicial. `?.let` mantém a
        // coluna nula num save anterior à issue (meta ainda não fixada).
        club.boardConfidence = club.boardConfidence?.let { INITIAL_BOARD_CONFIDENCE }
        clubRepo.save(club) // @Version: a verificação ocorre no flush/commit

        user.clubId = clubId
        // Clube novo, carreira nova: a demissão anterior (issue #62) deixa de
        // ser a notícia — sem isso a tela de game over voltaria a aparecer.
        user.dismissalJson = null
        val saved = userRepo.save(user)

        // Computado antes do commit; se o commit falhar por lock otimista, o
        // adapter descarta este valor e devolve AlreadyClaimed.
        return ClaimResult.Success(saved.toDomain())
    }

    /**
     * Libera o clube do técnico demitido (issue #62). O clube sai por um UPDATE
     * condicional ao dono (ver `releaseOwner`), que não depende de versão lida
     * e por isso não disputa lock com ninguém. A demissão só é registrada se o
     * usuário ainda aponta para ESTE clube: se ele já está em outro, não há o
     * que demitir — gravar a `dismissal` o mandaria à tela de game over com um
     * clube nas mãos. Tudo na mesma transação: clube sem dono e usuário ainda
     * apontando para ele (ou o contrário) não ficam visíveis.
     */
    @Transactional
    fun release(clubId: Long, dismissalJson: String): User? {
        val ownerId = clubRepo.findById(clubId).orElse(null)?.ownerId ?: return null
        if (clubRepo.releaseOwner(clubId, ownerId) == 0) return null

        val user = userRepo.findById(ownerId).orElse(null)?.takeIf { it.clubId == clubId } ?: return null
        user.clubId = null
        user.dismissalJson = dismissalJson
        return userRepo.save(user).toDomain()
    }
}
