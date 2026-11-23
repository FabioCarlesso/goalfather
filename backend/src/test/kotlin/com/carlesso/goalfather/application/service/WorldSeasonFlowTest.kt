package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubClaimRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
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
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.Dismissal
import com.carlesso.goalfather.domain.model.Division
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
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.TrainingFocus
import com.carlesso.goalfather.domain.model.TransferOffer
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.ClaimResult
import com.carlesso.goalfather.domain.rules.startWorld
import com.carlesso.goalfather.test.InMemoryCatalogRepository
import com.carlesso.goalfather.test.InMemoryCupRepository
import com.carlesso.goalfather.test.SERIE_A
import com.carlesso.goalfather.test.SERIE_B
import com.carlesso.goalfather.test.mvpCatalog
import com.carlesso.goalfather.test.mvpClubs
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * O mundo do MVP jogando de verdade pelo `PlayRoundService` (issue #131): os
 * 136 clubes sintéticos de tamanho real, encaixe a encaixe, com fakes em
 * memória dos ports (sem MockK: o estado tem de persistir entre os encaixes).
 *
 * Joga 2026 até a virada do Brasil (dezembro) e confere que só o Brasil virou;
 * depois segue até a virada da Europa (maio de 2027).
 *
 * `runBlocking` e não `runTest`: a simulação rápida roda no `Dispatchers.Default`
 * de verdade, e o ano inteiro passa do tempo-limite padrão do `runTest`.
 */
class WorldSeasonFlowTest {

    private val catalog = mvpCatalog()
    private val clubs = FakeClubs(mvpClubs())
    private val league = FakeLeague()
    private val history = FakeHistory()
    private val cups = InMemoryCupRepository()

    private val service = PlayRoundService(
        clubs,
        league,
        NoReadiness,
        EmptyMarket,
        NoPlayers,
        history,
        cups,
        NoClaims,
        NoOffers,
        InMemoryCatalogRepository(catalog),
    )

    private val brazil = Countries.BRAZIL.code
    private val europe = setOf(
        Countries.ENGLAND.code, Countries.SPAIN.code, Countries.ITALY.code, Countries.GERMANY.code, Countries.FRANCE.code,
    )

    init {
        val world = startWorld(2026, clubs.all(), catalog)
        world.standings.forEach { league.tables[it.competitionId to it.season] = it }
        world.cups.forEach { cups.cups[it.season] = it }
        league.rounds[requireNotNull(world.firstRound).matchday] = world.firstRound
    }

    /** Joga encaixes até um deles virar a temporada de algum país; devolve as viradas. */
    private fun playUntilTurnover(): List<RoundEvent.SeasonFinished> = runBlocking {
        repeat(MAX_MATCHDAYS) {
            val round = assertNotNull(league.findLatest(), "o mundo ficou sem encaixe")
            league.startRound(round.matchday)
            val events = service.stream(round.matchday).toList()
            val turnovers = events.filterIsInstance<RoundEvent.SeasonFinished>()
            if (turnovers.isNotEmpty()) return@runBlocking turnovers
        }
        error("Nenhuma virada em $MAX_MATCHDAYS encaixes")
    }

    private fun countryOf(event: RoundEvent.SeasonFinished): CountryCode = event.standings.first().country.code

    @Test
    fun `o Brasil vira em dezembro com acesso e rebaixamento e a Europa segue jogando ate maio`() {
        val europeBefore = clubs.all().filter { it.country.code in europe }.associate { it.id to it.division }

        // ── Virada do Brasil ────────────────────────────────────────────────
        val brazilTurn = playUntilTurnover()
        assertEquals(listOf(brazil), brazilTurn.map(::countryOf), "só o Brasil vira em dezembro")
        val turnAt = league.lastPlayed
        assertEquals(2026, turnAt.date.year)
        assertEquals(12, turnAt.date.monthValue)

        val finalA = brazilTurn.single().standings.single { it.competitionId == SERIE_A }
        val finalB = brazilTurn.single().standings.single { it.competitionId == SERIE_B }
        // Turno e returno completos: 38 jogos por clube, 380 na liga.
        assertTrue(finalA.rows.all { it.played == 38 } && finalB.rows.all { it.played == 38 })
        assertEquals(380, finalA.results.size)
        // Z-4 da A desce, G-4 da B sobe.
        val relegated = finalA.rows.sortedBy { it.position }.takeLast(4).map { it.clubId }
        val promoted = finalB.rows.sortedBy { it.position }.take(4).map { it.clubId }
        assertTrue(relegated.all { clubs.byId(it).division == Division(2) }, "rebaixados")
        assertTrue(promoted.all { clubs.byId(it).division == Division(1) }, "promovidos")
        assertEquals(20, clubs.all().count { it.country.code == brazil && it.division == Division(1) })

        // A história é do Brasil, temporada 2026.
        val record = history.records.single()
        assertEquals(brazil, record.country?.code)
        assertEquals("2026", record.seasonLabel)

        // A Europa NÃO virou: divisões iguais, tabela de 2026/27 pela metade.
        assertEquals(europeBefore, clubs.all().filter { it.country.code in europe }.associate { it.id to it.division })
        val current = runBlocking { league.currentStandings() }
        val premier = current.single { it.competitionId == CompetitionId("eng-premier-league") }
        assertEquals(2026, premier.season)
        assertTrue(premier.round in 1 until 38, "Premier League no meio da temporada: rodada ${premier.round}")
        assertTrue(clubs.all().filter { it.country.code in europe }.any { c -> c.squad.any { it.goals > 0 } }, "gols europeus não zerados")
        // O Brasil já está em 2027, com tabelas novas zeradas.
        val serieA2027 = current.single { it.competitionId == SERIE_A }
        assertEquals(2027, serieA2027.season)
        assertEquals(0, serieA2027.round)
        // O próximo encaixe é marcado depois do corrente.
        val next = assertNotNull(runBlocking { league.findLatest() })
        assertTrue(next.matchday > turnAt)

        // ── Virada da Europa ────────────────────────────────────────────────
        val europeTurns = mutableListOf<RoundEvent.SeasonFinished>()
        while (europeTurns.map(::countryOf).toSet() != europe) {
            val turn = playUntilTurnover()
            assertTrue(turn.none { countryOf(it) == brazil }, "o Brasil não vira de novo em maio")
            europeTurns += turn
        }
        val europeEnd = league.lastPlayed
        assertEquals(2027, europeEnd.date.year)
        assertTrue(europeEnd.date.monthValue <= 5)
        assertEquals(europe, history.records.filter { it.country?.code in europe }.map { it.country?.code }.toSet())
        assertTrue(history.records.filter { it.country?.code in europe }.all { it.season == 2026 && it.seasonLabel == "2026/27" })
        // Ligas fechadas: ninguém muda de divisão na Europa.
        assertEquals(europeBefore, clubs.all().filter { it.country.code in europe }.associate { it.id to it.division })
        val after = runBlocking { league.currentStandings() }
        assertTrue(after.filter { it.country.code in europe }.all { it.season == 2027 && it.seasonLabel == "2027/28" })
        // Enquanto isso o Brasil de 2027 andou (jan–mai).
        assertTrue(after.single { it.competitionId == SERIE_A }.round > 0)
    }

    // ─── Fakes em memória ───────────────────────────────────────────────────

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
        lateinit var lastPlayed: Matchday

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
            lastPlayed = round.matchday
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

    private object NoReadiness : RoundReadinessRepository {
        override suspend fun markReady(matchday: Matchday, userId: UserId) = Unit
        override suspend fun readyUserIds(matchday: Matchday): Set<UserId> = emptySet()
        override suspend fun firstReadyAt(matchday: Matchday): Instant? = null
        override suspend fun reset(matchday: Matchday) = Unit
    }

    private object EmptyMarket : MarketRepository {
        override suspend fun findAll(): List<MarketEntry> = emptyList()
        override suspend fun findEntry(playerId: PlayerId): MarketEntry? = null
        override suspend fun add(entry: MarketEntry) = Unit
        override suspend fun updateAll(entries: List<MarketEntry>) = Unit
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
        /** Um ano tem ~104 encaixes; folga para as duas viradas. */
        const val MAX_MATCHDAYS = 300
    }
}
