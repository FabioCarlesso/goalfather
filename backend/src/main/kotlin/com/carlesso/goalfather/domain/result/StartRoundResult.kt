package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.ReadinessStatus

/**
 * Resultado de tentar iniciar a rodada compartilhada (issue #20). Erro de
 * negócio modelado como VALOR (sealed), não exception — o controller mapeia
 * cada variante para um status HTTP com `when` exaustivo, sem `else`.
 */
sealed interface StartRoundResult {
    /** Liberada: a rodada `roundNumber` foi marcada `InProgress`. */
    data class Started(val roundNumber: Int) : StartRoundResult

    /** Ainda faltam técnicos sinalizarem — vira 409 com a lista de pendentes. */
    data class NotReady(val status: ReadinessStatus) : StartRoundResult

    /** A rodada corrente já foi simulada (replay/duplo clique) — 409. */
    data class AlreadyFinished(val roundNumber: Int) : StartRoundResult

    /** Não há rodada no calendário — 404. */
    data object NoRound : StartRoundResult
}
