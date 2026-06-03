package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.mapper.toDomain
import com.carlesso.goalfather.adapter.out.persistence.repository.ClubJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.UserJpaRepository
import com.carlesso.goalfather.application.port.out.ClubClaimRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.ClaimResult
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
}

@Component
class ClubClaimTransaction(
    private val clubRepo: ClubJpaRepository,
    private val userRepo: UserJpaRepository,
) {
    @Transactional
    fun claim(clubId: Long, userId: Long): ClaimResult {
        val club = clubRepo.findById(clubId).orElse(null)
            ?: return ClaimResult.ClubNotFound(ClubId(clubId))

        // Caminho comum (sem corrida): já tem dono → 409 sem mexer no banco.
        if (club.ownerId != null) return ClaimResult.AlreadyClaimed(ClubId(clubId))

        club.ownerId = userId
        clubRepo.save(club) // @Version: a verificação ocorre no flush/commit

        val user = userRepo.findById(userId).orElse(null)
            ?: return ClaimResult.AlreadyClaimed(ClubId(clubId))
        user.clubId = clubId
        val saved = userRepo.save(user)

        // Computado antes do commit; se o commit falhar por lock otimista, o
        // adapter descarta este valor e devolve AlreadyClaimed.
        return ClaimResult.Success(saved.toDomain())
    }
}
