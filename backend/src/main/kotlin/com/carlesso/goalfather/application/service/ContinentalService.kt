package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.ContinentalEditionView
import com.carlesso.goalfather.application.port.`in`.ContinentalUseCase
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.application.port.out.ContinentalRepository
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.TableZone
import com.carlesso.goalfather.domain.rules.groupTable
import com.carlesso.goalfather.domain.rules.tiebreakersOf

/**
 * Implementação de [ContinentalUseCase] (issue #132): lê a edição e monta a
 * tabela de cada grupo com os desempates do catálogo. Os [qualifiersPerGroup]
 * primeiros ganham a faixa `QUALIFICATION` — a mesma que pinta o G-4 da liga.
 */
class ContinentalService(
    private val continentalRepo: ContinentalRepository,
    private val catalogRepo: CompetitionCatalogRepository,
) : ContinentalUseCase {

    override suspend fun edition(competition: CompetitionId, season: Int?): ContinentalEditionView? {
        val edition = (if (season == null) continentalRepo.findLatest(competition) else continentalRepo.find(competition, season))
            ?: return null
        val tiebreakers = catalogRepo.findAll().tiebreakersOf(competition)
        val tables = edition.groups.associate { group ->
            group.name to edition.groupTable(group, tiebreakers).map { row ->
                if (row.position <= edition.qualifiersPerGroup) row.copy(zone = TableZone.QUALIFICATION) else row
            }
        }
        return ContinentalEditionView(edition, tables)
    }
}
