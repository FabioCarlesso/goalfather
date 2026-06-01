package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.test.makeClub
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayRoundServiceTest {

    private val clubRepo: ClubRepository = mockk()
    private val leagueRepo: LeagueRepository = mockk()
    private val service = PlayRoundService(clubRepo, leagueRepo)

    private val homeClub = makeClub(id = 1, name = "Home FC", squadSize = 11, overall = 80)
    private val awayClub = makeClub(id = 2, name = "Away FC", squadSize = 11, overall = 70)

    private val round = Round(
        number = 1,
        season = 2026,
        matches = listOf(
            RoundMatch(
                matchId = 1001,
                homeClubId = ClubId(1),
                awayClubId = ClubId(2),
                homeClubName = "Home FC",
                awayClubName = "Away FC",
            ),
        ),
    )

    private val standings = Standings(
        season = 2026,
        round = 0,
        rows = listOf(
            StandingRow(position = 1, clubId = ClubId(1), clubName = "Home FC"),
            StandingRow(position = 2, clubId = ClubId(2), clubName = "Away FC"),
        ),
    )

    @Test
    fun `stream comeca com KickOff e termina com RoundFinished`() = runTest {
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns standings
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns awayClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.saveStandings(any()) } just Runs

        val events = service.stream(1).toList()

        // Primeiro evento útil é o KickOff da única partida
        val firstUpdate = events.filterIsInstance<RoundEvent.MatchUpdate>().first()
        assertIs<MatchEvent.KickOff>(firstUpdate.event)
        assertEquals(1001L, firstUpdate.matchId)

        // Último evento é sempre RoundFinished
        assertIs<RoundEvent.RoundFinished>(events.last())
    }

    @Test
    fun `RoundFinished carrega standings recalculadas`() = runTest {
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns standings
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns awayClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.saveStandings(any()) } just Runs

        val events = service.stream(1).toList()
        val finished = events.last()

        assertIs<RoundEvent.RoundFinished>(finished)
        assertEquals(1, finished.standings.round)
        assertEquals(2026, finished.standings.season)
        // Soma de pontos da rodada = 3 (vencedor) ou 2 (empate)
        val total = finished.standings.rows.sumOf { it.points }
        assertTrue(total in 2..3, "Total de pontos numa rodada deve ser 2 ou 3, foi $total")
    }

    @Test
    fun `salvaRound e saveStandings sao invocados`() = runTest {
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns standings
        coEvery { clubRepo.findById(any()) } returns homeClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        val savedRounds = mutableListOf<Round>()
        val savedStandings = slot<Standings>()
        coEvery { leagueRepo.saveRound(capture(savedRounds)) } just Runs
        coEvery { leagueRepo.saveStandings(capture(savedStandings)) } just Runs

        service.stream(1).toList()

        assertTrue(savedStandings.isCaptured)
        // A rodada atual finalizada é salva (primeiro saveRound).
        val finished = savedRounds.first()
        assertEquals(RoundStatus.Finished, finished.status)
        assertNotNull(finished.matches.find { it.status == RoundStatus.Finished })
    }

    @Test
    fun `mesma rodada produz mesma sequencia (determinismo por matchId)`() = runTest {
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns standings
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns awayClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.saveStandings(any()) } just Runs

        val events1 = service.stream(1).toList()
        val events2 = service.stream(1).toList()

        // Mesmo matchId = mesma seed = mesmos eventos (exceto pelas
        // standings que poderiam diferir se mutássemos current — mas
        // mock retorna sempre o initial, então deve casar 100%).
        val updates1 = events1.filterIsInstance<RoundEvent.MatchUpdate>()
        val updates2 = events2.filterIsInstance<RoundEvent.MatchUpdate>()
        assertEquals(updates1, updates2)
    }

    @Test
    fun `apos a ultima rodada da temporada abre a temporada seguinte`() = runTest {
        // 2 clubes ⇒ turno único de 1 rodada ⇒ a rodada 1 é a última da temporada.
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns standings
        coEvery { clubRepo.findById(any()) } returns homeClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        val savedRounds = mutableListOf<Round>()
        coEvery { leagueRepo.saveRound(capture(savedRounds)) } just Runs
        coEvery { leagueRepo.saveStandings(any()) } just Runs

        service.stream(1).toList()

        // saveRound: rodada 1/2026 finalizada + rodada 1/2027 agendada.
        assertEquals(2, savedRounds.size)
        val next = savedRounds.last()
        assertEquals(1, next.number)
        assertEquals(2027, next.season)
        assertEquals(RoundStatus.Scheduled, next.status)
        assertEquals(1, next.matches.size) // 2 clubes → 1 partida por rodada
    }

    @Test
    fun `emite SeasonFinished com o campeao ao fim da temporada`() = runTest {
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns standings
        coEvery { clubRepo.findById(any()) } returns homeClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        val savedStandings = mutableListOf<Standings>()
        coEvery { leagueRepo.saveStandings(capture(savedStandings)) } just Runs

        val events = service.stream(1).toList()

        val seasonFinished = events.filterIsInstance<RoundEvent.SeasonFinished>().single()
        assertEquals(2026, seasonFinished.season)
        // Campeão = líder da tabela final.
        assertEquals(seasonFinished.standings.rows.first().clubId, seasonFinished.champion.clubId)
        // RoundFinished continua sendo o último evento do stream.
        assertIs<RoundEvent.RoundFinished>(events.last())
        // Tabela nova (2027) zerada também foi salva.
        val fresh = savedStandings.last()
        assertEquals(2027, fresh.season)
        assertEquals(0, fresh.round)
        assertTrue(fresh.rows.all { it.points == 0 })
    }

    @Test
    fun `no meio da temporada gera a proxima rodada da mesma temporada`() = runTest {
        val clubs = (1L..4L).map { makeClub(id = it, name = "C$it", squadSize = 11, overall = 75) }
        val midRound = Round(
            number = 1,
            season = 2026,
            matches = listOf(
                RoundMatch(1001, ClubId(1), ClubId(4), "C1", "C4"),
                RoundMatch(1002, ClubId(2), ClubId(3), "C2", "C3"),
            ),
        )
        val standings4 = Standings(
            season = 2026,
            round = 0,
            rows = clubs.mapIndexed { i, c -> StandingRow(i + 1, c.id, c.name) },
        )
        coEvery { leagueRepo.findRound(1) } returns midRound
        coEvery { leagueRepo.currentStandings() } returns standings4
        clubs.forEach { c -> coEvery { clubRepo.findById(c.id) } returns c }
        coEvery { clubRepo.findAll() } returns clubs
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        val savedRounds = mutableListOf<Round>()
        coEvery { leagueRepo.saveRound(capture(savedRounds)) } just Runs
        coEvery { leagueRepo.saveStandings(any()) } just Runs

        val events = service.stream(1).toList()

        // 4 clubes ⇒ 3 rodadas/temporada; a rodada 1 não encerra a temporada.
        assertTrue(events.none { it is RoundEvent.SeasonFinished })
        val next = savedRounds.last()
        assertEquals(2, next.number)
        assertEquals(2026, next.season)
        assertEquals(2, next.matches.size) // 4 clubes → 2 partidas
    }

    @Test
    fun `RoundFinished carrega financas e o caixa do mandante sobe com a bilheteria`() = runTest {
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns standings
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns awayClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.saveStandings(any()) } just Runs
        val savedClubs = mutableListOf<com.carlesso.goalfather.domain.model.Club>()
        coEvery { clubRepo.save(capture(savedClubs)) } answers { firstArg() }

        val events = service.stream(1).toList()
        val finished = events.last()
        assertIs<RoundEvent.RoundFinished>(finished)

        // Rodada 1 é ímpar → sem folha salarial; mandante (id 1) tem bilheteria > 0.
        val homeFinance = finished.finances.first { it.clubId == ClubId(1) }
        assertTrue(homeFinance.ticketRevenue > 0, "Mandante deveria ter bilheteria")
        assertEquals(0L, homeFinance.salariesPaid, "Rodada ímpar não cobra salários")
        val awayFinance = finished.finances.first { it.clubId == ClubId(2) }
        assertEquals(0L, awayFinance.ticketRevenue, "Visitante não tem bilheteria")

        // O caixa do mandante salvo reflete o caixa inicial + bilheteria.
        val savedHome = savedClubs.last { it.id == ClubId(1) }
        assertEquals(homeClub.cash + homeFinance.ticketRevenue, savedHome.cash)
    }

    @Test
    fun `rodada ja finalizada faz replay sem re-aplicar efeitos (idempotencia)`() = runTest {
        val finishedRound = round.copy(status = RoundStatus.Finished)
        coEvery { leagueRepo.findRound(1) } returns finishedRound
        coEvery { leagueRepo.currentStandings() } returns standings
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns awayClub

        val events = service.stream(1).toList()

        // Ainda re-emite os eventos (replay para visualização) e termina com RoundFinished.
        assertTrue(events.any { it is RoundEvent.MatchUpdate })
        assertIs<RoundEvent.RoundFinished>(events.last())

        // Nenhum efeito colateral: sem persistência de rodada/tabela/clube nem nova rodada.
        coVerify(exactly = 0) { leagueRepo.saveRound(any()) }
        coVerify(exactly = 0) { leagueRepo.saveStandings(any()) }
        coVerify(exactly = 0) { clubRepo.save(any()) }
        coVerify(exactly = 0) { clubRepo.findAll() }
    }

    @Test
    fun `rodada inexistente lanca IllegalArgumentException`() = runTest {
        coEvery { leagueRepo.findRound(999) } returns null

        assertThrows<IllegalArgumentException> {
            service.stream(999).toList()
        }
    }

    @Test
    fun `eventos com mesmo minuto preservam ordem de emissao (estabilidade)`() = runTest {
        // Duas partidas → eventos intercalados. Sort estável garante
        // que para um mesmo minuto, a ordem seja determinística.
        val twoMatchRound = round.copy(
            matches = round.matches + RoundMatch(
                matchId = 1002,
                homeClubId = ClubId(2),
                awayClubId = ClubId(1),
                homeClubName = "Away FC",
                awayClubName = "Home FC",
            ),
        )
        coEvery { leagueRepo.findRound(1) } returns twoMatchRound
        coEvery { leagueRepo.currentStandings() } returns standings
        coEvery { clubRepo.findById(any()) } returns homeClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.saveStandings(any()) } just Runs

        val events = service.stream(1).toList()
        val updates = events.filterIsInstance<RoundEvent.MatchUpdate>()

        // Verifica que minutos são monotonicamente não-decrescentes
        for (i in 1 until updates.size) {
            assertTrue(
                updates[i].event.minute >= updates[i - 1].event.minute,
                "Minuto retrocedeu: ${updates[i - 1].event.minute} -> ${updates[i].event.minute}",
            )
        }
    }
}
