package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

/**
 * Uma semana do jogo (issue #125): ano + semana ISO-8601. O relógio do jogo
 * anda de semana em semana, e cada semana tem dois [Slot]s de jogo.
 *
 * Semana ISO e não "semana do mês" porque é o que o `java.time` já sabe
 * calcular sem ambiguidade: semanas começam na segunda, a semana 1 é a que
 * contém a primeira quinta-feira do ano, e um ano tem 52 ou 53 semanas. `java.time`
 * é JDK, não framework — pode ficar no domínio sem ferir a regra de ouro.
 *
 * Atenção ao [year]: é o ano BASEADO EM SEMANA (`IsoFields.WEEK_BASED_YEAR`),
 * não o ano civil. 29/12/2026 cai na semana 53 de 2026, mas 01/01/2027 cai na
 * semana 53 de 2026 também — por isso a temporada civil do jogo se lê pela
 * data do encaixe ([Matchday.date]), nunca pelo ano da semana.
 *
 * `Comparable` permite `a < b`, `maxOf`, `sorted()` e faixas `a..b` com a
 * ordem natural do tempo — o operador `<` do Kotlin chama `compareTo`.
 */
@Serializable
data class GameWeek(val year: Int, val week: Int) : Comparable<GameWeek> {
    init {
        require(week in 1..weeksInYear(year)) { "Semana $week fora de 1..${weeksInYear(year)} em $year" }
    }

    /** Segunda-feira da semana — o dia que ancora todas as contas de data. */
    val monday: LocalDate
        get() = LocalDate.of(year, 1, 4)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
            .with(DayOfWeek.MONDAY)

    /** Domingo da semana (fim inclusivo). */
    val sunday: LocalDate get() = monday.plusDays(6)

    /** A semana [weeks] semanas adiante (negativo volta). */
    operator fun plus(weeks: Int): GameWeek = of(monday.plusWeeks(weeks.toLong()))

    fun next(): GameWeek = this + 1

    /** `date in semana` — a semana contém o dia? */
    operator fun contains(date: LocalDate): Boolean = date in monday..sunday

    // `compareValuesBy` (stdlib) compara campo a campo, na ordem dada: o
    // `compareTo` de uma linha, sem o `if` aninhado do Java.
    override fun compareTo(other: GameWeek): Int = compareValuesBy(this, other, GameWeek::year, GameWeek::week)

    override fun toString(): String = "%04d-W%02d".format(year, week)

    companion object {
        /** A semana ISO que contém [date]. */
        fun of(date: LocalDate): GameWeek =
            GameWeek(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

        /** 52 ou 53: 28 de dezembro cai SEMPRE na última semana ISO do ano. */
        fun weeksInYear(year: Int): Int = LocalDate.of(year, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    }
}

/**
 * Os dois encaixes de jogo de uma semana (issue #125). O dia de cada um é
 * fixo — quarta e domingo —, o que basta para saber em que data cai um jogo e
 * se ela toca uma data FIFA. Ligas preferem o fim de semana; copas e
 * continentais, o meio (ver `CompetitionSeason.preferredSlot`).
 *
 * A ORDEM das constantes é a ordem no tempo dentro da semana: `MIDWEEK` antes
 * de `WEEKEND`. O `compareTo` de [Matchday] depende disso (`ordinal`).
 */
@Serializable
enum class Slot(val dayOfWeek: DayOfWeek) {
    MIDWEEK(DayOfWeek.WEDNESDAY),
    WEEKEND(DayOfWeek.SUNDAY),
}

/**
 * A "data" de jogo do calendário (issue #125): semana + encaixe. Substitui o
 * antigo número de rodada global — que recomeçava a cada temporada e por isso
 * não identificava uma data sozinho. Um `Matchday` identifica: é a chave da
 * rodada persistida, da prontidão multiplayer e do WebSocket.
 *
 * [key] é a forma textual estável (`2026-W05-WEEKEND`) que cruza a API e a URL
 * do WebSocket; [serial] é um inteiro monotônico (encaixes desde 1970) usado
 * onde o código precisa de um número — seeds de RNG e `matchId`.
 */
@Serializable
data class Matchday(val week: GameWeek, val slot: Slot) : Comparable<Matchday> {

    /** O dia do jogo: quarta ou domingo da [week]. */
    val date: LocalDate get() = week.monday.with(slot.dayOfWeek)

    val key: String get() = "$week-${slot.name}"

    /**
     * Posição do encaixe na linha do tempo: 2 por semana desde a segunda-feira
     * 05/01/1970. Injetivo e crescente — encaixe seguinte, serial + 1.
     */
    val serial: Int get() = ChronoUnit.WEEKS.between(EPOCH_MONDAY, week.monday).toInt() * 2 + slot.ordinal

    /** O encaixe seguinte: meio → fim da mesma semana; fim → meio da próxima. */
    fun next(): Matchday = when (slot) {
        Slot.MIDWEEK -> copy(slot = Slot.WEEKEND)
        Slot.WEEKEND -> Matchday(week.next(), Slot.MIDWEEK)
    }

    override fun compareTo(other: Matchday): Int = compareValuesBy(this, other, Matchday::week, Matchday::slot)

    override fun toString(): String = key

    companion object {
        private val EPOCH_MONDAY: LocalDate = LocalDate.of(1970, 1, 5)
        private val KEY = Regex("""(\d{4})-W(\d{2})-(MIDWEEK|WEEKEND)""")

        /**
         * Faixa de `matchId` de cada encaixe: `matchId = serial * 1000 + n`, na
         * liga (`RoundFixturesGenerator`) e na copa (`CupRules`). Uma constante
         * só, para que [ofMatchId] não desande se a fórmula mudar.
         */
        const val MATCH_IDS_PER_MATCHDAY: Long = 1000

        /** Inverso de [serial]. `null` antes do primeiro encaixe (serial negativo). */
        fun fromSerial(serial: Int): Matchday? {
            if (serial < 0) return null
            val week = GameWeek.of(EPOCH_MONDAY.plusWeeks(serial / 2L))
            return Matchday(week, Slot.entries[serial % 2])
        }

        /**
         * O encaixe em que a partida [matchId] é jogada (issue #151): o
         * drill-down acha a rodada da partida sem depender de ela ainda ser a
         * "última" — depois de encerrada, `findLatest` já aponta a seguinte.
         * `null` para id que não sai da fórmula (zero ou negativo).
         */
        fun ofMatchId(matchId: Long): Matchday? =
            (matchId / MATCH_IDS_PER_MATCHDAY)
                .takeIf { matchId > 0 && it <= Int.MAX_VALUE }
                ?.let { fromSerial(it.toInt()) }

        /**
         * Lê a forma de [key]. `null` para texto malformado ou semana que o
         * ano não tem (W53 num ano de 52) — entrada de URL é dado externo, e
         * ausência no tipo é o idioma do projeto para "não deu", sem exception.
         */
        fun parse(key: String): Matchday? {
            val (year, week, slot) = KEY.matchEntire(key)?.destructured ?: return null
            val y = year.toInt()
            val w = week.toInt()
            if (w !in 1..GameWeek.weeksInYear(y)) return null
            return Matchday(GameWeek(y, w), Slot.valueOf(slot))
        }

        /** O encaixe da semana de [date] no [slot] dado. */
        fun of(date: LocalDate, slot: Slot): Matchday = Matchday(GameWeek.of(date), slot)

        /**
         * Todos os encaixes cuja data cai em `start..end` (inclusivo), em ordem.
         * `generateSequence` produz a sequência preguiçosa "este, o próximo, …"
         * e o `takeWhile` a corta — sem `while` nem lista mutável.
         */
        fun between(start: LocalDate, end: LocalDate): List<Matchday> =
            generateSequence(of(start, Slot.MIDWEEK)) { it.next() }
                .dropWhile { it.date < start }
                .takeWhile { it.date <= end }
                .toList()
    }
}
