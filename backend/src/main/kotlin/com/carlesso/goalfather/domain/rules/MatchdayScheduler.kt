package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionSeason
import com.carlesso.goalfather.domain.model.FifaWindow
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Slot

/**
 * Agendador do calendário semanal (issue #125). Funções PURAS: mesmas
 * competições + mesmas datas FIFA dão sempre o mesmo calendário — por isso
 * ele não precisa ser persistido, só recalculado.
 *
 * O agendador não sabe de formato nem de pareamento. Cada competição chega
 * como uma lista de RODADAS ordenadas, e cada rodada como o conjunto de clubes
 * que ela ocupa: na liga, os clubes dos jogos daquela rodada; num mata-mata
 * cujos confrontos ainda não foram sorteados, TODOS os clubes que ainda podem
 * estar vivos — a reserva é pessimista, e é ela que garante que o sorteio da
 * fase seguinte nunca cria conflito.
 */

/** Uma competição a encaixar: a edição + as rodadas, cada uma com os clubes que ela ocupa. */
data class ScheduleRequest(
    val season: CompetitionSeason,
    val rounds: List<Set<ClubId>>,
) {
    init {
        require(rounds.isNotEmpty()) { "Competição ${season.competition.id.value} sem rodadas para agendar" }
    }
}

/** A rodada [round] (1-based) de uma competição, marcada para o encaixe [matchday]. */
data class ScheduledRound(
    val competition: CompetitionId,
    val seasonYear: Int,
    val round: Int,
    val matchday: Matchday,
    val clubs: Set<ClubId>,
)

/**
 * O calendário montado: rodadas de várias competições, em ordem de encaixe.
 * Imutável — agendar mais uma competição devolve um calendário novo ([plus]).
 */
data class Schedule(val rounds: List<ScheduledRound>) {

    /** Encaixes com algum jogo, em ordem. */
    val matchdays: List<Matchday> by lazy { rounds.map { it.matchday }.distinct().sorted() }

    /** O que se joga em [matchday]. */
    fun at(matchday: Matchday): List<ScheduledRound> = rounds.filter { it.matchday == matchday }

    /** O primeiro encaixe com jogo; `null` num calendário vazio. */
    val first: Matchday? get() = matchdays.firstOrNull()

    /** O próximo encaixe com jogo DEPOIS de [matchday]; `null` se o calendário acabou. */
    fun nextAfter(matchday: Matchday): Matchday? = matchdays.firstOrNull { it > matchday }

    /** O último encaixe de [competition] — quando a temporada dela termina. */
    fun lastOf(competition: CompetitionId): Matchday? =
        rounds.filter { it.competition == competition }.maxOfOrNull { it.matchday }

    /**
     * Competições cuja temporada termina em [matchday]: a última rodada delas
     * é a deste encaixe. É o gatilho da virada POR COMPETIÇÃO — cada uma vira
     * sozinha, no seu tempo.
     */
    fun endingAt(matchday: Matchday): Set<CompetitionId> =
        rounds.map { it.competition }.distinct().filterTo(HashSet()) { lastOf(it) == matchday }

    /** Clubes ocupados em cada encaixe. */
    fun occupancy(): Map<Matchday, Set<ClubId>> =
        rounds.groupBy({ it.matchday }, { it.clubs }).mapValues { (_, sets) -> sets.flatten().toSet() }

    operator fun plus(other: Schedule): Schedule = Schedule((rounds + other.rounds).sortedBy { it.matchday })

    companion object {
        val EMPTY = Schedule(emptyList())
    }
}

/**
 * Resultado do agendamento. Não caber no calendário é desfecho de negócio
 * (temporada curta demais para o formato, datas FIFA demais), então vira VALOR
 * — o chamador decide o que fazer, nada de exception (CLAUDE.md).
 */
sealed interface ScheduleResult {
    data class Scheduled(val schedule: Schedule) : ScheduleResult

    /** A rodada [round] de [competition] não achou encaixe livre dentro da janela. */
    data class NoRoomLeft(val competition: CompetitionId, val round: Int) : ScheduleResult
}

/**
 * Encaixa [requests] no calendário, por cima do que [existing] já ocupa, sem
 * nunca pôr um clube em dois jogos no mesmo encaixe.
 *
 * Para cada competição:
 * 1. os encaixes candidatos são os da janela da temporada, menos as semanas
 *    tocadas por data FIFA quando ela pausa ([CompetitionSeason.pausesOnFifaWindows]);
 * 2. as rodadas se ESPALHAM pela janela: a rodada `i` de `R` mira a semana
 *    `i·W/R` das `W` semanas candidatas — 5 rodadas num ano viram uma a cada
 *    ~10 semanas, 38 rodadas ocupam quase toda semana;
 * 3. a partir da semana-alvo, o primeiro encaixe livre (nenhum clube da
 *    rodada já ocupado nele), preferindo o encaixe da competição
 *    ([CompetitionSeason.preferredSlot]) ao outro da mesma semana; se não
 *    houver nada até o fim da janela, tenta antes do alvo;
 * 4. cada rodada vem estritamente depois da anterior — a ordem dos turnos e
 *    das fases é preservada.
 *
 * Ligas (que preferem o fim de semana) são encaixadas primeiro: o calendário
 * delas é o mais denso e o menos flexível. Dentro de cada grupo vale a ordem
 * de [requests] — determinístico.
 *
 * [notBefore]: só encaixes DEPOIS dele são candidatos — reagendar no meio do
 * jogo nunca marca rodada no passado.
 */
fun scheduleSeasons(
    requests: List<ScheduleRequest>,
    fifaWindows: List<FifaWindow>,
    existing: Schedule = Schedule.EMPTY,
    notBefore: Matchday? = null,
): ScheduleResult {
    // Ocupação mutável LOCAL: o algoritmo guloso é mais claro com um mapa que
    // cresce a cada rodada encaixada. Não escapa — a função segue pura por fora.
    val occupied: MutableMap<Matchday, MutableSet<ClubId>> =
        existing.occupancy().mapValuesTo(HashMap()) { (_, clubs) -> clubs.toHashSet() }
    val placed = mutableListOf<ScheduledRound>()

    for (request in requests.sortedBy { if (it.season.preferredSlot == Slot.WEEKEND) 0 else 1 }) {
        val season = request.season
        val candidates = Matchday.between(season.start, season.end)
            .filterNot { season.pausesOnFifaWindows && fifaWindows.reserves(it.week) }
            .filter { notBefore == null || it > notBefore }
        val weeks = candidates.map { it.week }.distinct()
        // Posição de cada candidato na ordem do TEMPO: quantos encaixes ainda
        // sobram depois dele na janela.
        val slotsAfter = candidates.withIndex().associate { (i, m) -> m to candidates.size - 1 - i }
        // Dentro da semana, o encaixe preferido vem antes; entre semanas, a ordem do tempo.
        val ordered = candidates.sortedWith(
            compareBy<Matchday> { it.week }.thenBy { if (it.slot == season.preferredSlot) 0 else 1 },
        )

        var previous: Matchday? = null
        for ((index, clubs) in request.rounds.withIndex()) {
            val round = index + 1
            if (weeks.isEmpty()) return ScheduleResult.NoRoomLeft(season.competition.id, round)
            val target = weeks[index * weeks.size / request.rounds.size]
            val roundsLeft = request.rounds.size - round
            // Cópia `val`: a lambda abaixo não pode ler um `var` que o laço muda
            // (o compilador recusaria o smart cast de `Matchday?` para `Matchday`).
            val last = previous
            val afterPrevious = ordered.filter { last == null || it > last }
            fun isFree(matchday: Matchday) = occupied[matchday].orEmpty().none { it in clubs }
            // Folga: depois do escolhido ainda cabem as rodadas que faltam. Sem
            // isso, uma liga de 38 rodadas em 36 semanas pegaria só domingos e
            // ficaria sem encaixe no fim — com ela, o meio de semana entra
            // quando a conta aperta.
            fun leavesRoom(matchday: Matchday) = slotsAfter.getValue(matchday) >= roundsLeft

            val chosen = afterPrevious.firstOrNull { it.week >= target && isFree(it) && leavesRoom(it) }
                ?: afterPrevious.firstOrNull { isFree(it) && leavesRoom(it) }
                // Nem a folga cabe: o encaixe livre mais CEDO deixa o máximo de espaço.
                ?: afterPrevious.sorted().firstOrNull(::isFree)
                ?: return ScheduleResult.NoRoomLeft(season.competition.id, round)

            occupied.getOrPut(chosen) { HashSet() } += clubs
            placed += ScheduledRound(season.competition.id, season.year, round, chosen, clubs)
            previous = chosen
        }
    }
    return ScheduleResult.Scheduled(existing + Schedule(placed))
}

/**
 * Virada de UMA competição (issue #125): agenda a edição seguinte de
 * [finished] depois de [now], por cima do que o calendário ainda tem a jogar.
 * As outras competições seguem onde estavam — a europeia não espera a
 * brasileira acabar, nem o contrário. O passado fica no calendário devolvido
 * (é história), mas nada novo é marcado nele.
 */
fun Schedule.rollOver(
    finished: CompetitionSeason,
    nextRounds: List<Set<ClubId>>,
    fifaWindows: List<FifaWindow>,
    now: Matchday,
): ScheduleResult =
    scheduleSeasons(listOf(ScheduleRequest(finished.next(), nextRounds)), fifaWindows, existing = this, notBefore = now)
