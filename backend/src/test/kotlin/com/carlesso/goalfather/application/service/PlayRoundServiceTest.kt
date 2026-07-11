package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Division
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayRoundServiceTest {

    private val clubRepo: ClubRepository = mockk()
    private val leagueRepo: LeagueRepository = mockk()
    // relaxed: o reset de prontidão (issue #20) é efeito colateral; só alguns
    // testes o verificam explicitamente, os demais ignoram.
    private val readinessRepo: RoundReadinessRepository = mockk(relaxed = true)
    private val service = PlayRoundService(clubRepo, leagueRepo, readinessRepo)

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
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns awayClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.finishRound(any()) } returns true
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
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns awayClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.finishRound(any()) } returns true
        coEvery { leagueRepo.saveStandings(any()) } just Runs

        val events = service.stream(1).toList()
        val finished = events.last()

        assertIs<RoundEvent.RoundFinished>(finished)
        // Liga de divisão única → uma tabela só no evento (issue #47).
        val table = finished.standings.single()
        assertEquals(1, table.round)
        assertEquals(2026, table.season)
        // Soma de pontos da rodada = 3 (vencedor) ou 2 (empate)
        val total = table.rows.sumOf { it.points }
        assertTrue(total in 2..3, "Total de pontos numa rodada deve ser 2 ou 3, foi $total")
    }

    @Test
    fun `finishRound e saveStandings sao invocados`() = runTest {
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(any()) } returns homeClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        val savedStandings = slot<Standings>()
        val claimed = slot<Round>()
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.finishRound(capture(claimed)) } returns true
        coEvery { leagueRepo.saveStandings(capture(savedStandings)) } just Runs

        service.stream(1).toList()

        assertTrue(savedStandings.isCaptured)
        // A rodada encerra pelo claim `finishRound` (issue #46), não por um
        // `saveRound` solto — é ele que serializa as instâncias.
        val finished = claimed.captured
        assertEquals(RoundStatus.Finished, finished.status)
        assertNotNull(finished.matches.find { it.status == RoundStatus.Finished })
    }

    @Test
    fun `mesma rodada produz mesma sequencia (determinismo por matchId)`() = runTest {
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns awayClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.finishRound(any()) } returns true
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
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(any()) } returns homeClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        val savedRounds = mutableListOf<Round>()
        coEvery { leagueRepo.saveRound(capture(savedRounds)) } just Runs
        coEvery { leagueRepo.finishRound(any()) } returns true
        coEvery { leagueRepo.saveStandings(any()) } just Runs

        service.stream(1).toList()

        // A rodada 1/2026 encerra via `finishRound`; `saveRound` só grava a
        // rodada 1/2027 agendada (issue #46).
        assertEquals(1, savedRounds.size)
        val next = savedRounds.last()
        assertEquals(1, next.number)
        assertEquals(2027, next.season)
        assertEquals(RoundStatus.Scheduled, next.status)
        assertEquals(1, next.matches.size) // 2 clubes → 1 partida por rodada
    }

    @Test
    fun `emite SeasonFinished com o campeao ao fim da temporada`() = runTest {
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(any()) } returns homeClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        val savedStandings = mutableListOf<Standings>()
        coEvery { leagueRepo.finishRound(any()) } returns true
        coEvery { leagueRepo.saveStandings(capture(savedStandings)) } just Runs

        val events = service.stream(1).toList()

        val seasonFinished = events.filterIsInstance<RoundEvent.SeasonFinished>().single()
        assertEquals(2026, seasonFinished.season)
        // Campeão = líder da tabela final da elite (divisão 1).
        assertEquals(seasonFinished.standings.single().rows.first().clubId, seasonFinished.champion.clubId)
        // RoundFinished continua sendo o último evento do stream.
        assertIs<RoundEvent.RoundFinished>(events.last())
        // Tabela nova (2027) zerada também foi salva.
        val fresh = savedStandings.last()
        assertEquals(2027, fresh.season)
        assertEquals(0, fresh.round)
        assertTrue(fresh.rows.all { it.points == 0 })
    }

    @Test
    fun `virada de temporada aplica promocao e rebaixamento entre divisoes (issue 47)`() = runTest {
        // 2 divisões × 4 clubes → temporada de 3 rodadas; a rodada 3 encerra.
        // A rodada usa clubes fora das tabelas (ids 100/101) de propósito:
        // nenhum ponto muda e a ordem final é EXATAMENTE a das tabelas abaixo,
        // tornando o resultado da virada determinístico e legível no teste.
        val div1Clubs = (1L..4L).map { makeClub(id = it, name = "D1-$it") }
        val div2Clubs = (5L..8L).map { makeClub(id = it, name = "D2-$it", division = Division(2)) }
        val allClubs = div1Clubs + div2Clubs

        fun tableOf(division: Int, ids: List<Long>, promotion: Int, relegation: Int) = Standings(
            season = 2026,
            round = 2,
            division = Division(division),
            promotionSpots = promotion,
            relegationSpots = relegation,
            rows = ids.mapIndexed { i, id ->
                StandingRow(position = i + 1, clubId = ClubId(id), clubName = "C$id")
            },
        )

        val finalRound = Round(
            number = 3,
            season = 2026,
            matches = listOf(RoundMatch(3101, ClubId(100), ClubId(101), "X", "Y")),
        )
        coEvery { leagueRepo.findRound(3) } returns finalRound
        coEvery { leagueRepo.currentStandings() } returns listOf(
            tableOf(1, listOf(1, 2, 3, 4), promotion = 0, relegation = 2),
            tableOf(2, listOf(5, 6, 7, 8), promotion = 2, relegation = 0),
        )
        coEvery { clubRepo.findById(any()) } returns null
        coEvery { clubRepo.findAll() } returns allClubs
        val savedClubs = mutableListOf<Club>()
        coEvery { clubRepo.save(capture(savedClubs)) } answers { firstArg() }
        val savedRounds = mutableListOf<Round>()
        coEvery { leagueRepo.saveRound(capture(savedRounds)) } just Runs
        coEvery { leagueRepo.finishRound(any()) } returns true
        val savedStandings = mutableListOf<Standings>()
        coEvery { leagueRepo.saveStandings(capture(savedStandings)) } just Runs

        val events = service.stream(3).toList()

        // Campeão = líder da elite.
        val seasonFinished = events.filterIsInstance<RoundEvent.SeasonFinished>().single()
        assertEquals(ClubId(1), seasonFinished.champion.clubId)

        // 3 e 4 caem, 5 e 6 sobem — só quem mudou de divisão é regravado.
        val divisionByClub = savedClubs.associate { it.id.value to it.division.value }
        assertEquals(mapOf(3L to 2, 4L to 2, 5L to 1, 6L to 1), divisionByClub)

        // Rodada 1 da temporada nova respeita as divisões recompostas.
        val nextRound = savedRounds.last()
        assertEquals(2027, nextRound.season)
        val clubsByDivision = nextRound.matches.groupBy({ it.division.value }) {
            listOf(it.homeClubId.value, it.awayClubId.value)
        }.mapValues { (_, ids) -> ids.flatten().toSet() }
        assertEquals(setOf(1L, 2L, 5L, 6L), clubsByDivision[1])
        assertEquals(setOf(3L, 4L, 7L, 8L), clubsByDivision[2])

        // Tabelas zeradas da temporada nova: uma por divisão, com as vagas de zona.
        val fresh = savedStandings.filter { it.season == 2027 }
        assertEquals(listOf(1, 2), fresh.map { it.division.value })
        assertEquals(setOf(1L, 2L, 5L, 6L), fresh[0].rows.map { it.clubId.value }.toSet())
        assertEquals(2, fresh[0].relegationSpots)
        assertEquals(2, fresh[1].promotionSpots)
        assertEquals(0, fresh[0].promotionSpots)
        assertEquals(0, fresh[1].relegationSpots)
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
        coEvery { leagueRepo.currentStandings() } returns listOf(standings4)
        clubs.forEach { c -> coEvery { clubRepo.findById(c.id) } returns c }
        coEvery { clubRepo.findAll() } returns clubs
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        val savedRounds = mutableListOf<Round>()
        coEvery { leagueRepo.saveRound(capture(savedRounds)) } just Runs
        coEvery { leagueRepo.finishRound(any()) } returns true
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
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns awayClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.finishRound(any()) } returns true
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
    fun `folha sem cobertura registra deficit em RoundFinance (issue 23)`() = runTest {
        // Rodada 2 (par) cobra salários. O visitante (sem bilheteria) com caixa
        // zerado não cobre a folha → deficit > 0, sinalizando "no vermelho".
        // Antes esse rombo era truncado em zero e perdido silenciosamente.
        val poorAway = makeClub(id = 2, name = "Away FC", squadSize = 11, overall = 70, cash = 0)
        val salaryRound = Round(
            number = 2,
            season = 2026,
            matches = listOf(
                RoundMatch(2001, ClubId(1), ClubId(2), "Home FC", "Away FC"),
            ),
        )
        coEvery { leagueRepo.findRound(2) } returns salaryRound
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns poorAway
        coEvery { clubRepo.findAll() } returns listOf(homeClub, poorAway)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.finishRound(any()) } returns true
        coEvery { leagueRepo.saveStandings(any()) } just Runs

        val finished = service.stream(2).toList().last()
        assertIs<RoundEvent.RoundFinished>(finished)

        val awayFinance = finished.finances.first { it.clubId == ClubId(2) }
        assertTrue(awayFinance.salariesPaid > 0, "Rodada par deve cobrar salários")
        assertEquals(
            awayFinance.salariesPaid,
            awayFinance.deficit,
            "Caixa zero e sem bilheteria → folha inteira vira rombo",
        )

        // O mandante cobre a folha com caixa+bilheteria → sem deficit.
        val homeFinance = finished.finances.first { it.clubId == ClubId(1) }
        assertEquals(0L, homeFinance.deficit, "Mandante com caixa folgado não fica no vermelho")
    }

    @Test
    fun `rodada ja finalizada faz replay sem re-aplicar efeitos (idempotencia)`() = runTest {
        val finishedRound = round.copy(status = RoundStatus.Finished)
        coEvery { leagueRepo.findRound(1) } returns finishedRound
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns awayClub

        val events = service.stream(1).toList()

        // Ainda re-emite os eventos (replay para visualização) e termina com RoundFinished.
        assertTrue(events.any { it is RoundEvent.MatchUpdate })
        assertIs<RoundEvent.RoundFinished>(events.last())

        // Nenhum efeito colateral: nem sequer tenta reivindicar o encerramento.
        coVerify(exactly = 0) { leagueRepo.finishRound(any()) }
        coVerify(exactly = 0) { leagueRepo.saveRound(any()) }
        coVerify(exactly = 0) { leagueRepo.saveStandings(any()) }
        coVerify(exactly = 0) { clubRepo.save(any()) }
        coVerify(exactly = 0) { clubRepo.findAll() }
        // Replay não pode zerar a prontidão — a rodada já foi consumida (issue #20).
        coVerify(exactly = 0) { readinessRepo.reset(any()) }
    }

    @Test
    fun `ao finalizar a rodada a prontidao e resetada para a proxima (issue 20)`() = runTest {
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(any()) } returns homeClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.finishRound(any()) } returns true
        coEvery { leagueRepo.saveStandings(any()) } just Runs

        service.stream(1).toList()

        // Prontidão da rodada 1 é zerada exatamente uma vez.
        coVerify(exactly = 1) { readinessRepo.reset(1) }
    }

    @Test
    fun `dois streams concorrentes da mesma rodada aplicam os efeitos uma unica vez (issue 20)`() = runTest {
        // Dois clientes abrem o WS da mesma rodada ao mesmo tempo — no mesmo
        // processo OU em instâncias diferentes (issue #46). O mock reproduz o
        // compare-and-set que o `@Version` garante no banco: só o primeiro a ver
        // a rodada não-finalizada sai de `finishRound` com `true`.
        var current = round
        val claims = AtomicInteger()
        coEvery { leagueRepo.findRound(1) } answers { current }
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns awayClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.finishRound(any()) } answers {
            if (current.status == RoundStatus.Finished) return@answers false
            current = firstArg()
            claims.incrementAndGet()
            true
        }
        coEvery { leagueRepo.saveStandings(any()) } just Runs

        val a = async { service.stream(1).toList() }
        val b = async { service.stream(1).toList() }
        awaitAll(a, b)

        // Apesar dos dois streams, a rodada é encerrada e a prontidão é zerada
        // UMA vez só — o perdedor da corrida faz apenas replay. (Não asserimos
        // sobre saveStandings: esta liga de 2 clubes vira a temporada na rodada 1,
        // o que grava a tabela 2× num único encerramento — fora do escopo.)
        assertEquals(1, claims.get())
        coVerify(exactly = 1) { readinessRepo.reset(1) }
    }

    @Test
    fun `perdedor da corrida entre instancias nao aplica efeito nenhum (issue 46)`() = runTest {
        // Cenário multi-instância: o nó B simula, mas ao tentar reivindicar o
        // encerramento descobre que o nó A já commitou (`@Version` no banco →
        // OptimisticLockException → `false`). B não pode gravar NADA: caixa,
        // estatísticas, tabela e próxima rodada já foram aplicados por A.
        coEvery { leagueRepo.findRound(1) } returns round
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(ClubId(1)) } returns homeClub
        coEvery { clubRepo.findById(ClubId(2)) } returns awayClub
        coEvery { leagueRepo.finishRound(any()) } returns false

        val events = service.stream(1).toList()

        // O stream do perdedor ainda entrega a partida e o RoundFinished — do
        // ponto de vista do cliente, nada muda.
        assertTrue(events.any { it is RoundEvent.MatchUpdate })
        assertIs<RoundEvent.RoundFinished>(events.last())

        coVerify(exactly = 0) { clubRepo.save(any()) }
        coVerify(exactly = 0) { leagueRepo.saveStandings(any()) }
        coVerify(exactly = 0) { leagueRepo.saveRound(any()) }
        coVerify(exactly = 0) { readinessRepo.reset(any()) }
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
        coEvery { leagueRepo.currentStandings() } returns listOf(standings)
        coEvery { clubRepo.findById(any()) } returns homeClub
        coEvery { clubRepo.findAll() } returns listOf(homeClub, awayClub)
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.finishRound(any()) } returns true
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
