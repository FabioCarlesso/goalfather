package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.ClaimResult

/**
 * Port para a operação transacional de reivindicar um clube (issue #19).
 *
 * Cruza dois agregados (clube + usuário) numa única transação com lock
 * otimista — por isso fica num port dedicado, fora do [ClubRepository] que
 * lida só com clubes. A implementação resolve a corrida (dois usuários no
 * mesmo clube) devolvendo [ClaimResult.AlreadyClaimed] para o perdedor.
 */
interface ClubClaimRepository {
    suspend fun claim(clubId: ClubId, userId: UserId): ClaimResult
}
