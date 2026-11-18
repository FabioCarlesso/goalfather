package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.FifaWindow
import com.carlesso.goalfather.domain.model.GameWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Datas FIFA de [year] (issues #125 e #134).
 *
 * **Interino** (mesmo padrão de `Countries`): as datas são DADO e vão morar no
 * pack do mundo (`calendar/{year}.json`, issue #126). O pack só traz 2026, e
 * nem completo; até ele trazer o calendário inteiro, o jogo usa o padrão do
 * calendário internacional 2026–2030 aprovado pela FIFA, repetido a cada ano:
 *
 * - março: 9 dias, 2 jogos;
 * - junho: 9 dias, 2 jogos;
 * - setembro/outubro: 16 dias, 4 jogos — a janela única que estreia em 2026;
 * - novembro: 9 dias, 2 jogos.
 *
 * **Alinhadas à segunda-feira (issue #134).** As janelas da FIFA sempre
 * começam numa segunda (2026: 23/03, 01/06, 21/09, 09/11; 2027: 22/03,
 * 20/09, 08/11). Repetir o dia do mês de 2026 em outros anos fazia a janela
 * começar no meio da semana e reservar uma semana a mais das ligas que pausam.
 * Agora cada janela começa na segunda-feira da semana do dia de 2026 e tem a
 * duração do calendário: em 2026 as datas não mudam; nos outros anos ficam a
 * no máximo 6 dias das reais (junho de 2027 em diante é a mais incerta — o
 * calendário publicado só fixa o padrão).
 *
 * A Copa do Mundo e os torneios de seleção não entram: são competições
 * (issue #144), não pausas.
 */
fun interimFifaWindows(year: Int): List<FifaWindow> = listOf(
    mondayWindow(LocalDate.of(year, Month.MARCH, 23), days = 9),
    mondayWindow(LocalDate.of(year, Month.JUNE, 1), days = 9),
    mondayWindow(LocalDate.of(year, Month.SEPTEMBER, 21), days = 16),
    mondayWindow(LocalDate.of(year, Month.NOVEMBER, 9), days = 9),
)

/**
 * Janela de [days] dias que começa na segunda-feira da semana de [nominal].
 * `TemporalAdjusters.previousOrSame` é o "volte até a segunda" do `java.time`,
 * sem aritmética de dia da semana à mão.
 */
private fun mondayWindow(nominal: LocalDate, days: Long): FifaWindow {
    val start = nominal.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return FifaWindow(start, start.plusDays(days - 1))
}

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

/** Duração mínima, em dias, de uma janela de 4 jogos (a de setembro/outubro tem 16). */
private const val FOUR_MATCH_WINDOW_DAYS: Long = 14

/**
 * Quantos jogos as seleções fazem na janela (issue #134): 4 na janela longa de
 * setembro/outubro, 2 nas demais — a regra do calendário FIFA 2026–2030.
 * Derivado da duração para valer também com as datas do pack.
 */
val FifaWindow.matchSlots: Int
    get() = if (ChronoUnit.DAYS.between(start, end) + 1 >= FOUR_MATCH_WINDOW_DAYS) 4 else 2

/** Primeira e última semana do jogo tocadas pela janela. */
val FifaWindow.firstWeek: GameWeek get() = GameWeek.of(start)
val FifaWindow.lastWeek: GameWeek get() = GameWeek.of(end)
