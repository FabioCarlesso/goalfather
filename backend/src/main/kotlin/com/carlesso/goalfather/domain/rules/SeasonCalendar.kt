package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionSeason
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.FifaWindow
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.NextMatch
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.SeasonCompetitions
import com.carlesso.goalfather.domain.model.SeasonSpan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

/**
 * Calendário da temporada do mundo atual do jogo (issue #125): a liga de cada
 * divisão + a copa, encaixadas na semana pelo agendador genérico
 * ([scheduleSeasons]). Função PURA dos clubes (ids e divisões não mudam no
 * meio da temporada) e da copa — por isso o calendário nunca é persistido: o
 * serviço o recalcula a cada encaixe encerrado.
 *
 * Substitui a sequência de "datas" da issue #61 (liga intercalada com copa,
 * uma por clique). O que se mantém: o primeiro encaixe é de liga e as fases da
 * copa se espalham entre as rodadas. O campeão da copa sempre chega à virada
 * porque ela só acontece quando o plano não tem mais NENHUM encaixe — de
 * nenhuma competição. Que a copa acabe antes da última rodada da liga é
 * consequência dos parâmetros do seed (janela fev–nov, 4 fases), não uma
 * garantia do agendador.
 */

/** O que cada competição do mundo atual é — o que o calendário precisa para gerar os jogos do encaixe. */
sealed interface SeasonCompetition {
    val season: CompetitionSeason

    /** A liga de pontos corridos de uma [division]. */
    data class DivisionLeague(override val season: CompetitionSeason, val division: Division) : SeasonCompetition

    /** A copa mata-mata da temporada (issue #61). */
    data class SeasonCup(override val season: CompetitionSeason) : SeasonCompetition
}

/**
 * As competições da temporada + o calendário montado. [roundAt] transforma um
 * encaixe nos jogos de verdade.
 */
data class SeasonPlan(
    val season: Int,
    val competitions: Map<CompetitionId, SeasonCompetition>,
    val schedule: Schedule,
) {
    val first: Matchday? get() = schedule.first

    fun nextAfter(matchday: Matchday): Matchday? = schedule.nextAfter(matchday)

    /**
     * Os jogos do encaixe [matchday]: a rodada de cada liga marcada nele e,
     * se a copa estiver nele, os confrontos em aberto da fase corrente. O
     * `when` é exaustivo sobre [SeasonCompetition]: uma terceira competição
     * vira erro de compilação aqui, não um encaixe jogado como liga.
     */
    fun roundAt(matchday: Matchday, clubs: List<Club>, cup: Cup?): Round {
        val byDivision = clubs.groupBy { it.division }
        val matches = schedule.at(matchday).flatMap { scheduled ->
            when (val competition = competitions.getValue(scheduled.competition)) {
                is SeasonCompetition.DivisionLeague ->
                    leagueMatches(matchday, scheduled.round, competition.division, byDivision[competition.division].orEmpty())
                // O calendário só reserva encaixes de copa quando ela existe; um
                // `null` aqui é bug de quem montou o plano, não estado de jogo.
                is SeasonCompetition.SeasonCup ->
                    cupMatches(matchday, requireNotNull(cup) { "Encaixe $matchday reservado para a copa, mas não há copa em $season" }, clubs)
            }
        }
        return Round(matchday = matchday, season = season, status = RoundStatus.Scheduled, matches = matches)
    }

    /**
     * O próximo jogo de [clubId] DEPOIS do encaixe [after], como o calendário o
     * enxerga hoje. Jogos de liga são conhecidos de antemão (Berger); da copa
     * só se conhece a fase corrente — a seguinte depende de resultados —, então
     * só o PRIMEIRO encaixe de copa adiante entra na busca, e nenhum quando a
     * fase corrente ainda está em jogo ([cupStageInPlay]).
     */
    fun nextMatchOf(
        clubId: ClubId,
        after: Matchday,
        clubs: List<Club>,
        cup: Cup?,
        cupStageInPlay: Boolean = false,
    ): NextMatch? {
        var cupKnown = !cupStageInPlay
        for (matchday in schedule.matchdays.filter { it > after }) {
            val hasCup = schedule.at(matchday).any { competitions[it.competition] is SeasonCompetition.SeasonCup }
            if (hasCup && !cupKnown) continue
            val match = roundAt(matchday, clubs, cup).matches.firstOrNull { it.involves(clubId) }
            if (match != null) return NextMatch(matchday, match)
            if (hasCup) cupKnown = false
        }
        return null
    }
}

/**
 * Janela da copa do mundo atual: fevereiro a novembro, dentro da temporada
 * brasileira — começa depois da 1ª rodada da liga e termina antes da última,
 * para o campeão chegar à virada.
 *
 * Começa na 1ª SEGUNDA de fevereiro: assim a semana inicial da janela tem os
 * dois encaixes, e a copa estreia na quarta que prefere. `TemporalAdjusters`
 * é o "ajuste de data" do `java.time` — `nextOrSame(MONDAY)` devolve o próprio
 * dia se já for segunda.
 */
private fun cupWindow(season: Int): Pair<LocalDate, LocalDate> =
    LocalDate.of(season, Month.FEBRUARY, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)) to
        LocalDate.of(season, Month.NOVEMBER, 30)

/**
 * Monta o plano da temporada [season] para [clubs] + [cup].
 *
 * - Liga: uma competição por divisão, na janela do país dos clubes (ano civil
 *   no Brasil) e SEM pausa nas datas FIFA — o Brasileirão segue jogando.
 * - Copa: pausa nas datas FIFA; cada fase reserva TODOS os clubes da liga,
 *   porque os confrontos das fases seguintes só existem depois dos resultados.
 *   Na prática a copa nunca divide encaixe com a liga — que é o que garante que
 *   o sorteio de qualquer fase cabe no encaixe reservado.
 *
 * Uma liga que não cabe no calendário é erro de configuração do mundo (o seed
 * tem 5 rodadas num ano de 104 encaixes), não desfecho de jogo — daí o `error`.
 */
fun seasonPlan(
    season: Int,
    clubs: List<Club>,
    cup: Cup?,
    fifaWindows: List<FifaWindow> = interimFifaWindows(season..season + 1),
): SeasonPlan {
    require(canScheduleSeason(clubs)) { "Liga sem calendário possível: ${clubs.size} clubes" }
    val byDivision = clubs.groupBy { it.division }.toSortedMap()

    val leagues = byDivision.map { (division, divisionClubs) ->
        val country = divisionClubs.first().country
        val competition = Competition(SeasonCompetitions.leagueId(division), SeasonCompetitions.leagueName(division), LEAGUE_FORMAT)
        val competitionSeason = CompetitionSeason.of(
            competition,
            SeasonSpan.of(country.confederation),
            season,
            pausesOnFifaWindows = false,
        )
        val ids = divisionClubs.map { it.id }
        val rounds = (1..turnRounds(ids.size)).map { round ->
            roundRobinMatchday(ids, round).flatMapTo(HashSet()) { listOf(it.home, it.away) }
        }
        SeasonCompetition.DivisionLeague(competitionSeason, division) to rounds
    }

    val cupEntry = cup?.let {
        val (start, end) = cupWindow(season)
        val competition = Competition(SeasonCompetitions.CUP_ID, SeasonCompetitions.CUP_NAME, cupFormat(clubs.size))
        val competitionSeason = CompetitionSeason(competition, season, start, end, pausesOnFifaWindows = true)
        val everyone = clubs.mapTo(HashSet()) { club -> club.id }
        SeasonCompetition.SeasonCup(competitionSeason) to it.phases.map { everyone }
    }

    val entries = leagues + listOfNotNull(cupEntry)
    val requests = entries.map { (competition, rounds) -> ScheduleRequest(competition.season, rounds) }
    val schedule = when (val result = scheduleSeasons(requests, fifaWindows)) {
        is ScheduleResult.Scheduled -> result.schedule
        is ScheduleResult.NoRoomLeft -> error("Temporada $season não cabe no calendário: $result")
    }
    return SeasonPlan(
        season = season,
        competitions = entries.associate { (competition, _) -> competition.season.competition.id to competition },
        schedule = schedule,
    )
}

