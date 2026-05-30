package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.event.MatchEvent
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso para acompanhar UMA partida específica da rodada corrente
 * ao vivo (drill-down). Diferente de `PlayRoundUseCase`, que multiplexa
 * todas as partidas e altera estado pós-rodada, este apenas re-simula a
 * partida pedida (determinística pelo `matchId`) e não persiste nada.
 */
interface StreamMatchUseCase {
    fun stream(matchId: Long): Flow<MatchEvent>
}
