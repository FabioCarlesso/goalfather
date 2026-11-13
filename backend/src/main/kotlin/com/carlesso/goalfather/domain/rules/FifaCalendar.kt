package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.FifaWindow
import com.carlesso.goalfather.domain.model.GameWeek
import java.time.LocalDate
import java.time.Month

/**
 * Datas FIFA de [year] (issue #125).
 *
 * **Interino** (mesmo padrão de `Countries`): as datas são DADO e vão morar no
 * pack do mundo (`calendar/{year}.json`, issue #126), lidas pelo
 * `WorldDataProvider` quando o mundo do MVP entrar no ar (issue #131). Até lá o
 * calendário usa o padrão do calendário internacional 2026–2030 aprovado pela
 * FIFA, repetido a cada ano:
 *
 * - março: 23 a 31;
 * - junho: 1 a 9;
 * - setembro/outubro: 21/09 a 06/10 — a janela única de 16 dias e 4 jogos
 *   que estreia em 2026;
 * - novembro: 9 a 17.
 *
 * A Copa do Mundo e os torneios de seleção não entram: são competições
 * (issue #144), não pausas.
 */
fun interimFifaWindows(year: Int): List<FifaWindow> = listOf(
    FifaWindow(LocalDate.of(year, Month.MARCH, 23), LocalDate.of(year, Month.MARCH, 31)),
    FifaWindow(LocalDate.of(year, Month.JUNE, 1), LocalDate.of(year, Month.JUNE, 9)),
    FifaWindow(LocalDate.of(year, Month.SEPTEMBER, 21), LocalDate.of(year, Month.OCTOBER, 6)),
    FifaWindow(LocalDate.of(year, Month.NOVEMBER, 9), LocalDate.of(year, Month.NOVEMBER, 17)),
)

/** Datas FIFA de vários anos — uma temporada europeia precisa de dois. */
fun interimFifaWindows(years: IntRange): List<FifaWindow> = years.flatMap(::interimFifaWindows)

/**
 * A data FIFA toca a [week]? A reserva é por SEMANA inteira: se a janela pega
 * qualquer dia de segunda a domingo, a semana é das seleções para quem pausa.
 * Extension aqui (e não método em `FifaWindow`) porque é regra do calendário
 * semanal; o tipo compartilhado com a issue #126 fica só com as datas.
 */
fun FifaWindow.touches(week: GameWeek): Boolean = !(end < week.monday || start > week.sunday)

/** Alguma das [windows] toca a [week]? */
fun List<FifaWindow>.reserves(week: GameWeek): Boolean = any { it.touches(week) }
