package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.result.MedicalResult

/**
 * Departamento médico (issue #54): paga a taxa fixa e acelera a recuperação
 * do elenco — paridade com o `recoverPlayers()` do protótipo.
 */
interface TreatSquadUseCase {
    suspend fun execute(clubId: ClubId, requesterId: Long): MedicalResult
}
