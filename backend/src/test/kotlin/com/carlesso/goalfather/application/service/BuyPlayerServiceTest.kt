package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.result.TransferResult
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BuyPlayerServiceTest {

    private val clubRepo: ClubRepository = mockk()
    private val marketRepo: MarketRepository = mockk()
    private val service = BuyPlayerService(clubRepo, marketRepo, squadMaxSize = 25)

    @Test
    fun `compra bem-sucedida debita caixa, adiciona ao elenco e remove do mercado`() = runTest {
        val club = makeClub(cash = 1_000_000_00, squadSize = 11)
        val target = makePlayer(99, Position.FW, overall = 88)
        val entry = MarketEntry(player = target, price = 400_000_00)

        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { marketRepo.findEntry(PlayerId(99)) } returns entry
        val savedClub = slot<com.carlesso.goalfather.domain.model.Club>()
        coEvery { clubRepo.save(capture(savedClub)) } answers { savedClub.captured }
        coEvery { marketRepo.remove(PlayerId(99)) } just Runs

        val result = service.execute(ClubId(1), PlayerId(99))

        assertIs<TransferResult.Success>(result)
        assertEquals(600_000_00, result.club.cash)
        assertEquals(12, result.club.squad.size)
        assertEquals(target, result.player)

        coVerify(exactly = 1) { clubRepo.save(any()) }
        coVerify(exactly = 1) { marketRepo.remove(PlayerId(99)) }
    }

    @Test
    fun `caixa insuficiente retorna InsufficientFunds e nao persiste`() = runTest {
        val club = makeClub(cash = 100_000_00)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { marketRepo.findEntry(PlayerId(99)) } returns
            MarketEntry(player = makePlayer(99), price = 500_000_00)

        val result = service.execute(ClubId(1), PlayerId(99))

        assertIs<TransferResult.InsufficientFunds>(result)
        assertEquals(100_000_00, result.available)
        assertEquals(500_000_00, result.required)

        coVerify(exactly = 0) { clubRepo.save(any()) }
        coVerify(exactly = 0) { marketRepo.remove(any()) }
    }

    @Test
    fun `elenco cheio retorna SquadFull`() = runTest {
        val club = makeClub(cash = 10_000_000_00, squadSize = 25)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { marketRepo.findEntry(PlayerId(99)) } returns
            MarketEntry(player = makePlayer(99), price = 100_00)

        val result = service.execute(ClubId(1), PlayerId(99))

        assertIs<TransferResult.SquadFull>(result)
        assertEquals(25, result.maxSize)

        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `clube inexistente retorna PlayerNotAvailable`() = runTest {
        coEvery { clubRepo.findById(ClubId(999)) } returns null

        val result = service.execute(ClubId(999), PlayerId(1))

        assertIs<TransferResult.PlayerNotAvailable>(result)
        assertEquals(PlayerId(1), result.playerId)
    }

    @Test
    fun `jogador fora do mercado retorna PlayerNotAvailable`() = runTest {
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub()
        coEvery { marketRepo.findEntry(PlayerId(99)) } returns null

        val result = service.execute(ClubId(1), PlayerId(99))

        assertIs<TransferResult.PlayerNotAvailable>(result)
    }
}
