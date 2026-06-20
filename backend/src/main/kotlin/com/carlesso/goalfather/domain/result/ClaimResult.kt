package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId

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

    /**
     * O usuário autenticado não existe mais (issue #29). Inalcançável no fluxo
     * normal — o principal do JWT sempre mapeia um usuário real — mas modelado
     * explicitamente para não corromper estado: sem este caso, um id fantasma
     * marcaria o clube como reivindicado e devolveria `AlreadyClaimed`,
     * prendendo o clube a um dono inexistente.
     */
    data class UserNotFound(val userId: UserId) : ClaimResult
}
