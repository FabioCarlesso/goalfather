package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.application.port.out.UserRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.StartRoundResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cobre a sincronização de rodadas (issue #20): contagem de prontos,
 * sinalização e o gate `start()` que só destrava com todos prontos — mais o
 * escape hatch por timeout (issue #45), com `Clock` injetado para adiantar o
 * relógio sem `Thread.sleep`.
 */
class RoundReadinessServiceTest {

    private val leagueRepo: LeagueRepository = mockk()
    private val userRepo: UserRepository = mockk()
    private val readinessRepo: RoundReadinessRepository = mockk(relaxed = true)

    private val timeout: Duration = Duration.ofMinutes(2)
    private val base: Instant = Instant.parse("2026-07-09T12:00:00Z")

    // Relógio ajustável: `now` controla o "agora" em cada teste de timeout.
    private var now: Instant = base
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
    }

    private val service = RoundReadinessService(leagueRepo, userRepo, readinessRepo, timeout, clock)

    private val scheduled = Round(number = 5, season = 2026, matches = emptyList())

    private fun manager(id: Long, name: String) = User(UserId(id), name, "hash", ClubId(id))

    @BeforeTest
    fun setup() {
        // Default: sem primeiro-pronto registrado → cronômetro não corre. O
        // relaxed mock devolveria um `Instant` fictício (não null), fazendo o
        // timeout parecer estourado; fixamos null e cada teste de timeout
        // sobrescreve com um instante concreto.
        coEvery { readinessRepo.firstReadyAt(any()) } returns null
    }

    @Test
    fun `status conta prontos e lista os pendentes pelo username`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled
        coEvery { userRepo.findManagers() } returns listOf(manager(1, "ana"), manager(2, "bruno"))
        coEvery { readinessRepo.readyUserIds(5) } returns setOf(UserId(1))

        val status = service.status()

        assertEquals(5, status.roundNumber)
        assertEquals(1, status.readyCount)
        assertEquals(2, status.totalCount)
        assertEquals(listOf("bruno"), status.pendingUsernames)
        assertTrue(!status.allReady)
    }

    @Test
    fun `markReady sinaliza pronto e devolve status atualizado`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled
        coEvery { userRepo.findManagers() } returns listOf(manager(1, "ana"), manager(2, "bruno"))
        coEvery { readinessRepo.readyUserIds(5) } returns setOf(UserId(1), UserId(2))

        val status = service.markReady(UserId(2))

        coVerify(exactly = 1) { readinessRepo.markReady(5, UserId(2)) }
        assertEquals(2, status.readyCount)
        assertTrue(status.allReady)
        assertTrue(status.pendingUsernames.isEmpty())
    }

    @Test
    fun `start retorna NotReady com pendentes quando nem todos sinalizaram`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled
        coEvery { userRepo.findManagers() } returns listOf(manager(1, "ana"), manager(2, "bruno"))
        coEvery { readinessRepo.readyUserIds(5) } returns setOf(UserId(1))

        val result = service.start()

        val notReady = assertIs<StartRoundResult.NotReady>(result)
        assertEquals(listOf("bruno"), notReady.status.pendingUsernames)
        // Não destrava a rodada.
        coVerify(exactly = 0) { leagueRepo.startRound(any()) }
    }

    @Test
    fun `start destrava e marca InProgress quando todos prontos`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled
        coEvery { userRepo.findManagers() } returns listOf(manager(1, "ana"), manager(2, "bruno"))
        coEvery { readinessRepo.readyUserIds(5) } returns setOf(UserId(1), UserId(2))
        coEvery { leagueRepo.startRound(5) } returns true

        val result = service.start()

        assertEquals(StartRoundResult.Started(5), result)
        coVerify(exactly = 1) { leagueRepo.startRound(5) }
    }

    @Test
    fun `start e idempotente quando a rodada ja esta InProgress`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled.copy(status = RoundStatus.InProgress)
        coEvery { userRepo.findManagers() } returns listOf(manager(1, "ana"))
        coEvery { readinessRepo.readyUserIds(5) } returns setOf(UserId(1))

        val result = service.start()

        assertEquals(StartRoundResult.Started(5), result)
        // Já estava InProgress: não regrava.
        coVerify(exactly = 0) { leagueRepo.startRound(any()) }
    }

    @Test
    fun `start retorna AlreadyFinished quando a rodada ja foi simulada`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled.copy(status = RoundStatus.Finished)

        val result = service.start()

        assertEquals(StartRoundResult.AlreadyFinished(5), result)
        coVerify(exactly = 0) { leagueRepo.startRound(any()) }
    }

    @Test
    fun `start retorna NoRound quando nao ha rodada`() = runTest {
        coEvery { leagueRepo.findLatest() } returns null

        assertEquals(StartRoundResult.NoRound, service.start())
    }

    @Test
    fun `sem tecnicos a liga nao destrava (totalCount zero)`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled
        coEvery { userRepo.findManagers() } returns emptyList()
        coEvery { readinessRepo.readyUserIds(5) } returns emptySet()

        val result = service.start()

        assertIs<StartRoundResult.NotReady>(result)
    }

    @Test
    fun `markReady ignora quem nao e tecnico (sem clube) - ponto 6`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled
        coEvery { userRepo.findManagers() } returns listOf(manager(1, "ana"), manager(2, "bruno"))
        coEvery { readinessRepo.readyUserIds(5) } returns emptySet()

        // UserId(99) não está entre os técnicos: não deve gerar linha de prontidão.
        service.markReady(UserId(99))

        coVerify(exactly = 0) { readinessRepo.markReady(any(), any()) }
    }

    @Test
    fun `dois start concorrentes destravam a rodada uma unica vez - ponto 3`() = runTest {
        // A transição Scheduled→InProgress agora é um compare-and-set no banco
        // (`startRound`, lock otimista da issue #46), não mais um Mutex in-JVM.
        // O mock reproduz o contrato do port: só o primeiro chamador a ver
        // `Scheduled` sai com `true`; os demais recebem `false` e seguem
        // idempotentes.
        var current = scheduled
        val transitions = AtomicInteger()
        coEvery { leagueRepo.findLatest() } answers { current }
        coEvery { userRepo.findManagers() } returns listOf(manager(1, "ana"), manager(2, "bruno"))
        coEvery { readinessRepo.readyUserIds(5) } returns setOf(UserId(1), UserId(2))
        coEvery { leagueRepo.startRound(5) } answers {
            if (current.status != RoundStatus.Scheduled) return@answers false
            current = current.copy(status = RoundStatus.InProgress)
            transitions.incrementAndGet()
            true
        }

        val a = async { service.start() }
        val b = async { service.start() }
        val results = awaitAll(a, b)

        // Ambos confirmam Started, mas a rodada só transita UMA vez.
        assertTrue(results.all { it is StartRoundResult.Started })
        assertEquals(1, transitions.get())
    }

    @Test
    fun `start devolve AlreadyFinished mesmo com a proxima rodada ja gerada - issue 46`() = runTest {
        // A rodada 5 era Scheduled no primeiro `findLatest`, mas outra instância
        // a simulou por completo antes do `startRound` — e ao concluir já gerou a
        // rodada 6 (Scheduled), que passou a ser a "última". O CAS falha; a
        // detecção precisa reler a rodada 5 POR NÚMERO (`findRound(5)`). Se
        // voltasse a usar `findLatest()`, leria a 6 (Scheduled), não veria o
        // encerramento e devolveria `Started(5)` de uma rodada já encerrada
        // (achado 2 da review). O stub de `findLatest` devolve a 6 justamente
        // para o teste falhar caso a detecção regrida para ele.
        val roundReadThenNext = listOf(scheduled, Round(number = 6, season = 2026, matches = emptyList()))
        coEvery { leagueRepo.findLatest() } returnsMany roundReadThenNext
        coEvery { leagueRepo.findRound(5) } returns scheduled.copy(status = RoundStatus.Finished)
        coEvery { userRepo.findManagers() } returns listOf(manager(1, "ana"))
        coEvery { readinessRepo.readyUserIds(5) } returns setOf(UserId(1))
        coEvery { leagueRepo.startRound(5) } returns false

        val result = service.start()

        assertEquals(StartRoundResult.AlreadyFinished(5), result)
    }

    // ─── Escape hatch por timeout (issue #45) ─────────────────────────────

    @Test
    fun `status expoe secondsRemaining enquanto o timeout corre - issue 45`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled
        coEvery { userRepo.findManagers() } returns listOf(manager(1, "ana"), manager(2, "bruno"))
        coEvery { readinessRepo.readyUserIds(5) } returns setOf(UserId(1))
        coEvery { readinessRepo.firstReadyAt(5) } returns base
        now = base.plusSeconds(90) // faltam 30s dos 120s

        val status = service.status()

        assertEquals(30, status.secondsRemaining)
        assertTrue(!status.timedOut)
        assertTrue(!status.canStart)
    }

    @Test
    fun `sem ninguem pronto o cronometro nao corre - issue 45`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled
        coEvery { userRepo.findManagers() } returns listOf(manager(1, "ana"), manager(2, "bruno"))
        coEvery { readinessRepo.readyUserIds(5) } returns emptySet()

        val status = service.status()

        assertNull(status.secondsRemaining)
        assertTrue(!status.timedOut)
        // Não consulta firstReadyAt: relógio só arma com o primeiro pronto.
        coVerify(exactly = 0) { readinessRepo.firstReadyAt(any()) }
    }

    @Test
    fun `start destrava com pendente quando o timeout expira - issue 45`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled
        coEvery { userRepo.findManagers() } returns listOf(manager(1, "ana"), manager(2, "bruno"))
        // bruno nunca sinaliza; só ana está pronta.
        coEvery { readinessRepo.readyUserIds(5) } returns setOf(UserId(1))
        coEvery { readinessRepo.firstReadyAt(5) } returns base
        coEvery { leagueRepo.startRound(5) } returns true
        now = base.plus(timeout).plusSeconds(1) // ultrapassa o timeout

        val result = service.start()

        assertEquals(StartRoundResult.Started(5), result)
        coVerify(exactly = 1) { leagueRepo.startRound(5) }
    }

    @Test
    fun `start segue NotReady antes de o timeout expirar - issue 45`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled
        coEvery { userRepo.findManagers() } returns listOf(manager(1, "ana"), manager(2, "bruno"))
        coEvery { readinessRepo.readyUserIds(5) } returns setOf(UserId(1))
        coEvery { readinessRepo.firstReadyAt(5) } returns base
        now = base.plusSeconds(60) // ainda dentro dos 120s

        val result = service.start()

        val notReady = assertIs<StartRoundResult.NotReady>(result)
        assertEquals(60, notReady.status.secondsRemaining)
        coVerify(exactly = 0) { leagueRepo.startRound(any()) }
    }
}
