package com.carlesso.goalfather.domain.model

/**
 * Uma semana do calendário do clube (issue #133): a semana ISO, se ela é (ou
 * toca) uma data FIFA e os jogos do clube nela, em ordem de encaixe.
 *
 * Reaproveita [NextMatch] (encaixe + partida): cada jogo do calendário é "um
 * jogo do clube num encaixe", o mesmo par que o relógio já devolve.
 */
data class ScheduleWeek(
    val week: GameWeek,
    val fifaWindow: Boolean,
    val matches: List<NextMatch>,
)

/** O calendário do clube nas próximas semanas, a partir da semana do encaixe [current]. */
data class ClubSchedule(
    val clubId: ClubId,
    val current: Matchday?,
    val weeks: List<ScheduleWeek>,
)
