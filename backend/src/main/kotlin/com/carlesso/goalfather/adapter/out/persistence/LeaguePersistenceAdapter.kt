package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.entity.RoundStatusEnum
import com.carlesso.goalfather.adapter.out.persistence.mapper.toDomain
import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.mapper.updateFrom
import com.carlesso.goalfather.adapter.out.persistence.repository.RoundJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.StandingsJpaRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Standings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class LeaguePersistenceAdapter(
    private val roundRepo: RoundJpaRepository,
    private val standingsRepo: StandingsJpaRepository,
    private val json: Json,
    private val transition: RoundTransition,
) : LeagueRepository {

    override suspend fun findRound(number: Int): Round? = withContext(Dispatchers.IO) {
        roundRepo.findById(number).orElse(null)?.toDomain(json)
    }

    override suspend fun findLatest(): Round? = withContext(Dispatchers.IO) {
        roundRepo.findTopByOrderByNumberDesc()?.toDomain(json)
    }

    override suspend fun currentStandings(): Standings = withContext(Dispatchers.IO) {
        // Temporada ativa = maior `season`. Ao virar o ano, a tabela anterior
        // permanece no DB (PK por season) e esta passa a apontar para a nova.
        val entity = standingsRepo.findTopByOrderBySeasonDesc()
            ?: throw IllegalStateException("Standings nao inicializada — seed faltando")
        entity.toDomain(json)
    }

    override suspend fun findStandings(season: Int): Standings? = withContext(Dispatchers.IO) {
        standingsRepo.findById(season).orElse(null)?.toDomain(json)
    }

    @Transactional
    override suspend fun saveRound(round: Round) {
        withContext(Dispatchers.IO) {
            // Atualiza a linha existente em vez de mapear para uma entidade nova:
            // preserva o `version` do lock otimista (issue #46). A PK é o número
            // da rodada, então a virada de temporada reescreve a rodada 1 — sem
            // este cuidado o save chegaria com version=0 contra uma linha já em
            // version≥1 e estouraria stale state (mesma armadilha do clube, #19).
            val entity = roundRepo.findById(round.number).orElse(null)
            roundRepo.save(entity?.updateFrom(round, json) ?: round.toEntity(json))
        }
    }

    @Transactional
    override suspend fun saveStandings(standings: Standings) {
        withContext(Dispatchers.IO) {
            standingsRepo.save(standings.toEntity(json))
        }
    }

    override suspend fun startRound(roundNumber: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            transition.start(roundNumber)
        } catch (_: OptimisticLockingFailureException) {
            false
        }
    }

    override suspend fun finishRound(round: Round): Boolean = withContext(Dispatchers.IO) {
        try {
            transition.finish(round)
        } catch (_: OptimisticLockingFailureException) {
            false
        }
    }
}

/**
 * Transições de status da rodada sob lock otimista (issue #46). Bean separado e
 * **não-suspend** de propósito: `@Transactional` sobre função `suspend` é frágil
 * (o `withContext` troca de thread e a transação é thread-bound) e
 * self-invocation não passaria pelo proxy do Spring. Com a unidade transacional
 * síncrona, o `@Version` dispara de verdade no commit — mesmo padrão do claim de
 * clube (#19) e do mercado (#21).
 *
 * Dois mecanismos, dois papéis:
 * - o **guard por status** resolve o caso comum, sem corrida (rodada já
 *   finalizada → `false` sem escrever nada);
 * - o **`@Version`** resolve a corrida real, quando duas instâncias leem o mesmo
 *   estado e ambas tentam gravar: o segundo commit não acha mais a versão que
 *   leu, e a `OptimisticLockingFailureException` sobe para o adapter, que a
 *   traduz em `false`.
 */
@Component
class RoundTransition(
    private val roundRepo: RoundJpaRepository,
    private val json: Json,
) {
    @Transactional
    fun start(number: Int): Boolean {
        val entity = roundRepo.findById(number).orElse(null) ?: return false
        if (entity.status != RoundStatusEnum.Scheduled) return false
        entity.status = RoundStatusEnum.InProgress
        roundRepo.save(entity) // @Version: a verificação ocorre no flush/commit
        return true
    }

    @Transactional
    fun finish(round: Round): Boolean {
        val entity = roundRepo.findById(round.number).orElse(null) ?: return false
        if (entity.status == RoundStatusEnum.Finished) return false
        roundRepo.save(entity.updateFrom(round, json)) // grava placares + Finished
        return true
    }
}
