package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.TransferResult
import com.carlesso.goalfather.test.makeClub
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SellPlayerServiceTest {

    private val clubRepo: ClubRepository = mockk()
    private val marketRepo: MarketRepository = mockk()
    private val service = SellPlayerService(
        clubRepo, marketRepo,
        salePriceCalc = { 200_000_00 }, // fixo para determinismo
    )

    @Test
    fun `venda bem-sucedida credita caixa, remove do elenco e listou no mercado`() = runTest {
        val club = makeClub(cash = 500_000_00, squadSize = 11)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { clubRepo.save(any()) } answers { firstArg() }
        coEvery { marketRepo.add(any()) } just Runs

        val targetId = club.squad.first().id
        val result = service.execute(ClubId(1), targetId)

        assertIs<TransferResult.Success>(result)
        assertEquals(700_000_00, result.club.cash)
        assertEquals(10, result.club.squad.size)
        assertEquals(targetId, result.player.id)

        coVerify(exactly = 1) { clubRepo.save(any()) }
        coVerify(exactly = 1) { marketRepo.add(any()) }
    }

    @Test
    fun `jogador fora do elenco retorna PlayerNotAvailable`() = runTest {
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub()

        val result = service.execute(ClubId(1), PlayerId(999))

        assertIs<TransferResult.PlayerNotAvailable>(result)
        coVerify(exactly = 0) { clubRepo.save(any()) }
        coVerify(exactly = 0) { marketRepo.add(any()) }
    }

    @Test
    fun `clube inexistente retorna PlayerNotAvailable`() = runTest {
        coEvery { clubRepo.findById(ClubId(999)) } returns null

        val result = service.execute(ClubId(999), PlayerId(1))

        assertIs<TransferResult.PlayerNotAvailable>(result)
    }
}
