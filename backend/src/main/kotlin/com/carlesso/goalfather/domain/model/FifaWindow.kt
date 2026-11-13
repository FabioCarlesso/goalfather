package com.carlesso.goalfather.domain.model

import java.time.LocalDate

/**
 * Data FIFA (épico #148, onda 2): período em que as seleções jogam e as ligas
 * que pausam (`pausesOnFifaWindows`) não marcam jogos.
 *
 * Tipo compartilhado pelas issues #125 (o calendário reserva as semanas) e
 * #126 (o pack do mundo traz as datas em `calendar/{year}.json`). Fica no
 * domínio com `java.time`, que é JDK e não framework — não fere a regra de ouro.
 *
 * As datas valem para o mundo todo (o calendário internacional é da FIFA, não
 * da confederação), por isso não há país nem confederação aqui.
 */
data class FifaWindow(
    val start: LocalDate,
    val end: LocalDate,
) {
    init {
        require(!end.isBefore(start)) { "FifaWindow termina ($end) antes de começar ($start)" }
    }

    /** `true` se [date] cai dentro da janela (extremos inclusos). */
    operator fun contains(date: LocalDate): Boolean = date in start..end
}
