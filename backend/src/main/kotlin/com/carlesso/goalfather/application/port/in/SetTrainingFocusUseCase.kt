package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.TrainingFocus
import com.carlesso.goalfather.domain.result.TrainingFocusResult

/**
 * Escolha do foco de treino da semana (issue #58). O efeito só acontece na
 * virada da rodada — aqui o técnico apenas registra a decisão.
 */
interface SetTrainingFocusUseCase {
    suspend fun execute(clubId: ClubId, requesterId: Long, focus: TrainingFocus): TrainingFocusResult
}
