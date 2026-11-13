package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.event.MatchEvent
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso para acompanhar UMA partida específica ao vivo (drill-down).
 * Diferente de `PlayRoundUseCase`, que multiplexa todas as partidas e altera
 * estado pós-rodada, este apenas re-simula a partida pedida (determinística
 * pelo `matchId`) e não persiste nada.
 *
 * Desde a issue #151 abrir o stream pode ser recusado: partida de encaixe
 * ainda não liberado não é simulada, ou o drill-down viraria uma prévia do
 * resultado. A recusa é um VALOR ([MatchStream.NotStarted]), não exception.
 * `suspend` porque decidir exige ler o encaixe antes de devolver o Flow.
 */
interface StreamMatchUseCase {
    suspend fun open(matchId: Long): MatchStream
}

/**
 * Desfecho de [StreamMatchUseCase.open]. `sealed` + `when` exaustivo no
 * adapter: um desfecho novo quebra a compilação lá, em vez de cair num `else`.
 */
sealed interface MatchStream {
    /** Encaixe liberado (`InProgress` ou `Finished`): os eventos da partida. */
    data class Live(val events: Flow<MatchEvent>) : MatchStream

    /** Encaixe ainda `Scheduled`: nada é simulado. */
    data object NotStarted : MatchStream
}
