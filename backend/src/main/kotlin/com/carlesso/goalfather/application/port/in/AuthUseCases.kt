package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.ClaimResult
import com.carlesso.goalfather.domain.result.LoginResult
import com.carlesso.goalfather.domain.result.RegisterResult

interface RegisterUserUseCase {
    suspend fun execute(username: String, rawPassword: String): RegisterResult
}

interface LoginUseCase {
    suspend fun execute(username: String, rawPassword: String): LoginResult
}

interface ClaimClubUseCase {
    suspend fun execute(clubId: ClubId, userId: UserId): ClaimResult
}
