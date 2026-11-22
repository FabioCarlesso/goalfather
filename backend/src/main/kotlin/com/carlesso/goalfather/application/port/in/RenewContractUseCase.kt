package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.ContractOffer
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.RenewalResult

/**
 * Oferecer renovação a um jogador do próprio elenco (issue #138). Quem decide
 * é o jogador (`playerResponse`); a posse do clube é checada no controller,
 * como no mercado.
 */
interface RenewContractUseCase {
    suspend fun renew(clubId: ClubId, playerId: PlayerId, offer: ContractOffer): RenewalResult
}
