package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.event.RoundEvent
import kotlinx.coroutines.flow.Flow

/**
 * Use case de "jogar rodada". Retorna `Flow<RoundEvent>`: eventos de
 * partida (multiplexados por matchId) + `RoundFinished` no encerramento.
 *
 * O adapter Spring WebSocket (Fase 3) vai apenas coletar este Flow,
 * serializar cada evento como JSON e enviar via `WebSocketSession`. A
 * coordenação de várias partidas paralelas vive aqui no domínio/app,
 * não no controller.
 */
interface PlayRoundUseCase {
    fun stream(roundNumber: Int): Flow<RoundEvent>
}
