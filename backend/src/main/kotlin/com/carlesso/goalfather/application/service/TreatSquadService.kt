package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.TreatSquadUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.result.MedicalResult
import com.carlesso.goalfather.domain.rules.MEDICAL_DEPARTMENT_COST_CENTS
import com.carlesso.goalfather.domain.rules.applyMedicalTreatment

/**
 * Implementação de `TreatSquadUseCase` (issue #54). O serviço só orquestra —
 * a matemática de recuperação vive em `domain/rules/FitnessRules`, mesmo
 * padrão de `PlayRoundService` com estatísticas e finanças.
 */
class TreatSquadService(
    private val clubRepo: ClubRepository,
) : TreatSquadUseCase {

    override suspend fun execute(clubId: ClubId, requesterId: Long): MedicalResult {
        val club = clubRepo.findById(clubId)
            ?: return MedicalResult.ClubNotFound(clubId)

        if (club.ownerId != requesterId) {
            return MedicalResult.NotOwner(clubId)
        }

        if (club.cash < MEDICAL_DEPARTMENT_COST_CENTS) {
            return MedicalResult.InsufficientFunds(
                available = club.cash,
                required = MEDICAL_DEPARTMENT_COST_CENTS,
            )
        }

        // Só o `squad` é tocado: `Club.startingLineup()` reidrata os titulares
        // pelo id, então a escalação salva já enxerga a stamina nova.
        val treated = club.copy(
            cash = club.cash - MEDICAL_DEPARTMENT_COST_CENTS,
            squad = applyMedicalTreatment(club.squad),
        )

        return MedicalResult.Success(clubRepo.save(treated))
    }
}
