package com.carlesso.goalfather.adapter.out.persistence.repository

import com.carlesso.goalfather.adapter.out.persistence.entity.ClubEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.MarketEntryEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PlayerEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PositionEnum
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.StandingsEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.UserEntity
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.jpa.repository.JpaRepository

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
}

interface PlayerJpaRepository : JpaRepository<PlayerEntity, Long> {
    fun findAllByClubId(clubId: Long): List<PlayerEntity>
    fun findAllByClubIdIsNullAndPosition(position: PositionEnum): List<PlayerEntity>
    fun findAllByClubIdIsNull(): List<PlayerEntity>
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
}

interface RoundJpaRepository : JpaRepository<RoundEntity, Int> {
    fun findTopByOrderByNumberDesc(): RoundEntity?
}

interface StandingsJpaRepository : JpaRepository<StandingsEntity, Int> {
    /** Tabela da temporada ativa = maior `season` (suporta histórico multi-temporada). */
    @Cacheable("standings")
    fun findTopByOrderBySeasonDesc(): StandingsEntity?

    @CacheEvict("standings", allEntries = true)
    override fun <S : StandingsEntity> save(entity: S): S
}
