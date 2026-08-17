package com.carlesso.goalfather.adapter.out.persistence.repository

import com.carlesso.goalfather.adapter.out.persistence.entity.ClubEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.MarketEntryEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PlayerEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PositionEnum
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundReadinessEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundReadinessId
import com.carlesso.goalfather.adapter.out.persistence.entity.SeasonHistoryEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.StandingsEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.StandingsId
import com.carlesso.goalfather.adapter.out.persistence.entity.UserEntity
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * Repositórios Spring Data — interfaces declarativas. Implementação
 * gerada em runtime pelo Spring Data JPA. Nenhum método suspend aqui
 * (Spring Data não suporta diretamente); a ponte para coroutines fica
 * nos PersistenceAdapter via `withContext(Dispatchers.IO)`.
 */
interface ClubJpaRepository : JpaRepository<ClubEntity, Long> {
    /** Clubes ainda sem dono — base do fluxo de seleção (issue #19). */
    fun findAllByOwnerIdIsNull(): List<ClubEntity>
}

interface UserJpaRepository : JpaRepository<UserEntity, Long> {
    fun findByUsername(username: String): UserEntity?
    fun existsByUsername(username: String): Boolean

    /**
     * Donos de clube = técnicos humanos da liga (issue #20). `OrderById`
     * garante ordem determinística — `pendingUsernames` no 409/UI fica estável
     * entre chamadas (sem `ORDER BY` a ordem das linhas é indefinida).
     */
    fun findAllByClubIdIsNotNullOrderById(): List<UserEntity>
}

interface PlayerJpaRepository : JpaRepository<PlayerEntity, Long> {
    fun findAllByClubId(clubId: Long): List<PlayerEntity>
    fun findAllByClubIdIsNullAndPosition(position: PositionEnum): List<PlayerEntity>
    fun findAllByClubIdIsNull(): List<PlayerEntity>

    /** Busca o elenco de vários clubes numa só query — evita o N+1 do `findAll`. */
    fun findAllByClubIdIn(clubIds: Collection<Long>): List<PlayerEntity>
}

/**
 * O cache (issue #13) é aplicado na camada SÍNCRONA do Spring Data, não nos
 * adapters suspend: o interceptor de cache do Spring não suporta funções
 * `suspend` (lida com o parâmetro `Continuation`). Anotar o método bloqueante
 * do repositório resolve sem gambiarra — o adapter só o envelopa em
 * `withContext(Dispatchers.IO)`. `save`/`deleteById` são sobrescritos só para
 * pendurar o `@CacheEvict`.
 */
interface MarketEntryJpaRepository : JpaRepository<MarketEntryEntity, Long> {
    @Cacheable("market")
    override fun findAll(): List<MarketEntryEntity>

    @CacheEvict("market", allEntries = true)
    override fun <S : MarketEntryEntity> save(entity: S): S

    @CacheEvict("market", allEntries = true)
    override fun deleteById(id: Long)

    /**
     * Delete de uma entidade JÁ carregada — usado no claim (issue #21). Ao
     * contrário de `deleteById` (que faz `findById` interno e vira no-op se a
     * linha sumiu), apagar a instância gerenciada faz o Hibernate emitir
     * `DELETE ... WHERE player_id = ? AND version = ?`; se a linha já foi
     * removida por outro comprador, 0 linhas afetadas → `OptimisticLock...`.
     * Mesmo `@CacheEvict` do `deleteById` para não deixar mercado obsoleto.
     */
    @CacheEvict("market", allEntries = true)
    override fun delete(entity: MarketEntryEntity)
}

interface RoundJpaRepository : JpaRepository<RoundEntity, Int> {
    fun findTopByOrderByNumberDesc(): RoundEntity?
}

interface RoundReadinessJpaRepository : JpaRepository<RoundReadinessEntity, RoundReadinessId> {
    fun findAllByIdRoundNumber(roundNumber: Int): List<RoundReadinessEntity>

    /**
     * Menor `ready_at` da rodada = instante do primeiro "pronto" (issue #45).
     * `MIN` no banco evita carregar todas as linhas só para achar o mínimo;
     * devolve `null` quando não há nenhuma marcação.
     */
    @Query(
        "select min(r.readyAt) from RoundReadinessEntity r where r.id.roundNumber = :roundNumber",
    )
    fun findFirstReadyAt(@Param("roundNumber") roundNumber: Int): Instant?
}

/**
 * Histórico de temporadas (issue #60). Sem `@Cacheable`: a leitura acontece
 * quando o técnico abre a tela de histórico, não a cada rodada, e o cache só
 * adicionaria um lugar para a temporada recém-gravada não aparecer.
 */
interface SeasonHistoryJpaRepository : JpaRepository<SeasonHistoryEntity, Int> {
    /** Da temporada mais recente para a mais antiga — ordem de exibição. */
    fun findAllByOrderBySeasonDesc(): List<SeasonHistoryEntity>
}

interface StandingsJpaRepository : JpaRepository<StandingsEntity, StandingsId> {
    /**
     * Tabelas da temporada ativa = maior `season`, uma por divisão (issue
     * #47). Sem parâmetros de propósito: o cache usa `SimpleKey.EMPTY` e a
     * subquery resolve a temporada ativa na mesma ida ao banco.
     */
    @Cacheable("standings")
    @Query(
        "select s from StandingsEntity s " +
            "where s.season = (select max(x.season) from StandingsEntity x) " +
            "order by s.division",
    )
    fun findAllForLatestSeason(): List<StandingsEntity>

    /** Tabelas de uma temporada específica (histórico), ordenadas por divisão. */
    fun findAllBySeasonOrderByDivision(season: Int): List<StandingsEntity>

    @CacheEvict("standings", allEntries = true)
    override fun <S : StandingsEntity> save(entity: S): S
}
