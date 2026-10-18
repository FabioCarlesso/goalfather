package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.result.ExpandStadiumResult

/**
 * Ampliação do estádio (issue #79): debita o custo da obra do caixa e soma os
 * assentos à capacidade.
 */
interface ExpandStadiumUseCase {
    suspend fun execute(clubId: ClubId, requesterId: Long, additionalSeats: Int): ExpandStadiumResult
}
