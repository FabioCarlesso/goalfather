package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Dismissal
import com.carlesso.goalfather.domain.model.User
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
    /** Reivindicar: o clube ganha dono e o usuário ganha clube — e perde a demissão anterior. */
    suspend fun claim(clubId: ClubId, userId: UserId): ClaimResult

    /**
     * Direção inversa do [claim] (issue #62): a diretoria demitiu o técnico.
     * Na MESMA transação o clube volta ao pool da IA (`ownerId = null`) e o
     * usuário fica sem clube, com a [dismissal] registrada para a tela de game
     * over. Devolve o usuário liberado, ou `null` se o clube não tinha dono
     * humano — demitir a IA não tem efeito.
     */
    suspend fun release(clubId: ClubId, dismissal: Dismissal): User?
}
