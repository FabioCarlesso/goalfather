package com.carlesso.goalfather.test

import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.KnockoutStage
import com.carlesso.goalfather.domain.model.Legs
import com.carlesso.goalfather.domain.model.PenaltyScore
import com.carlesso.goalfather.domain.model.PlayedMatch
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.Tiebreaker
import com.carlesso.goalfather.domain.model.WorldQualificationRules
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.rules.drawCup
import com.carlesso.goalfather.domain.rules.squadRating
import kotlin.random.Random

/**
 * Catálogo e tabelas de teste do mundo do save (issue #131). Os ids são os do
 * pack (`bra-serie-a`…), os mesmos que a frente do seed grava.
 */

internal val SERIE_A: CompetitionId = WorldQualificationRules.BRA_SERIE_A
internal val SERIE_B: CompetitionId = WorldQualificationRules.BRA_SERIE_B
internal val COPA_DO_BRASIL: CompetitionId = WorldQualificationRules.BRA_COPA_DO_BRASIL
internal const val SERIE_A_NAME = "Campeonato Brasileiro Série A"
internal const val SERIE_B_NAME = "Campeonato Brasileiro Série B"

/** Brasileirão: pontos, vitórias, saldo, gols pró, confronto direto, vermelhos, amarelos, sorteio. */
internal val BRASILEIRAO_TIEBREAKERS = listOf(
    Tiebreaker.POINTS, Tiebreaker.WINS, Tiebreaker.GOAL_DIFFERENCE, Tiebreaker.GOALS_FOR,
    Tiebreaker.HEAD_TO_HEAD, Tiebreaker.FEWER_RED_CARDS, Tiebreaker.FEWER_YELLOW_CARDS, Tiebreaker.DRAWING_OF_LOTS,
)
private val PREMIER_TIEBREAKERS = listOf(
    Tiebreaker.POINTS, Tiebreaker.GOAL_DIFFERENCE, Tiebreaker.GOALS_FOR, Tiebreaker.HEAD_TO_HEAD, Tiebreaker.DRAWING_OF_LOTS,
)
internal val LA_LIGA_TIEBREAKERS = listOf(
    Tiebreaker.POINTS, Tiebreaker.HEAD_TO_HEAD, Tiebreaker.HEAD_TO_HEAD_GOAL_DIFFERENCE,
    Tiebreaker.GOAL_DIFFERENCE, Tiebreaker.GOALS_FOR, Tiebreaker.DRAWING_OF_LOTS,
)

/** Uma liga do catálogo. */
internal fun leagueCompetition(
    id: CompetitionId,
    name: String,
    country: Country,
    tier: Int,
    legs: Int = 2,
    tiebreakers: List<Tiebreaker> = Competition.DEFAULT_TIEBREAKERS,
    foreignPlayerLimit: Int? = null,
    span: SeasonSpan = SeasonSpan.of(country.confederation),
    pausesOnFifaWindows: Boolean = span == SeasonSpan.AUGUST_TO_MAY,
): GameCompetition = GameCompetition(
    competition = Competition(id, name, CompetitionFormat.RoundRobin(legs), tiebreakers, foreignPlayerLimit = foreignPlayerLimit),
    country = country,
    kind = CompetitionKind.League(Division(tier)),
    span = span,
    pausesOnFifaWindows = pausesOnFifaWindows,
)

/**
 * O Brasil dos testes de serviço: Série A, Série B e Copa do Brasil, com o
 * limite de 9 estrangeiros. [legs] = 1 mantém os testes escritos para a liga
 * de turno único (o formato vem do catálogo — o teste escolhe o seu).
 */
internal fun brazilCatalog(legs: Int = 1, cupClubs: Int = 12): List<GameCompetition> = listOf(
    leagueCompetition(SERIE_A, SERIE_A_NAME, Countries.BRAZIL, 1, legs, BRASILEIRAO_TIEBREAKERS, 9),
    leagueCompetition(SERIE_B, SERIE_B_NAME, Countries.BRAZIL, 2, legs, BRASILEIRAO_TIEBREAKERS, 9),
    GameCompetition(
        competition = Competition(COPA_DO_BRASIL, "Copa do Brasil", cupFormat(cupClubs), foreignPlayerLimit = 9),
        country = Countries.BRAZIL,
        kind = CompetitionKind.NationalCup,
        span = SeasonSpan.CALENDAR_YEAR,
        pausesOnFifaWindows = true,
    ),
)

/** A Copa do Brasil 2026 do pack: 69 jogos, Série A entrando na 5ª fase. */
internal val COPA_DO_BRASIL_2026 = CompetitionFormat.Knockout(
    listOf(
        KnockoutStage("Quarta fase", 16, Legs.SINGLE, 16),
        KnockoutStage("Quinta fase", 32, Legs.TWO_LEGGED, 24),
        KnockoutStage("Oitavas de final", 16, Legs.TWO_LEGGED),
        KnockoutStage("Quartas de final", 8, Legs.TWO_LEGGED),
        KnockoutStage("Semifinal", 4, Legs.TWO_LEGGED),
        KnockoutStage("Final", 2, Legs.SINGLE),
    ),
)

/**
 * O catálogo do MVP (issue #131) com os ids e os desempates do pack: Série A
 * e B (ano civil, sem pausa FIFA, 9 estrangeiros), Copa do Brasil e as cinco
 * grandes europeias (ago–mai, pausam nas datas FIFA, sem limite).
 */
internal fun mvpCatalog(): List<GameCompetition> = listOf(
    leagueCompetition(SERIE_A, SERIE_A_NAME, Countries.BRAZIL, 1, 2, BRASILEIRAO_TIEBREAKERS, 9),
    leagueCompetition(SERIE_B, SERIE_B_NAME, Countries.BRAZIL, 2, 2, BRASILEIRAO_TIEBREAKERS, 9),
    GameCompetition(
        competition = Competition(COPA_DO_BRASIL, "Copa do Brasil", COPA_DO_BRASIL_2026, foreignPlayerLimit = 9),
        country = Countries.BRAZIL,
        kind = CompetitionKind.NationalCup,
        span = SeasonSpan.CALENDAR_YEAR,
        pausesOnFifaWindows = true,
    ),
    leagueCompetition(CompetitionId("eng-premier-league"), "Premier League", Countries.ENGLAND, 1, tiebreakers = PREMIER_TIEBREAKERS),
    leagueCompetition(CompetitionId("esp-la-liga"), "La Liga", Countries.SPAIN, 1, tiebreakers = LA_LIGA_TIEBREAKERS),
    leagueCompetition(CompetitionId("ita-serie-a"), "Serie A", Countries.ITALY, 1, tiebreakers = LA_LIGA_TIEBREAKERS),
    leagueCompetition(CompetitionId("ger-bundesliga"), "Bundesliga", Countries.GERMANY, 1, tiebreakers = PREMIER_TIEBREAKERS),
    leagueCompetition(CompetitionId("fra-ligue-1"), "Ligue 1", Countries.FRANCE, 1, tiebreakers = PREMIER_TIEBREAKERS),
)

/** Tamanho real de cada liga do MVP: 20/20 no Brasil; 20/20/20/18/18 na Europa. */
internal val MVP_LEAGUE_SIZES: List<Triple<Country, Int, Int>> = listOf(
    Triple(Countries.BRAZIL, 1, 20),
    Triple(Countries.BRAZIL, 2, 20),
    Triple(Countries.ENGLAND, 1, 20),
    Triple(Countries.SPAIN, 1, 20),
    Triple(Countries.ITALY, 1, 20),
    Triple(Countries.GERMANY, 1, 18),
    Triple(Countries.FRANCE, 1, 18),
)

/**
 * Clubes sintéticos do MVP (136), ids contíguos a partir de 1, cada um no seu
 * país e divisão, com elenco de 11 de força decrescente dentro da liga.
 */
internal fun mvpClubs(): List<Club> {
    var next = 1L
    return MVP_LEAGUE_SIZES.flatMap { (country, tier, size) ->
        (1..size).map { i ->
            val id = next++
            makeClub(id = id, name = "${country.code.value}$tier-$i", overall = 85 - i, division = Division(tier))
                .copy(country = country)
        }
    }
}

/** Catálogo em memória para os testes de serviço. */
internal class InMemoryCatalogRepository(var competitions: List<GameCompetition> = brazilCatalog()) :
    CompetitionCatalogRepository {
    override suspend fun findAll(): List<GameCompetition> = competitions

    override suspend fun replaceAll(competitions: List<GameCompetition>) {
        this.competitions = competitions
    }
}

/** A liga brasileira de cada divisão nos testes (1 = Série A, 2 = Série B). */
internal fun brazilLeagueOf(division: Division): CompetitionId = when (division.value) {
    1 -> SERIE_A
    2 -> SERIE_B
    else -> CompetitionId("bra-divisao-${division.value}")
}

internal fun brazilLeagueName(division: Division): String = when (division.value) {
    1 -> SERIE_A_NAME
    2 -> SERIE_B_NAME
    else -> "Divisão ${division.value}"
}

/**
 * Tabela de teste com os parâmetros de sempre (temporada, rodada, linhas,
 * divisão, vagas) e a liga do mundo derivada da divisão — a liga brasileira
 * dela, como o mundo antigo (só Brasil) era.
 */
internal fun testStandings(
    season: Int = 2026,
    round: Int = 0,
    rows: List<StandingRow>,
    division: Division = Division.FIRST,
    promotionSpots: Int = 0,
    relegationSpots: Int = 0,
    competitionId: CompetitionId = brazilLeagueOf(division),
    competitionName: String = brazilLeagueName(division),
    country: Country = Countries.BRAZIL,
    seasonLabel: String = "$season",
    results: List<PlayedMatch> = emptyList(),
): Standings = Standings(
    competitionId = competitionId,
    competitionName = competitionName,
    country = country,
    season = season,
    seasonLabel = seasonLabel,
    round = round,
    rows = rows,
    division = division,
    promotionSpots = promotionSpots,
    relegationSpots = relegationSpots,
    results = results,
)


/**
 * Partida de teste com os parâmetros de [RoundMatch] e a liga brasileira da
 * divisão como competição padrão (o `RoundMatch` não tem mais default de
 * competição desde a issue #131).
 */
internal fun leagueMatch(
    matchId: Long,
    homeClubId: ClubId,
    awayClubId: ClubId,
    homeClubName: String,
    awayClubName: String,
    status: RoundStatus = RoundStatus.Scheduled,
    homeGoals: Int = 0,
    awayGoals: Int = 0,
    minute: Int = 0,
    division: Division = Division.FIRST,
    penalties: PenaltyScore? = null,
    stage: RoundStage = RoundStage.League(1),
    competition: CompetitionId = brazilLeagueOf(division),
    competitionName: String = brazilLeagueName(division),
): RoundMatch = RoundMatch(
    matchId, homeClubId, awayClubId, homeClubName, awayClubName, status, homeGoals, awayGoals, minute,
    division, penalties, stage, competition, competitionName,
)

/**
 * Formato de copa pequeno para os testes de serviço (o antigo `cupFormat` da
 * copa genérica, que saiu do código de produção na #131): jogo único, a menor
 * potência de 2 que comporta os clubes, e quem antes "folgava" ENTRA na 2ª
 * fase — 12 clubes viram 8 na Primeira fase + 4 entrantes nas quartas.
 */
internal fun cupFormat(clubCount: Int): CompetitionFormat.Knockout {
    require(clubCount in 4..32) { "Copa de teste exige de 4 a 32 clubes: $clubCount" }
    // `takeHighestOneBit` (stdlib) — potência de 2 imediatamente abaixo; com
    // `(n - 1)` e `shl 1`, a menor potência de 2 que é >= n.
    val bracket = (clubCount - 1).takeHighestOneBit() shl 1
    val byes = bracket - clubCount
    val sizes = generateSequence(bracket) { (it / 2).takeIf { half -> half >= 2 } }.toList()
    val stages = if (byes == 0) {
        sizes.mapIndexed { i, teams -> KnockoutStage(stageName(teams), teams, Legs.SINGLE, if (i == 0) teams else 0) }
    } else {
        val opening = bracket - 2 * byes
        listOf(KnockoutStage("Primeira fase", opening, Legs.SINGLE, opening)) +
            sizes.drop(1).mapIndexed { i, teams -> KnockoutStage(stageName(teams), teams, Legs.SINGLE, if (i == 0) byes else 0) }
    }
    return CompetitionFormat.Knockout(stages)
}

private fun stageName(teams: Int): String = when (teams) {
    2 -> "Final"
    4 -> "Semifinal"
    8 -> "Quartas de final"
    16 -> "Oitavas de final"
    else -> "Primeira fase"
}

/** Copa do Brasil de teste com [clubs] (formato [cupFormat]), os mais fortes entrando por último. */
internal fun testCup(season: Int, clubs: Collection<Club>, rng: Random): Cup = drawCup(
    season,
    Competition(COPA_DO_BRASIL, "Copa do Brasil", cupFormat(clubs.size), foreignPlayerLimit = 9),
    clubs.sortedWith(compareByDescending<Club> { it.squadRating() }.thenBy { it.id.value }),
    rng,
)
