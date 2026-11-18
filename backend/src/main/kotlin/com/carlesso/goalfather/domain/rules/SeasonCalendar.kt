package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.CompetitionSeason
import com.carlesso.goalfather.domain.model.ContinentalCup
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.FifaWindow
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.NextMatch
import com.carlesso.goalfather.domain.model.Pairing
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.Slot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Calendário do MUNDO (issues #125 e #131): as competições do catálogo do
 * save — uma liga por (país, tier) e a copa nacional de cada país —, cada uma
 * na SUA temporada corrente, encaixadas na semana pelo agendador genérico
 * ([scheduleSeasons]).
 *
 * **Uma temporada por país.** O Brasil joga jan–dez e a Europa ago–mai, então
 * no mesmo encaixe convivem o Brasil de 2027 e a Europa de 2026/27. O estado
 * que o plano precisa é, por país, o ano de início da edição corrente
 * ([WorldSeasons]) — o resto (clubes, catálogo, copa sorteada) já está no save.
 *
 * **País a país, e por isso recalculável.** Os clubes de dois países nunca se
 * cruzam nas competições domésticas, então o encaixe de um país não depende
 * do outro: o plano do mundo é a UNIÃO dos planos de cada país. É isso que
 * deixa recalcular o plano a cada encaixe encerrado (ele nunca é persistido)
 * sem que a virada do Brasil mexa uma vírgula no calendário da Premier League —
 * a "nova edição por cima do calendário corrente" sai de graça. As janelas
 * (`SeasonSpan`) de duas edições seguidas do mesmo país não se sobrepõem, então
 * a edição nova nunca cai no passado (o serviço confere: [SeasonPlan.first]).
 *
 * **Datas FIFA**: ainda as interinas ([interimFifaWindows]) do ano de início e
 * do seguinte — o pack já traz `calendar/2026.json`, mas só um ano; quando o
 * pack trouxer a série, o plano passa a recebê-las do catálogo.
 */

/** O ano de início da edição que cada país está jogando (`2026` = Brasil 2026, Europa 2026/27). */
typealias WorldSeasons = Map<CountryCode, Int>

/** Uma competição do plano, já na temporada corrente — o que o calendário precisa para gerar os jogos. */
sealed interface SeasonCompetition {
    val game: GameCompetition
    val season: CompetitionSeason

    /**
     * Liga de pontos corridos: os [rounds] de pareamentos (turno e returno do
     * formato do catálogo) e o [slot] da liga na faixa de `matchId`.
     */
    data class League(
        override val game: GameCompetition,
        override val season: CompetitionSeason,
        val slot: Int,
        val rounds: List<List<Pairing>>,
    ) : SeasonCompetition

    /** Copa nacional já sorteada. Só a fase corrente tem confrontos conhecidos. */
    data class NationalCup(
        override val game: GameCompetition,
        override val season: CompetitionSeason,
        val cup: Cup,
    ) : SeasonCompetition

    /**
     * Continental já sorteada (issue #132): os jogos de grupo são conhecidos de
     * antemão; os do mata-mata, só a fase corrente — como a copa nacional.
     */
    data class Continental(
        override val game: GameCompetition,
        override val season: CompetitionSeason,
        val edition: ContinentalCup,
    ) : SeasonCompetition
}

/**
 * As competições do mundo na temporada corrente de cada país + o calendário
 * montado. [roundAt] transforma um encaixe nos jogos de verdade.
 */
data class SeasonPlan(
    val competitions: Map<CompetitionId, SeasonCompetition>,
    val schedule: Schedule,
) {
    val first: Matchday? get() = schedule.first

    fun nextAfter(matchday: Matchday): Matchday? = schedule.nextAfter(matchday)

    /** A temporada corrente de cada país do plano. */
    val seasons: WorldSeasons
        get() = competitions.values.associate { it.game.country.code to it.season.year }

    /**
     * Países cujas competições domésticas já terminaram TODAS em [matchday]: a
     * última rodada do país (de qualquer competição dele) é a deste encaixe —
     * ou anterior, o que só acontece num save que parou entre o fim e a virada
     * (vira agora, em vez de nunca). É o gatilho da virada POR PAÍS — o Brasil
     * vira em dezembro, a Europa em maio.
     */
    fun countriesEndingBy(matchday: Matchday): Set<CountryCode> {
        val lastByCountry = schedule.rounds
            .groupBy { competitions.getValue(it.competition).game.country.code }
            .mapValues { (_, rounds) -> rounds.maxOf { it.matchday } }
        return lastByCountry.filterValues { it <= matchday }.keys
    }

    /**
     * Os jogos do encaixe [matchday]: a rodada de cada liga marcada nele e, se
     * uma copa estiver nele, os confrontos em aberto da fase corrente. O `when`
     * é exaustivo sobre [SeasonCompetition]: uma competição de outro papel vira
     * erro de compilação aqui, não um encaixe jogado como liga.
     *
     * [Round.season] é o ano CIVIL do encaixe — com várias temporadas no mesmo
     * domingo, a temporada é de cada competição, não da rodada.
     */
    fun roundAt(matchday: Matchday, clubs: Collection<Club>): Round {
        val byId = clubs.associateBy { it.id }
        val matches = schedule.at(matchday).flatMap { scheduled ->
            when (val competition = competitions.getValue(scheduled.competition)) {
                is SeasonCompetition.League ->
                    leagueMatches(
                        matchday,
                        scheduled.round,
                        competition.game,
                        competition.slot,
                        competition.rounds[scheduled.round - 1],
                        byId,
                    )
                is SeasonCompetition.NationalCup ->
                    nationalCupMatches(matchday, competition.cup, competition.game, clubs)
                is SeasonCompetition.Continental ->
                    continentalMatches(matchday, competition.edition, scheduled.round)
            }
        }
        return Round(matchday = matchday, season = matchday.date.year, status = RoundStatus.Scheduled, matches = matches)
    }

    /**
     * O próximo jogo de [clubId] DEPOIS do encaixe [after], como o calendário o
     * enxerga hoje. Jogos de liga são conhecidos de antemão (Berger); da copa
     * só se conhece a fase corrente — a seguinte depende de resultados —, então
     * só o PRIMEIRO encaixe de cada copa adiante entra na busca, e nenhum
     * quando a fase corrente dela ainda está em jogo ([cupsInPlay]).
     */
    fun nextMatchOf(
        clubId: ClubId,
        after: Matchday,
        clubs: Collection<Club>,
        cupsInPlay: Set<CompetitionId> = emptySet(),
    ): NextMatch? {
        val cupsUnknown = cupsInPlay.toMutableSet()
        for (matchday in schedule.matchdays.filter { it > after }) {
            // Mata-mata (copa nacional ou o da continental, issue #132): só a
            // fase corrente é conhecida.
            val cups = schedule.at(matchday)
                .filter { scheduled ->
                    when (val competition = competitions[scheduled.competition]) {
                        is SeasonCompetition.NationalCup -> true
                        is SeasonCompetition.Continental -> scheduled.round > competition.edition.groupRounds
                        is SeasonCompetition.League, null -> false
                    }
                }
                .mapTo(HashSet()) { it.competition }
            val visible = Round(
                matchday = matchday,
                season = matchday.date.year,
                matches = roundAt(matchday, clubs).matches.filterNot { it.competition in cupsUnknown },
            )
            val match = visible.matches.firstOrNull { it.involves(clubId) }
            if (match != null) return NextMatch(matchday, match)
            cupsUnknown += cups
        }
        return null
    }

    /** O plano com as competições e o calendário de [other] junto (países disjuntos). */
    operator fun plus(other: SeasonPlan): SeasonPlan =
        SeasonPlan(competitions + other.competitions, schedule + other.schedule)

    companion object {
        val EMPTY = SeasonPlan(emptyMap(), Schedule.EMPTY)
    }
}

/**
 * Plano do mundo: o de cada país do catálogo com temporada em [seasons], unidos.
 * País sem temporada corrente fica de fora (ainda não começou).
 *
 * [cups]: a copa já sorteada de cada copa nacional, pela competição. Copa
 * ausente = o país joga só as ligas (o motor não comportou os inscritos, ou o
 * save é anterior à copa).
 */
fun worldPlan(
    competitions: List<GameCompetition>,
    clubs: Collection<Club>,
    seasons: WorldSeasons,
    cups: Map<CompetitionId, Cup> = emptyMap(),
    // A edição corrente de cada continental (issue #132). Ausente = não joga.
    continentals: Map<CompetitionId, ContinentalCup> = emptyMap(),
): SeasonPlan {
    val slots = leagueSlots(competitions)
    return competitions.groupBy { it.country.code }
        .toList()
        .mapNotNull { (code, own) ->
            seasons[code]?.let { year ->
                countryPlan(own, year, clubs.filter { it.country.code == code }, cups, slots, continentals = continentals)
            }
        }
        .fold(SeasonPlan.EMPTY, SeasonPlan::plus)
}

/**
 * O slot de cada liga na faixa de `matchId` do encaixe: a posição dela entre
 * as ligas do catálogo, 1-based. Fixo enquanto o catálogo do save não muda —
 * e ele não muda num jogo em andamento.
 */
fun leagueSlots(competitions: List<GameCompetition>): Map<CompetitionId, Int> {
    val leagues = competitions.filter { it.kind is CompetitionKind.League }
    require(leagues.size <= MAX_LEAGUE_SLOTS) {
        "O calendário comporta até $MAX_LEAGUE_SLOTS ligas por encaixe (faixa de matchId): ${leagues.size}"
    }
    return leagues.withIndex().associate { (i, league) -> league.id to i + 1 }
}

/**
 * Plano de UM país na temporada [year]:
 *
 * - liga: uma por `GameCompetition` League, com os clubes do país cujo
 *   `division` é o tier dela, em ordem de id; o formato do catálogo dá os
 *   turnos (`RoundRobin(legs = 2)` = turno e returno). Encaixe na janela do
 *   `span` e com a pausa da competição (o Brasileirão segue nas datas FIFA; as
 *   europeias param). Liga com menos de 2 clubes não tem calendário;
 * - copa nacional: cada encaixe dela reserva TODOS os clubes das ligas do
 *   país (os confrontos das fases seguintes só existem depois dos resultados).
 *   Na prática a copa nunca divide encaixe com a liga do país — é o que
 *   garante que o sorteio de qualquer fase cabe no encaixe reservado. Janela:
 *   do 2º ao penúltimo mês da temporada ([cupWindow]), para o campeão sair
 *   antes da virada do país.
 *
 * Não caber no calendário é erro de CONFIGURAÇÃO do mundo (o catálogo pede
 * mais rodadas do que a janela tem), não desfecho de jogo — daí o `error`.
 */
fun countryPlan(
    competitions: List<GameCompetition>,
    year: Int,
    clubs: Collection<Club>,
    cups: Map<CompetitionId, Cup>,
    slots: Map<CompetitionId, Int>,
    fifaWindows: List<FifaWindow> = interimFifaWindows(year..year + 1),
    continentals: Map<CompetitionId, ContinentalCup> = emptyMap(),
): SeasonPlan {
    val countryClubs = clubs.sortedBy { it.id.value }
    // `buildList` monta a lista com `add` num bloco e devolve a versão imutável.
    val entries: List<Pair<SeasonCompetition, List<Set<ClubId>>>> = buildList {
        for (game in competitions) {
            // `when` exaustivo sobre o papel da competição: um papel novo
            // obriga a decidir aqui como ele entra no plano.
            when (val kind = game.kind) {
                is CompetitionKind.League -> {
                    val ids = countryClubs.filter { it.division == kind.tier }.map { it.id }
                    if (ids.size < 2) continue
                    val rounds = leagueRounds(game, ids)
                    val league = SeasonCompetition.League(game, game.seasonOf(year), slots.getValue(game.id), rounds)
                    add(league to rounds.map { round -> round.flatMapTo(HashSet()) { listOf(it.home, it.away) } })
                }
                CompetitionKind.NationalCup -> {
                    val cup = cups[game.id] ?: continue
                    val everyone = countryClubs.mapTo(HashSet()) { it.id }
                    val (start, end) = cupWindow(game.span, year)
                    val season = CompetitionSeason(game.competition, year, start, end, game.pausesOnFifaWindows)
                    add(SeasonCompetition.NationalCup(game, season, cup) to List(cupRounds(cup)) { everyone })
                }
                // Continental hospedada por este país (issue #132): agendada JUNTO
                // com as competições dele, então nenhum clube brasileiro fica com
                // dois jogos no encaixe. Cada rodada reserva todos os inscritos —
                // inclusive os estrangeiros, que só jogam ela. Só no meio de
                // semana, na mesma janela da copa nacional (fevereiro a novembro).
                CompetitionKind.Continental -> {
                    val edition = continentals[game.id]?.takeIf { it.season == year } ?: continue
                    val everyone = edition.clubs.mapTo(HashSet()) { it.id }
                    val (start, end) = cupWindow(game.span, year)
                    val season = CompetitionSeason(game.competition, year, start, end, game.pausesOnFifaWindows)
                    add(SeasonCompetition.Continental(game, season, edition) to List(edition.calendarRounds) { everyone })
                }
            }
        }
    }
    if (entries.isEmpty()) return SeasonPlan.EMPTY

    val requests = entries.map { (competition, rounds) ->
        when (competition) {
            is SeasonCompetition.Continental -> ScheduleRequest(competition.season, rounds, slots = setOf(Slot.MIDWEEK))
            is SeasonCompetition.League, is SeasonCompetition.NationalCup -> ScheduleRequest(competition.season, rounds)
        }
    }
    val schedule = when (val result = scheduleSeasons(requests, fifaWindows)) {
        is ScheduleResult.Scheduled -> result.schedule
        is ScheduleResult.NoRoomLeft -> error("Temporada $year de ${competitions.first().country.name} não cabe no calendário: $result")
    }
    return SeasonPlan(entries.associate { (competition, _) -> competition.game.id to competition }, schedule)
}

/**
 * As rodadas de pareamentos da liga [game] entre [clubs], no formato do
 * catálogo. Só pontos corridos é liga; os demais formatos são de copa ou
 * continental — numa competição de papel `League` são erro de configuração.
 */
private fun leagueRounds(game: GameCompetition, clubs: List<ClubId>): List<List<Pairing>> =
    when (val format = game.competition.format) {
        is CompetitionFormat.RoundRobin -> roundRobinSchedule(clubs, format.legs)
        is CompetitionFormat.Knockout,
        is CompetitionFormat.GroupsThenKnockout,
        is CompetitionFormat.SwissLeague,
        -> error("Liga ${game.id.value} com formato que não é de pontos corridos: $format")
    }

/**
 * Janela da copa nacional dentro da temporada do país: da 1ª SEGUNDA do 2º mês
 * ao fim do penúltimo mês (no Brasil, fevereiro a novembro) — começa depois da
 * 1ª rodada da liga e termina antes da última, para o campeão chegar à virada.
 *
 * Começa numa segunda para a semana inicial ter os dois encaixes e a copa
 * estrear na quarta que prefere. `TemporalAdjusters.nextOrSame(MONDAY)`
 * devolve o próprio dia se já for segunda.
 */
private fun cupWindow(span: SeasonSpan, year: Int): Pair<LocalDate, LocalDate> {
    val start = span.start(year).plusMonths(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
    val end = span.end(year).withDayOfMonth(1).minusDays(1)
    return start to end
}
