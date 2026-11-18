package com.carlesso.goalfather.application.service

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
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.ContinentalCup
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.Dismissal
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.OfferId
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.SeasonRecord
import com.carlesso.goalfather.domain.model.Slot
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.TrainingFocus
import com.carlesso.goalfather.domain.model.TransferOffer
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.ClaimResult
import com.carlesso.goalfather.domain.rules.ContinentalDraw
import com.carlesso.goalfather.domain.rules.drawContinental
import com.carlesso.goalfather.domain.rules.seededForContinental
import com.carlesso.goalfather.domain.rules.startWorld
import com.carlesso.goalfather.test.InMemoryCatalogRepository
import com.carlesso.goalfather.test.InMemoryCupRepository
import com.carlesso.goalfather.test.foreignContinentalClubs
import com.carlesso.goalfather.test.libertadoresGame
import com.carlesso.goalfather.test.mvpCatalog
import com.carlesso.goalfather.test.mvpClubs
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A Libertadores jogada de verdade pelo `PlayRoundService` (issue #132): o
 * Brasil do MVP (Série A, Série B e Copa do Brasil) + a Libertadores com 6
 * brasileiros e 26 estrangeiros sem liga. Joga 2026 até a virada do Brasil e
 * confere o meio de semana, o campeão na história, o envelhecimento dos
 * estrangeiros e a edição de 2027 sorteada com o campeão dentro.
 *
 * Fakes em memória (sem MockK): o estado precisa persistir entre os encaixes.
 */
class LibertadoresFlowTest {

    private val game = libertadoresGame()
    private val catalog = mvpCatalog().filter { it.country == Countries.BRAZIL } + game
    private val foreign = foreignContinentalClubs()
    private val clubs = FakeClubs(mvpClubs().filter { it.country == Countries.BRAZIL } + foreign)
    private val league = FakeLeague()
    private val history = FakeHistory()
    private val cups = InMemoryCupRepository()
    private val continentals = FakeContinentals()

    private val service = PlayRoundService(
        clubs, league, NoReadiness, EmptyMarket, NoPlayers, history, cups, NoClaims, NoOffers,
        InMemoryCatalogRepository(catalog),
        continentalRepo = continentals,
    )

    init {
        val brazilians = clubs.all().filter { it.country == Countries.BRAZIL }.take(6)
        val draw = drawContinental(2026, game, seededForContinental(brazilians + foreign, null), null, Random(2026))
        val edition = assertIs<ContinentalDraw.Drawn>(draw).edition
        val world = startWorld(2026, clubs.all(), catalog, listOf(edition))
        world.standings.forEach { league.tables[it.competitionId to it.season] = it }
        world.cups.forEach { cups.cups[it.season] = it }
        world.continentals.forEach { continentals.editions[it.competitionId to it.season] = it }
        league.rounds[requireNotNull(world.firstRound).matchday] = world.firstRound
    }

    @Test
    fun `a Libertadores e jogada no meio de semana, o campeao vai para a historia e para a edicao seguinte`() = runBlocking {
        var turnover: RoundEvent.SeasonFinished? = null
        val libertadoresMatchdays = mutableListOf<Matchday>()
        repeat(MAX_MATCHDAYS) {
            if (turnover != null) return@repeat
            val round = assertNotNull(league.findLatest(), "o mundo ficou sem encaixe")
            val own = round.matches.filter { it.competition == game.id }
            if (own.isNotEmpty()) {
                libertadoresMatchdays += round.matchday
                // Ninguém joga duas vezes no encaixe.
                val inAction = round.matches.flatMap { listOf(it.homeClubId, it.awayClubId) }
                assertEquals(inAction.size, inAction.toSet().size, "clube em dois jogos em ${round.matchday.key}")
            }
            league.startRound(round.matchday)
            turnover = service.stream(round.matchday).toList().filterIsInstance<RoundEvent.SeasonFinished>().firstOrNull()
        }
        assertNotNull(turnover, "o Brasil não virou")

        // 6 rodadas de grupo + 7 pernas de mata-mata, todas no meio de semana.
        assertEquals(13, libertadoresMatchdays.size)
        assertTrue(libertadoresMatchdays.all { it.slot == Slot.MIDWEEK })

        val ended = continentals.editions.getValue(game.id to 2026)
        val champion = assertNotNull(ended.champion, "a final foi disputada")
        assertEquals(12, ended.results.count { it.group == "Grupo A" })

        // Campeão na história do Brasil de 2026.
        val record = history.records.single()
        assertEquals(listOf(champion), record.continentalChampions.map { it.champion })

        // Os estrangeiros (sem liga) envelheceram com a virada do Brasil.
        assertTrue(foreign.all { club -> clubs.byId(club.id).squad.map { it.age } != club.squad.map { it.age } })

        // Edição de 2027 sorteada: 32, mesma cota por país e o campeão dentro.
        val next = continentals.editions.getValue(game.id to 2027)
        assertEquals(32, next.clubs.size)
        assertEquals(ended.quotas, next.quotas)
        assertTrue(next.clubs.any { it.id == champion.id })
        next.groups.forEach { assertEquals(4, it.clubs.map { c -> c.country }.distinct().size) }
    }

    // ─── Fakes em memória ───────────────────────────────────────────────────

    private class FakeContinentals : ContinentalRepository {
        val editions = mutableMapOf<Pair<CompetitionId, Int>, ContinentalCup>()
        override suspend fun find(competition: CompetitionId, season: Int): ContinentalCup? = editions[competition to season]
        override suspend fun findLatest(competition: CompetitionId): ContinentalCup? =
            editions.filterKeys { it.first == competition }.maxByOrNull { it.key.second }?.value
        override suspend fun save(edition: ContinentalCup) {
            editions[edition.competitionId to edition.season] = edition
        }
    }

    private class FakeClubs(initial: List<Club>) : ClubRepository {
        private val clubs = initial.associateBy { it.id }.toMutableMap()
        fun all(): List<Club> = clubs.values.sortedBy { it.id.value }
        fun byId(id: ClubId): Club = clubs.getValue(id)
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
            records += record
            return true
        }
    }

    private object NoReadiness : RoundReadinessRepository {
        override suspend fun markReady(matchday: Matchday, userId: UserId) = Unit
        override suspend fun readyUserIds(matchday: Matchday): Set<UserId> = emptySet()
        override suspend fun firstReadyAt(matchday: Matchday): Instant? = null
        override suspend fun reset(matchday: Matchday) = Unit
    }

    private object EmptyMarket : MarketRepository {
        override suspend fun findAll(position: Position?, maxPrice: Long?): List<MarketEntry> = emptyList()
        override suspend fun findEntry(playerId: PlayerId): MarketEntry? = null
        override suspend fun add(entry: MarketEntry) = Unit
        override suspend fun claim(playerId: PlayerId): Boolean = false
    }

    private object NoPlayers : PlayerRepository {
        override suspend fun saveFreeAgents(players: List<Player>) = Unit
        override suspend fun deleteAll(ids: List<PlayerId>) = Unit
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

    private companion object {
        const val MAX_MATCHDAYS = 200
    }
}
