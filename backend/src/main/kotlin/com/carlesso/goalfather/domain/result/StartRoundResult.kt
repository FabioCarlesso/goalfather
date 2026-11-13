package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.ReadinessStatus

/**
 * Resultado de tentar iniciar a rodada compartilhada (issue #20). Erro de
 * negócio modelado como VALOR (sealed), não exception — o controller mapeia
 * cada variante para um status HTTP com `when` exaustivo, sem `else`.
 */
sealed interface StartRoundResult {
    /** Liberada: o encaixe [matchday] foi marcado `InProgress` (issue #125). */
    data class Started(val matchday: Matchday) : StartRoundResult

    /** Ainda faltam técnicos sinalizarem — vira 409 com a lista de pendentes. */
    data class NotReady(val status: ReadinessStatus) : StartRoundResult

    /** A rodada corrente já foi simulada (replay/duplo clique) — 409. */
    data class AlreadyFinished(val matchday: Matchday) : StartRoundResult

    /** Não há rodada no calendário — 404. */
    data object NoRound : StartRoundResult
}
