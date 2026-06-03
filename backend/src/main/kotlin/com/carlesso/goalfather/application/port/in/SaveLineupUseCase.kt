package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.LineupResult

interface SaveLineupUseCase {
    suspend fun execute(
        clubId: ClubId,
        requesterId: Long,
        formation: Formation,
        playerIds: List<PlayerId>,
    ): LineupResult
}
