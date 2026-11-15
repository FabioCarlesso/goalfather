package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.mapper.SavedCountries
import com.carlesso.goalfather.adapter.out.persistence.mapper.toDomain
import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.CompetitionCatalogJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.CountryJpaRepository
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.GameCompetition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Adapter JPA do catálogo de competições do save (issue #131). A escrita mora
 * em [CompetitionCatalogTransaction] (bloqueante, `@Transactional`) para o
 * `SeedWriter` poder chamá-la DENTRO da transação do seed — o mundo novo é
 * gravado inteiro ou nada.
 */
@Repository
class CompetitionCatalogPersistenceAdapter(
    private val repo: CompetitionCatalogJpaRepository,
    private val tx: CompetitionCatalogTransaction,
) : CompetitionCatalogRepository {

    override suspend fun findAll(): List<GameCompetition> = withContext(Dispatchers.IO) {
        repo.findAllByOrderBySortOrder().map { it.toDomain() }
    }

    override suspend fun replaceAll(competitions: List<GameCompetition>) = withContext(Dispatchers.IO) {
        tx.replaceAll(competitions)
    }
}

@Component
class CompetitionCatalogTransaction(private val repo: CompetitionCatalogJpaRepository) {

    /** `REQUIRED` (o default): dentro do seed, junta-se à transação dele. */
    @Transactional
    fun replaceAll(competitions: List<GameCompetition>) {
        repo.deleteAllInBatch()
        // `mapIndexed` numera na ordem recebida — é a ordem que `findAll` devolve.
        repo.saveAll(competitions.mapIndexed { order, competition -> competition.toEntity(order) })
    }
}

/**
 * Os países do save (issue #131): grava a tabela `countries` e mantém o
 * [SavedCountries] (o catálogo em memória dos mappers) igual a ela.
 *
 * Não é um port: nenhum caso de uso lista países — eles só existem para a
 * persistência resolver o trigrama das colunas `country`/`nationality`. Quem
 * chama é a configuração (boot e reset do E2E).
 */
@Component
class SavedCountryCatalog(private val repo: CountryJpaRepository) {

    /** Dentro do seed, junta-se à transação dele; [reload] depois do commit. */
    @Transactional
    fun replaceAll(countries: List<Country>) {
        repo.deleteAllInBatch()
        repo.saveAll(countries.map { it.toEntity() })
    }

    /** Recarrega o catálogo em memória a partir do banco (boot, depois do seed). */
    fun reload() {
        SavedCountries.replace(repo.findAll().map { it.toDomain() })
    }
}
