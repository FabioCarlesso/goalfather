package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.application.seed.interimBrazilCatalog
import com.carlesso.goalfather.domain.model.GameCompetition
import org.springframework.stereotype.Repository

/**
 * Catálogo de competições EM MEMÓRIA, só para a frente B da issue #131 subir
 * a aplicação e os testes de integração sem o adapter JPA do catálogo.
 *
 * #131 integrador: APAGAR este arquivo — a frente A entrega o adapter JPA
 * (migration V19) que implementa [CompetitionCatalogRepository]. Com os dois,
 * o Spring acha dois beans do port e não sobe.
 */
@Repository
class InterimCompetitionCatalogAdapter : CompetitionCatalogRepository {
    // `@Volatile`: leitura e escrita vêm de threads diferentes (requests e
    // reset do E2E); a lista em si é imutável e trocada inteira.
    @Volatile
    private var catalog: List<GameCompetition> = interimBrazilCatalog()

    override suspend fun findAll(): List<GameCompetition> = catalog

    override suspend fun replaceAll(competitions: List<GameCompetition>) {
        catalog = competitions
    }
}
