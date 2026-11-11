package com.carlesso.goalfather.domain.model

import java.time.LocalDate
import java.time.Month

/**
 * Como a temporada de um país se distribui no ano (issue #125). O Brasil joga
 * de janeiro a dezembro; a Europa, de agosto a maio, atravessando a virada do
 * ano. Conjunto fechado e sem dado próprio por variante — `enum`, com o
 * comportamento (as datas) no próprio valor, como `KnockoutPhase`.
 */
enum class SeasonSpan {
    /** Janeiro a dezembro do mesmo ano (Brasil, CONMEBOL). */
    CALENDAR_YEAR {
        override fun start(year: Int): LocalDate = LocalDate.of(year, Month.JANUARY, 1)
        override fun end(year: Int): LocalDate = LocalDate.of(year, Month.DECEMBER, 31)
    },

    /** Agosto de [year] a maio do ano seguinte (ligas da UEFA). */
    AUGUST_TO_MAY {
        override fun start(year: Int): LocalDate = LocalDate.of(year, Month.AUGUST, 1)
        override fun end(year: Int): LocalDate = LocalDate.of(year + 1, Month.MAY, 31)
    },
    ;

    abstract fun start(year: Int): LocalDate

    abstract fun end(year: Int): LocalDate

    companion object {
        /**
         * Calendário da confederação. A UEFA é a única do MVP (épico #148) que
         * atravessa o ano; as demais seguem o ano civil até o pack do mundo
         * (#126/#131) trazer a exceção de cada país.
         */
        fun of(confederation: Confederation): SeasonSpan = when (confederation) {
            Confederation.UEFA -> AUGUST_TO_MAY
            Confederation.CONMEBOL,
            Confederation.CONCACAF,
            Confederation.CAF,
            Confederation.AFC,
            Confederation.OFC,
            -> CALENDAR_YEAR
        }
    }
}

/**
 * Uma edição de uma competição no tempo (issue #125): a [competition] + a
 * janela em que ela pode marcar jogos. [year] é o ano de INÍCIO, que rotula a
 * temporada (`2026` no Brasil, `2026/27` na Europa — ver [label]).
 *
 * Cada competição tem a sua temporada e vira SOZINHA quando termina: a
 * europeia de 2026/27 encerra em maio de 2027 enquanto a brasileira de 2027
 * já está em andamento. O ano do jogo é o ano civil; quem atravessa a virada
 * é a competição, não o jogo.
 *
 * [pausesOnFifaWindows]: ligas europeias param nas datas FIFA; o Brasileirão
 * costuma seguir jogando. Fica aqui, e não em [Competition], porque é regra do
 * calendário daquela edição (a CBF muda de ideia de um ano para o outro) — e
 * porque [Competition] é o formato, que não sabe de datas.
 */
data class CompetitionSeason(
    val competition: Competition,
    val year: Int,
    val start: LocalDate,
    val end: LocalDate,
    val pausesOnFifaWindows: Boolean = true,
) {
    init {
        require(!end.isBefore(start)) { "Temporada de ${competition.id.value} termina ($end) antes de começar ($start)" }
    }

    /** `2026` quando cabe num ano; `2026/27` quando atravessa a virada. */
    val label: String
        get() = if (start.year == end.year) "$year" else "$year/%02d".format(end.year % 100)

    /**
     * Encaixe preferido: liga no fim de semana; copa, mata-mata e continental
     * no meio. `when` exaustivo sobre o `sealed` [CompetitionFormat] — um
     * formato novo obriga a decidir aqui em que dia ele joga.
     */
    val preferredSlot: Slot
        get() = when (competition.format) {
            is CompetitionFormat.RoundRobin -> Slot.WEEKEND
            is CompetitionFormat.Knockout,
            is CompetitionFormat.GroupsThenKnockout,
            is CompetitionFormat.SwissLeague,
            -> Slot.MIDWEEK
        }

    /** `matchday in temporada` — o encaixe cai dentro da janela? */
    operator fun contains(matchday: Matchday): Boolean = matchday.date in start..end

    /** A edição seguinte: mesma competição e regras, um ano depois. */
    fun next(): CompetitionSeason = copy(year = year + 1, start = start.plusYears(1), end = end.plusYears(1))

    companion object {
        /** Edição de [year] na janela do [span] do país (ou confederação). */
        fun of(
            competition: Competition,
            span: SeasonSpan,
            year: Int,
            pausesOnFifaWindows: Boolean = true,
        ): CompetitionSeason = CompetitionSeason(competition, year, span.start(year), span.end(year), pausesOnFifaWindows)
    }
}
