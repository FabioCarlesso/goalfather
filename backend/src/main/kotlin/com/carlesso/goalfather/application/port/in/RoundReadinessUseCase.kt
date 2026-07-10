package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ReadinessStatus
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.StartRoundResult

/**
 * Coordenação "todos prontos → joga rodada" da liga compartilhada (issue #20).
 *
 * Separa o "sinalizar pronto" (cada técnico) da "tentativa de iniciar" (o
 * gate que destrava a simulação). O controller só conversa com esta porta.
 */
interface RoundReadinessUseCase {
    /** Estado atual de prontidão da rodada corrente (para o card de lobby). */
    suspend fun status(): ReadinessStatus

    /** Marca o técnico como pronto e devolve o estado atualizado. */
    suspend fun markReady(userId: UserId): ReadinessStatus

    /**
     * Tenta destravar a rodada. Só transiciona para `InProgress` quando todos
     * os técnicos humanos estão prontos; a transição é serializada no banco por
     * lock otimista (`LeagueRepository.startRound`, issue #46), então dois
     * cliques simultâneos — no mesmo processo ou em instâncias diferentes — não
     * disparam a simulação duas vezes.
     */
    suspend fun start(): StartRoundResult
}
