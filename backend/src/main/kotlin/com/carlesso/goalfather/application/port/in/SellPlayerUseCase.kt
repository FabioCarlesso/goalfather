package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.TransferResult

interface SellPlayerUseCase {
    suspend fun execute(clubId: ClubId, playerId: PlayerId): TransferResult
}
