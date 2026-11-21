package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.rules.PlayerResponse
import java.time.LocalDate

/**
 * Desfecho de uma proposta de renovação a um jogador do próprio elenco (issue
 * #138). Erros de negócio como VALOR; posse e existência do clube viram status
 * HTTP no controller, como no mercado.
 */
sealed interface RenewalResult {

    /** Contrato novo assinado: substitui o antigo e a duração recomeça hoje. */
    data class Renewed(val club: Club, val player: Player) : RenewalResult

    /**
     * O jogador recusou. [cooldownUntil] não nulo: a oferta foi ofensiva e ele
     * não ouve outra até essa data (exclusive).
     */
    data class PlayerRefused(
        val playerId: PlayerId,
        val refusal: PlayerResponse.Refuses,
        val cooldownUntil: LocalDate?,
    ) : RenewalResult

    /** Ainda "esfriado" por uma recusa anterior: não ouve proposta antes de [until]. */
    data class TalksCoolingDown(val playerId: PlayerId, val until: LocalDate) : RenewalResult

    /** O jogador não está (mais) no elenco do clube. */
    data class PlayerNotInSquad(val playerId: PlayerId) : RenewalResult

    /** Lock otimista do clube (issue #89) que sobreviveu ao retry: nada foi aplicado. */
    data class Conflict(val clubId: ClubId) : RenewalResult
}
