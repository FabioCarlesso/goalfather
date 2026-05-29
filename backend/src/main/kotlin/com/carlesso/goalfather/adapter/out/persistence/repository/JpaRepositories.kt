package com.carlesso.goalfather.adapter.out.persistence.repository

import com.carlesso.goalfather.adapter.out.persistence.entity.ClubEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.MarketEntryEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PlayerEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PositionEnum
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.StandingsEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Repositórios Spring Data — interfaces declarativas. Implementação
 * gerada em runtime pelo Spring Data JPA. Nenhum método suspend aqui
 * (Spring Data não suporta diretamente); a ponte para coroutines fica
 * nos PersistenceAdapter via `withContext(Dispatchers.IO)`.
 */
interface ClubJpaRepository : JpaRepository<ClubEntity, Long>

interface PlayerJpaRepository : JpaRepository<PlayerEntity, Long> {
    fun findAllByClubId(clubId: Long): List<PlayerEntity>
    fun findAllByClubIdIsNullAndPosition(position: PositionEnum): List<PlayerEntity>
    fun findAllByClubIdIsNull(): List<PlayerEntity>
}

interface MarketEntryJpaRepository : JpaRepository<MarketEntryEntity, Long>

interface RoundJpaRepository : JpaRepository<RoundEntity, Int>

interface StandingsJpaRepository : JpaRepository<StandingsEntity, Int>
