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
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Cobre a sincronização de rodadas (issue #20): contagem de prontos,
 * sinalização e o gate `start()` que só destrava com todos prontos.
 */
class RoundReadinessServiceTest {

    private val leagueRepo: LeagueRepository = mockk()
    private val userRepo: UserRepository = mockk()
    private val readinessRepo: RoundReadinessRepository = mockk(relaxed = true)
    private val service = RoundReadinessService(leagueRepo, userRepo, readinessRepo)

    private val scheduled = Round(number = 5, season = 2026, matches = emptyList())

    private fun manager(id: Long, name: String) = User(UserId(id), name, "hash", ClubId(id))

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
        coVerify(exactly = 0) { leagueRepo.saveRound(any()) }
    }

    @Test
    fun `start destrava e marca InProgress quando todos prontos`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled
        coEvery { userRepo.findManagers() } returns listOf(manager(1, "ana"), manager(2, "bruno"))
        coEvery { readinessRepo.readyUserIds(5) } returns setOf(UserId(1), UserId(2))
        coEvery { leagueRepo.saveRound(any()) } just Runs

        val result = service.start()

        assertEquals(StartRoundResult.Started(5), result)
        coVerify(exactly = 1) { leagueRepo.saveRound(match { it.status == RoundStatus.InProgress }) }
    }

    @Test
    fun `start e idempotente quando a rodada ja esta InProgress`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled.copy(status = RoundStatus.InProgress)
        coEvery { userRepo.findManagers() } returns listOf(manager(1, "ana"))
        coEvery { readinessRepo.readyUserIds(5) } returns setOf(UserId(1))

        val result = service.start()

        assertEquals(StartRoundResult.Started(5), result)
        // Já estava InProgress: não regrava.
        coVerify(exactly = 0) { leagueRepo.saveRound(any()) }
    }

    @Test
    fun `start retorna AlreadyFinished quando a rodada ja foi simulada`() = runTest {
        coEvery { leagueRepo.findLatest() } returns scheduled.copy(status = RoundStatus.Finished)

        val result = service.start()

        assertEquals(StartRoundResult.AlreadyFinished(5), result)
        coVerify(exactly = 0) { leagueRepo.saveRound(any()) }
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
}
