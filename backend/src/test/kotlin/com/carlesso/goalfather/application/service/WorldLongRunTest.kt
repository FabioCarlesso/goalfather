package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.adapter.out.worlddata.FileWorldDataProvider
import com.carlesso.goalfather.application.port.out.PackLoadResult
import com.carlesso.goalfather.application.port.out.ClubClaimRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.ContinentalRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.application.port.out.PlayerRepository
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.application.port.out.SeasonHistoryRepository
import com.carlesso.goalfather.application.port.out.TransferCompletion
import com.carlesso.goalfather.application.port.out.TransferOfferRepository
import com.carlesso.goalfather.application.seed.dsl.namePoolFor
import com.carlesso.goalfather.application.seed.toInitialWorld
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.ContinentalCup
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.Dismissal
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.OfferId
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.SeasonRecord
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.TrainingFocus
import com.carlesso.goalfather.domain.model.TransferOffer
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.model.leagueOf
import com.carlesso.goalfather.domain.result.ClaimResult
import com.carlesso.goalfather.domain.rules.YOUTH_INTAKE_AGES
import com.carlesso.goalfather.test.InMemoryCatalogRepository
import com.carlesso.goalfather.test.InMemoryCupRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Teste de longo prazo do mundo (issue #139): o pack REAL jogando 10
 * temporadas pelo `PlayRoundService`, na simulação rápida, com fakes em
 * memória dos ports — inclusive um mercado de verdade, para o pool de livres
 * crescer, envelhecer e aposentar.
 *
 * O critério de aceite: o número de jogadores e o overall médio POR LIGA
 * ficam a ±10% do inicial. Ninguém contrata (não há técnico humano), então o
 * equilíbrio é só base × aposentadoria × dispensa da IA.
 *
 * Lento (dezenas de segundos): `runBlocking` e não `runTest`, porque a
 * simulação rápida roda no `Dispatchers.Default` de verdade.
 */
class WorldLongRunTest {

    private val initial = runBlocking { loadWorld() }
    private val catalog = initial.competitions
    private val clubs = FakeClubs(initial.clubs)
    private val league = FakeLeague()
    private val history = FakeHistory()
    private val cups = InMemoryCupRepository()
    private val continentals = FakeContinentals()
    private val market = FakeMarket(initial.market)

    private val service = PlayRoundService(
        clubs, league, NoReadiness, market, market, history, cups, NoClaims, NoOffers,
        InMemoryCatalogRepository(catalog),
        continentalRepo = continentals,
    )

    init {
        val start = initial.start()
        start.standings.forEach { league.tables[it.competitionId to it.season] = it }
        start.cups.forEach { cups.cups[it.season] = it }
        start.continentals.forEach { continentals.editions[it.competitionId to it.season] = it }
        league.rounds[requireNotNull(start.firstRound).matchday] = start.firstRound
    }

    private val leagues: List<GameCompetition> = catalog.filter { it.kind is CompetitionKind.League }

    /** Jogadores e overall médio de cada liga, pelos clubes que estão nela agora. */
    private fun snapshot(): Map<CompetitionId, LeagueSnapshot> = leagues.associate { game ->
        val players = clubs.all().filter { catalog.leagueOf(it)?.id == game.id }.flatMap { it.squad }
        game.id to LeagueSnapshot(players.size, players.map { it.overall }.average())
    }

    private data class LeagueSnapshot(val players: Int, val averageOverall: Double)

    @Test
    fun `dez temporadas mantem tamanho e nivel de cada liga e a base fala a lingua do clube`() = runBlocking {
        val before = snapshot()
        val countries = leagues.map { it.country.code }.toSet()
        val youths = mutableListOf<Pair<ClubId, Player>>()

        // Joga até cada país fechar 10 temporadas.
        var matchdays = 0
        while (countries.any { country -> history.records.count { it.country?.code == country } < SEASONS }) {
            check(matchdays++ < SEASONS * MATCHDAYS_PER_YEAR) { "o mundo não virou 10 vezes em $matchdays encaixes" }
            val round = assertNotNull(league.findLatest(), "o mundo ficou sem encaixe")
            league.startRound(round.matchday)
            service.stream(round.matchday).toList()
                .filterIsInstance<RoundEvent.SeasonFinished>()
                .forEach { turn -> youths += turn.youthPromotions.map { it.clubId to it.player } }
        }

        val after = snapshot()
        val report = leagues.joinToString("\n") { g ->
            val b = before.getValue(g.id)
            val a = after.getValue(g.id)
            "%-22s jogadores %4d → %4d   overall %.1f → %.1f".format(g.id.value, b.players, a.players, b.averageOverall, a.averageOverall)
        }
        println("$report\npool de livres: ${initial.market.size} → ${market.entries.size}; garotos da base: ${youths.size}")

        for (game in leagues) {
            val b = before.getValue(game.id)
            val a = after.getValue(game.id)
            assertTrue(a.players.toDouble() in b.players * 0.9..b.players * 1.1, "jogadores de ${game.id.value} fora de ±10%:\n$report")
            assertTrue(
                a.averageOverall in b.averageOverall * 0.9..b.averageOverall * 1.1,
                "overall médio de ${game.id.value} fora de ±10%:\n$report",
            )
        }

        // O pool não incha: os dispensados que ninguém contrata se aposentam.
        val inSquads = clubs.all().sumOf { it.squad.size }
        assertTrue(market.entries.size <= inSquads / 4, "pool de ${market.entries.size} livres para $inSquads em elencos")

        // Nacionalidade e nome do país do clube: o sobrenome sai da lista do país.
        val clubById = initial.clubs.associateBy { it.id }
        assertTrue(youths.isNotEmpty())
        for ((clubId, youth) in youths) {
            val country = clubById.getValue(clubId).country
            assertTrue(youth.nationality == country, "${youth.name} (${youth.nationality.code}) num clube de ${country.code}")
            assertTrue(youth.age in YOUTH_INTAKE_AGES)
            assertTrue(youth.name.substringAfterLast(' ') in surnamesOf(country.code), "${youth.name} não é nome de ${country.code}")
        }
    }

    /**
     * Sobrenomes que o pool do país produz: 1.000 nomes distintos cobrem as
     * listas de sobrenomes (35 a 58 cada). Mais que as combinações possíveis
     * (35 × 35 = 1.225) o `take` sobre nomes DISTINTOS giraria para sempre.
     */
    private val surnameCache = mutableMapOf<CountryCode, Set<String>>()
    private fun surnamesOf(code: CountryCode): Set<String> = surnameCache.getOrPut(code) {
        namePoolFor(code.value).names(Random(1), emptySet()).take(1_000).map { it.substringAfterLast(' ') }.toSet()
    }

    private companion object {
        const val SEASONS = 10

        /** Um ano tem ~104 encaixes; folga para 10 anos e meio. */
        const val MATCHDAYS_PER_YEAR = 115

        suspend fun loadWorld() = when (val result = FileWorldDataProvider(Path.of("../data/world/v1")).load()) {
            is PackLoadResult.Loaded -> result.pack.toInitialWorld()
            else -> error("pack inválido: $result")
        }
    }

    // ─── Fakes em memória ───────────────────────────────────────────────────

    /** Mercado + jogadores livres num lugar só: os dois ports veem o mesmo pool. */
    private class FakeMarket(initial: List<MarketEntry>) : MarketRepository, PlayerRepository {
        val entries = initial.associateBy { it.player.id }.toMutableMap()
        override suspend fun findAll(): List<MarketEntry> = entries.values.sortedBy { it.player.id.value }
        override suspend fun findEntry(playerId: PlayerId): MarketEntry? = entries[playerId]
        override suspend fun add(entry: MarketEntry) {
            entries[entry.player.id] = entry
        }
        override suspend fun updateAll(entries: List<MarketEntry>) {
            entries.forEach { e -> this.entries.computeIfPresent(e.player.id) { _, old -> old.copy(price = e.price, seasonsWithoutClub = e.seasonsWithoutClub) } }
        }
        override suspend fun claim(playerId: PlayerId): Boolean = entries.remove(playerId) != null
        override suspend fun saveFreeAgents(players: List<Player>) {
            players.forEach { p -> entries.computeIfPresent(p.id) { _, old -> old.copy(player = p) } }
        }
        override suspend fun deleteAll(ids: List<PlayerId>) {
            ids.forEach { entries.remove(it) }
        }
    }

    private class FakeClubs(initial: List<Club>) : ClubRepository {
        private val clubs = initial.associateBy { it.id }.toMutableMap()
        fun all(): List<Club> = clubs.values.sortedBy { it.id.value }
        override suspend fun findById(id: ClubId): Club? = clubs[id]
        override suspend fun findAll(): List<Club> = all()
        override suspend fun findAvailable(): List<Club> = all().filter { it.ownerId == null }
        override suspend fun save(club: Club): ClubSaveResult {
            clubs[club.id] = club
            return ClubSaveResult.Saved(club)
        }
        override suspend fun updateTicketPrice(id: ClubId, ticketPriceCents: Money): Club? = null
        override suspend fun updateTrainingFocus(id: ClubId, focus: TrainingFocus): Club? = null
    }

    private class FakeLeague : LeagueRepository {
        val rounds = sortedMapOf<Matchday, Round>()
        val tables = mutableMapOf<Pair<CompetitionId, Int>, Standings>()
        override suspend fun findRound(matchday: Matchday): Round? = rounds[matchday]
        override suspend fun findLatest(): Round? = rounds.values.lastOrNull()
        override suspend fun currentStandings(): List<Standings> =
            tables.values.groupBy { it.competitionId }.values.map { all -> all.maxBy { it.season } }
        override suspend fun findStandings(season: Int): List<Standings> = tables.values.filter { it.season == season }
        override suspend fun saveRound(round: Round) {
            rounds[round.matchday] = round
        }
        override suspend fun saveStandings(standings: Standings) {
            tables[standings.competitionId to standings.season] = standings
        }
        override suspend fun startRound(matchday: Matchday): Boolean {
            val round = rounds[matchday] ?: return false
            if (round.status != RoundStatus.Scheduled) return false
            rounds[matchday] = round.copy(status = RoundStatus.InProgress)
            return true
        }
        override suspend fun finishRound(round: Round): Boolean {
            if (rounds[round.matchday]?.status != RoundStatus.InProgress) return false
            rounds[round.matchday] = round
            return true
        }
    }

    private class FakeHistory : SeasonHistoryRepository {
        val records = mutableListOf<SeasonRecord>()
        override suspend fun findAll(): List<SeasonRecord> = records
        override suspend fun findBySeason(season: Int, country: CountryCode): SeasonRecord? =
            records.firstOrNull { it.season == season && it.country?.code == country }
        override suspend fun append(record: SeasonRecord): Boolean {
            if (findBySeason(record.season, requireNotNull(record.country).code) != null) return false
            records += record
            return true
        }
    }

    private class FakeContinentals : ContinentalRepository {
        val editions = mutableMapOf<Pair<CompetitionId, Int>, ContinentalCup>()
        override suspend fun find(competition: CompetitionId, season: Int): ContinentalCup? = editions[competition to season]
        override suspend fun findLatest(competition: CompetitionId): ContinentalCup? =
            editions.filterKeys { it.first == competition }.maxByOrNull { it.key.second }?.value
        override suspend fun save(edition: ContinentalCup) {
            editions[edition.competitionId to edition.season] = edition
        }
    }

    private object NoReadiness : RoundReadinessRepository {
        override suspend fun markReady(matchday: Matchday, userId: UserId) = Unit
        override suspend fun readyUserIds(matchday: Matchday): Set<UserId> = emptySet()
        override suspend fun firstReadyAt(matchday: Matchday): Instant? = null
        override suspend fun reset(matchday: Matchday) = Unit
    }

    private object NoClaims : ClubClaimRepository {
        override suspend fun claim(clubId: ClubId, userId: UserId): ClaimResult = error("sem técnico humano no teste")
        override suspend fun release(clubId: ClubId, dismissal: Dismissal): User? = null
    }

    private object NoOffers : TransferOfferRepository {
        override suspend fun create(offer: TransferOffer): TransferOffer = offer
        override suspend fun findById(id: OfferId): TransferOffer? = null
        override suspend fun findByClub(clubId: ClubId): List<TransferOffer> = emptyList()
        override suspend fun findPending(): List<TransferOffer> = emptyList()
        override suspend fun findPendingForPlayer(playerId: PlayerId): List<TransferOffer> = emptyList()
        override suspend fun saveIfPending(offer: TransferOffer): Boolean = false
        override suspend fun completeTransfer(
            accepted: TransferOffer,
            seller: Club,
            buyer: Club,
            rivals: List<TransferOffer>,
        ): TransferCompletion = TransferCompletion.OfferNoLongerPending
    }
}
