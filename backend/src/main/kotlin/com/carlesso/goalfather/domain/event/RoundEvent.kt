package com.carlesso.goalfather.domain.event

import com.carlesso.goalfather.domain.model.Standings

/**
 * Mensagem do stream de rodada (WS `/ws/round/{n}`).
 *
 * Multiplexa eventos de várias partidas + sinal de fim de rodada.
 * Mapeia o schema `RoundEvent` no `contract/openapi.yaml` (oneOf +
 * discriminator `type`).
 *
 * Backend Spring vai expor isso como `Flow<RoundEvent>` —
 * `PlayRoundService.stream(roundNumber)` já produz exatamente esse
 * tipo.
 */
sealed interface RoundEvent {

    /** Evento individual de uma partida, com `matchId` para o cliente desambiguar. */
    data class MatchUpdate(
        val matchId: Long,
        val event: MatchEvent,
    ) : RoundEvent

    /** Último frame do stream. Carrega a tabela já recalculada. */
    data class RoundFinished(val standings: Standings) : RoundEvent
}
