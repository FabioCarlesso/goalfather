package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.SetTrainingFocusUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.TrainingFocus
import com.carlesso.goalfather.domain.result.TrainingFocusResult

/**
 * Implementação de `SetTrainingFocusUseCase` (issue #58). Só orquestra: a
 * matemática do treino vive em `domain/rules/TrainingRules` e roda na virada
 * da rodada (`PlayRoundService`), não aqui — mesma separação do departamento
 * médico com `FitnessRules`.
 *
 * Repetir o foco que já está gravado não persiste nada: o `save` evita uma
 * escrita (e um bump de `@Version`) que não muda nada.
 */
class SetTrainingFocusService(
    private val clubRepo: ClubRepository,
) : SetTrainingFocusUseCase {

    override suspend fun execute(
        clubId: ClubId,
        requesterId: Long,
        focus: TrainingFocus,
    ): TrainingFocusResult {
        val club = clubRepo.findById(clubId)
            ?: return TrainingFocusResult.ClubNotFound(clubId)

        if (club.ownerId != requesterId) {
            return TrainingFocusResult.NotOwner(clubId)
        }

        if (club.trainingFocus == focus) {
            return TrainingFocusResult.Success(club)
        }

        return TrainingFocusResult.Success(clubRepo.save(club.copy(trainingFocus = focus)))
    }
}
