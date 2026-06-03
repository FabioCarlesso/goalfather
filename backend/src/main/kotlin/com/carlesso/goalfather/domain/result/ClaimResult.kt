package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.User

/**
 * Resultado de reivindicar um clube (issue #19).
 *
 * `AlreadyClaimed` cobre tanto o caso "outro usuário já é dono" quanto a
 * corrida vencida pelo concorrente (falha de lock otimista) — para o cliente
 * ambos significam a mesma coisa: 409, escolha outro clube.
 */
sealed interface ClaimResult {
    /** Sucesso — devolve o usuário já com o `clubId` gravado. */
    data class Success(val user: User) : ClaimResult
    data class ClubNotFound(val clubId: ClubId) : ClaimResult
    data class AlreadyClaimed(val clubId: ClubId) : ClaimResult
}
