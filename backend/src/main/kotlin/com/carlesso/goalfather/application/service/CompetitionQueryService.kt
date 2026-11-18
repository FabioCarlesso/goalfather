package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.CompetitionLookup
import com.carlesso.goalfather.application.port.`in`.CompetitionMatchday
import com.carlesso.goalfather.application.port.`in`.CompetitionOverview
import com.carlesso.goalfather.application.port.`in`.CompetitionQueryUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.application.port.out.CupRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.ClubSchedule
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.CompetitionScorer
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.Slot
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.leagueOf
import com.carlesso.goalfather.domain.rules.SeasonPlan
import com.carlesso.goalfather.domain.rules.WorldSeasons
import com.carlesso.goalfather.domain.rules.clubScheduleWeeks
import com.carlesso.goalfather.domain.rules.interimFifaWindows
import com.carlesso.goalfather.domain.rules.topScorers
import com.carlesso.goalfather.domain.rules.visibleRoundsAfter
import com.carlesso.goalfather.domain.rules.worldPlan
import com.carlesso.goalfather.domain.rules.worldSeasons

/**
 * Implementação de [CompetitionQueryUseCase] (issue #133). Lê o catálogo, os
 * clubes, as tabelas, as copas e as rodadas gravadas, e monta o resto com as
 * MESMAS regras que andam o relógio (`worldPlan`, a visibilidade do
 * `nextMatchOf`): o que a tela mostra como "próximo" é o que vai ser jogado.
 *
 * Nada aqui grava. Cada chamada lê o mundo de novo ([World]) — o plano sai em
 * poucos milissegundos (medido na #131) e não vale um cache que precisaria
 * ser invalidado a cada encaixe.
 */
class CompetitionQueryService(
    private val catalogRepo: CompetitionCatalogRepository,
    private val clubRepo: ClubRepository,
    private val leagueRepo: LeagueRepository,
    private val cupRepo: CupRepository,
) : CompetitionQueryUseCase {

    override suspend fun competitions(): List<CompetitionOverview> {
        val world = loadWorld()
        return world.catalog
            .sortedWith(WORLD_ORDER)
            .map { world.overviewOf(it) }
    }

    override suspend fun competition(id: CompetitionId): CompetitionLookup<CompetitionOverview> =
        withCompetition(id) { world, game -> CompetitionLookup.Found(world.overviewOf(game)) }

    override suspend fun standings(id: CompetitionId): CompetitionLookup<Standings> =
        withCompetition(id) { _, _ ->
            // `?.let(::Found) ?: NotAvailable`: com tabela, embrulha; sem, o
            // segundo "não" — a competição existe mas não tem tabela.
            leagueRepo.currentStandings().firstOrNull { it.competitionId == id }
                ?.let { CompetitionLookup.Found(it) }
                ?: CompetitionLookup.NotAvailable
        }

    override suspend fun scorers(id: CompetitionId): CompetitionLookup<List<CompetitionScorer>> =
        withCompetition(id) { world, game -> CompetitionLookup.Found(world.clubsOf(game).topScorers(TOP_SCORERS)) }

    override suspend fun bracket(id: CompetitionId): CompetitionLookup<Cup> =
        withCompetition(id) { world, _ ->
            world.cups[id]?.let { CompetitionLookup.Found(it) } ?: CompetitionLookup.NotAvailable
        }

    override suspend fun matches(id: CompetitionId): CompetitionLookup<List<CompetitionMatchday>> =
        withCompetition(id) { world, _ ->
            val current = leagueRepo.findLatest() ?: return@withCompetition CompetitionLookup.Found(emptyList())
            val ahead = world.plan.visibleRoundsAfter(current.matchday, world.clubs, current.knockoutsInPlay())
                .associateBy { it.matchday }
            val matchdays = world.plan.schedule.rounds
                .filter { it.competition == id }
                .map { it.matchday }
                .distinct()
                .sorted()
            val days = matchdays.map { matchday ->
                val matches = when {
                    matchday == current.matchday -> current.matches
                    matchday > current.matchday -> ahead[matchday]?.matches.orEmpty()
                    // Encaixe já jogado: os placares gravados. Sem a rodada
                    // gravada (save que nasceu no meio da temporada), os jogos
                    // de liga do plano, sem placar; mata-mata passado não se
                    // reconstrói.
                    else -> leagueRepo.findRound(matchday)?.matches
                        ?: world.plan.roundAt(matchday, world.clubs).matches.filterNot { it.isKnockout }
                }
                CompetitionMatchday(matchday, matches.filter { it.competition == id })
            }
            CompetitionLookup.Found(days)
        }

    override suspend fun clubSchedule(clubId: ClubId, weeks: Int): ClubSchedule? {
        clubRepo.findById(clubId) ?: return null
        val current = leagueRepo.findLatest() ?: return ClubSchedule(clubId, current = null, weeks = emptyList())
        val world = loadWorld()
        val from = current.matchday.week
        val last = from + (weeks - 1)

        // Encaixes da semana corrente já jogados (o meio de semana, quando o
        // corrente é o domingo), o corrente e os adiante até a última semana.
        val earlier = Slot.entries
            .map { Matchday(from, it) }
            .filter { it < current.matchday }
            .mapNotNull { leagueRepo.findRound(it) }
        val ahead = world.plan.visibleRoundsAfter(current.matchday, world.clubs, current.knockoutsInPlay())
            .takeWhile { it.matchday.week <= last }
        val fifaWindows = interimFifaWindows(from.monday.year..last.sunday.year)

        return ClubSchedule(
            clubId = clubId,
            current = current.matchday,
            weeks = clubScheduleWeeks(clubId, from, weeks, earlier + current + ahead, fifaWindows),
        )
    }

    /**
     * Roda [block] com a competição [id] do catálogo, ou devolve
     * `UnknownCompetition`. O tipo da lambda é `suspend (…) -> …`: o bloco
     * pode chamar os ports (funções `suspend`) de dentro dela.
     */
    private suspend fun <T> withCompetition(
        id: CompetitionId,
        block: suspend (World, GameCompetition) -> CompetitionLookup<T>,
    ): CompetitionLookup<T> {
        val world = loadWorld()
        val game = world.catalog.firstOrNull { it.id == id } ?: return CompetitionLookup.UnknownCompetition
        return block(world, game)
    }

    private suspend fun loadWorld(): World {
        val catalog = catalogRepo.findAll()
        val seasons = leagueRepo.currentStandings().worldSeasons()
        return World(catalog, clubRepo.findAll(), seasons, cupRepo.currentCups(catalog, seasons))
    }

    /**
     * O mundo lido dos ports numa chamada. [plan] é `by lazy`: só as leituras
     * de jogos e calendário pagam a montagem do plano; a lista de competições
     * não precisa dele.
     */
    private class World(
        val catalog: List<GameCompetition>,
        val clubs: List<Club>,
        val seasons: WorldSeasons,
        val cups: Map<CompetitionId, Cup>,
    ) {
        val plan: SeasonPlan by lazy { worldPlan(catalog, clubs, seasons, cups) }

        fun overviewOf(game: GameCompetition): CompetitionOverview =
            CompetitionOverview(game, seasons[game.country.code], clubsOf(game).size)

        /**
         * Os clubes que disputam [game] na temporada corrente. `when`
         * exaustivo sobre o papel: liga = os clubes cuja liga (país +
         * divisão) é ela; copa nacional = os inscritos na copa sorteada, ou,
         * antes do sorteio, os clubes das ligas do país.
         */
        fun clubsOf(game: GameCompetition): List<Club> = when (game.kind) {
            is CompetitionKind.League -> clubs.filter { catalog.leagueOf(it)?.id == game.id }
            CompetitionKind.NationalCup -> {
                val entrants = cups[game.id]?.phases?.flatMapTo(HashSet()) { phase -> phase.entrants.map { it.id } }
                clubs.filter { club ->
                    entrants?.contains(club.id)
                        ?: (club.country.code == game.country.code && catalog.leagueOf(club) != null)
                }
            }
        }
    }

    private companion object {
        const val TOP_SCORERS = 20

        /** O país "da casa" vem primeiro, como nas tabelas (`inWorldOrder`). */
        const val HOME_COUNTRY = "BRA"

        /**
         * Ordem da lista: Brasil primeiro, os demais pelo código; no país, as
         * ligas da elite para baixo e depois as copas. `compareBy` encadeado
         * com `thenBy` é o `Comparator.comparing(...).thenComparing(...)` do
         * Java sem a cerimônia.
         */
        val WORLD_ORDER: Comparator<GameCompetition> =
            compareBy<GameCompetition> { it.country.code.value != HOME_COUNTRY }
                .thenBy { it.country.code.value }
                .thenBy { game ->
                    when (val kind = game.kind) {
                        is CompetitionKind.League -> kind.tier.value
                        CompetitionKind.NationalCup -> Int.MAX_VALUE
                    }
                }
    }
}

/**
 * Os mata-matas com fase em jogo no encaixe [this]: a fase seguinte deles nem
 * foi sorteada, então nenhum encaixe adiante tem confronto conhecido. Mesmo
 * critério do `CalendarService`.
 */
private fun Round.knockoutsInPlay(): Set<CompetitionId> =
    if (status == RoundStatus.Finished) emptySet()
    else matches.filter { it.isKnockout }.mapTo(HashSet()) { it.competition }
