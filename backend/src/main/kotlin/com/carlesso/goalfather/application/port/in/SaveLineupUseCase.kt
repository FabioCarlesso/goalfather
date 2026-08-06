package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Tactics
import com.carlesso.goalfather.domain.result.LineupResult

interface SaveLineupUseCase {
    /**
     * `tactics` tem default (issue #56): salvar sem informar postura mantém
     * a escalação `EQUILIBRADA`, que é o comportamento de antes da tática
     * existir — clientes velhos continuam válidos.
     */
    suspend fun execute(
        clubId: ClubId,
        requesterId: Long,
        formation: Formation,
        playerIds: List<PlayerId>,
        tactics: Tactics = Tactics.DEFAULT,
    ): LineupResult
}
