package com.carlesso.goalfather.adapter.out.persistence.repository

import com.carlesso.goalfather.adapter.out.persistence.entity.CompetitionCatalogEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.CountryEntity
import org.springframework.data.jpa.repository.JpaRepository

/** Países do save (issue #131). */
interface CountryJpaRepository : JpaRepository<CountryEntity, String>

/** Catálogo de competições do save (issue #131). */
interface CompetitionCatalogJpaRepository : JpaRepository<CompetitionCatalogEntity, String> {
    fun findAllByOrderBySortOrder(): List<CompetitionCatalogEntity>
}
