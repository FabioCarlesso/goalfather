package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.entity.RoundStatusEnum
import com.carlesso.goalfather.adapter.out.persistence.mapper.toDomain
import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.mapper.toKey
import com.carlesso.goalfather.adapter.out.persistence.mapper.updateFrom
import com.carlesso.goalfather.adapter.out.persistence.repository.RoundJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.StandingsJpaRepository
import com.carlesso.goalfather.application.metrics.GoalfatherMetrics
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Standings
import io.micrometer.core.instrument.MeterRegistry
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
    private val meterRegistry: MeterRegistry,
) : LeagueRepository {

    override suspend fun findRound(matchday: Matchday): Round? = withContext(Dispatchers.IO) {
        roundRepo.findById(matchday.toKey()).orElse(null)?.toDomain(json)
    }

    override suspend fun findLatest(): Round? = withContext(Dispatchers.IO) {
        roundRepo.findTopByOrderByIdIsoYearDescIdIsoWeekDescIdSlotDesc()?.toDomain(json)
    }

    override suspend fun currentStandings(): List<Standings> = withContext(Dispatchers.IO) {
        // Temporada ativa = maior `season`. Ao virar o ano, as tabelas
        // anteriores permanecem no DB (PK por season+division) e estas passam
        // a apontar para a nova. Uma tabela por divisão (issue #47).
        val entities = standingsRepo.findAllForLatestSeason()
        check(entities.isNotEmpty()) { "Standings nao inicializada — seed faltando" }
        entities.map { it.toDomain(json) }
    }

    override suspend fun findStandings(season: Int): List<Standings> = withContext(Dispatchers.IO) {
        standingsRepo.findAllBySeasonOrderByDivision(season).map { it.toDomain(json) }
    }

    override suspend fun saveRound(round: Round) {
        withContext(Dispatchers.IO) {
            transition.save(round)
        }
    }

    @Transactional
    override suspend fun saveStandings(standings: Standings) {
        withContext(Dispatchers.IO) {
            standingsRepo.save(standings.toEntity(json))
        }
    }

    override suspend fun startRound(matchday: Matchday): Boolean = withContext(Dispatchers.IO) {
        claim("start") { transition.start(matchday) }
    }

    override suspend fun finishRound(round: Round): Boolean = withContext(Dispatchers.IO) {
        claim("finish") { transition.finish(round) }
    }

    /**
     * Executa uma transição e traduz a perda da corrida em `false`. A
     * `OptimisticLockingFailureException` (lançada no commit quando outra
     * instância avançou a versão) é o caso multi-instância que a issue #46
     * fecha — contamos cada ocorrência para dar visibilidade ao lock em ação.
     */
    private inline fun claim(phase: String, block: () -> Boolean): Boolean =
        try {
            block()
        } catch (_: OptimisticLockingFailureException) {
            meterRegistry.counter(GoalfatherMetrics.ROUND_CLAIM_CONFLICTS, "phase", phase).increment()
            false
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
 * - o **guard por status** resolve o caso comum, sem corrida (encaixe já
 *   finalizado → `false` sem escrever);
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
    fun save(round: Round) {
        // Atualiza a linha existente em vez de mapear para uma entidade nova:
        // preserva o `version` do lock otimista (issue #46) — sem este cuidado o
        // save chegaria com version=0 contra uma linha já em version≥1 e
        // estouraria stale state (mesma armadilha do clube, #19). Read e write
        // ficam na MESMA transação (ao contrário do antigo `@Transactional
        // suspend`, que trocava de thread e não segurava nada).
        val entity = roundRepo.findById(round.matchday.toKey()).orElse(null)
        roundRepo.save(entity?.updateFrom(round, json) ?: round.toEntity(json))
    }

    @Transactional
    fun start(matchday: Matchday): Boolean {
        val entity = roundRepo.findById(matchday.toKey()).orElse(null) ?: return false
        if (entity.status != RoundStatusEnum.Scheduled) return false
        entity.status = RoundStatusEnum.InProgress
        roundRepo.save(entity) // @Version: a verificação ocorre no flush/commit
        return true
    }

    @Transactional
    fun finish(round: Round): Boolean {
        val entity = roundRepo.findById(round.matchday.toKey()).orElse(null) ?: return false
        // Desde a issue #125 a PK é o ENCAIXE (ano + semana + slot), que nunca
        // se repete: a linha de uma temporada não é mais reescrita pela seguinte,
        // e o antigo guard por `season` (que existia porque a PK era só o número
        // da rodada) deixou de ser necessário. Basta o status.
        // Só um encaixe LIBERADO termina. Desde a #151 o stream só roda depois
        // de `start` (`Scheduled → InProgress`), então `Scheduled` aqui é um
        // stream órfão: a coroutine da rodada sobrevive ao cliente (ver
        // `RoundWebSocketHandler`) e, se o estado foi recriado no meio (reset
        // do profile e2e), o mesmo encaixe reaparece `Scheduled` — sem este
        // guard o órfão aplicaria lesões, cartões e suspensões à liga nova.
        if (entity.status != RoundStatusEnum.InProgress) return false
        roundRepo.save(entity.updateFrom(round, json)) // grava placares + Finished
        return true
    }
}
