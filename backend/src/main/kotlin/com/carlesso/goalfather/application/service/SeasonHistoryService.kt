package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.SeasonHistoryUseCase
import com.carlesso.goalfather.application.port.out.SeasonHistoryRepository
import com.carlesso.goalfather.domain.model.ClubCareer
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.SeasonRecord
import com.carlesso.goalfather.domain.rules.careerOf

/**
 * Casos de uso de leitura do histórico (issue #60). Fino de propósito: o
 * trabalho de verdade — contar títulos, eleger a melhor campanha — é regra
 * pura em `careerOf`, e o service só busca os records e a aplica.
 *
 * A carreira é derivada da lista inteira, não de uma coluna agregada: com
 * poucas temporadas por liga é leitura barata, e evita manter um contador
 * que pode divergir do histórico que ele resume.
 */
class SeasonHistoryService(
    private val historyRepo: SeasonHistoryRepository,
) : SeasonHistoryUseCase {

    override suspend fun history(): List<SeasonRecord> = historyRepo.findAll()

    override suspend fun season(season: Int): SeasonRecord? = historyRepo.findBySeason(season)

    override suspend fun career(clubId: ClubId): ClubCareer? = careerOf(clubId, historyRepo.findAll())
}
