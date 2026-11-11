package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.entity.RoundReadinessEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundReadinessId
import com.carlesso.goalfather.adapter.out.persistence.repository.RoundReadinessJpaRepository
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.UserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Implementação JPA do port [RoundReadinessRepository] (issue #20). Mesma
 * ponte coroutine → Spring Data dos demais adapters: `withContext(IO)`.
 *
 * `reset` faz select-then-delete (`deleteAll(Iterable)`) em vez de um delete
 * derivado: os métodos do `CrudRepository` já são transacionais por si, então
 * evitamos `@Transactional` sobre função `suspend` — frágil porque o
 * `withContext` troca de thread (mesma razão documentada no claim de clube).
 */
@Repository
class RoundReadinessPersistenceAdapter(
    private val repo: RoundReadinessJpaRepository,
) : RoundReadinessRepository {

    override suspend fun markReady(matchday: Matchday, userId: UserId) {
        withContext(Dispatchers.IO) {
            // save com PK existente apenas atualiza ready_at — idempotente.
            repo.save(RoundReadinessEntity(matchday.readinessId(userId)))
        }
    }

    override suspend fun readyUserIds(matchday: Matchday): Set<UserId> = withContext(Dispatchers.IO) {
        rows(matchday).map { UserId(it.id.userId) }.toSet()
    }

    override suspend fun firstReadyAt(matchday: Matchday): Instant? = withContext(Dispatchers.IO) {
        repo.findFirstReadyAt(matchday.week.year, matchday.week.week, matchday.slot.name)
    }

    override suspend fun reset(matchday: Matchday) {
        withContext(Dispatchers.IO) {
            val rows = rows(matchday)
            if (rows.isNotEmpty()) repo.deleteAll(rows)
        }
    }

    private fun rows(matchday: Matchday): List<RoundReadinessEntity> =
        repo.findAllByIdIsoYearAndIdIsoWeekAndIdSlot(matchday.week.year, matchday.week.week, matchday.slot.name)

    private fun Matchday.readinessId(userId: UserId) =
        RoundReadinessId(isoYear = week.year, isoWeek = week.week, slot = slot.name, userId = userId.value)
}
