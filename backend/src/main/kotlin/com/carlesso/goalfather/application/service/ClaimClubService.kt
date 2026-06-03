package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.ClaimClubUseCase
import com.carlesso.goalfather.application.port.out.ClubClaimRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.ClaimResult

/**
 * Reivindicar clube (issue #19). A transação + lock otimista vivem no
 * adapter (detalhe de persistência); este serviço é o ponto de entrada do
 * caso de uso e mantém o controller falando só com `port.in`.
 */
class ClaimClubService(
    private val claimRepo: ClubClaimRepository,
) : ClaimClubUseCase {

    override suspend fun execute(clubId: ClubId, userId: UserId): ClaimResult =
        claimRepo.claim(clubId, userId)
}
