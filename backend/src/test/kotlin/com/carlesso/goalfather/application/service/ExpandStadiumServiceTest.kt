package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.result.ExpandStadiumResult
import com.carlesso.goalfather.domain.rules.MIN_STADIUM_EXPANSION_SEATS
import com.carlesso.goalfather.test.makeClub
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Ampliação do estádio como use case (issue #79). */
class ExpandStadiumServiceTest {

    private val clubRepo: ClubRepository = mockk()
    private val service = ExpandStadiumService(clubRepo)

    private val owner = 7L

    @Test
    fun `mil assentos custam R$ 100 mil e somam a capacidade`() = runTest {
        val club = makeClub(ownerId = owner, cash = 1_075_000_00)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        val saved = slot<Club>()
        coEvery { clubRepo.save(capture(saved)) } answers { ClubSaveResult.Saved(saved.captured) }

        val result = service.execute(ClubId(1), owner, 1_000)

        assertIs<ExpandStadiumResult.Success>(result)
        assertEquals(975_000_00, saved.captured.cash)
        assertEquals(club.stadiumCapacity + 1_000, saved.captured.stadiumCapacity)
        assertEquals(saved.captured, result.club)
    }

    @Test
    fun `abaixo do minimo volta como valor e nao grava - inclusive zero e negativos`() = runTest {
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub(ownerId = owner)

        for (seats in listOf(MIN_STADIUM_EXPANSION_SEATS - 1, 0, -4_000, -10_000)) {
            val result = service.execute(ClubId(1), owner, seats)
            assertIs<ExpandStadiumResult.BelowMinimumSeats>(result, "seats=$seats")
            assertEquals(seats, result.requested)
            assertEquals(MIN_STADIUM_EXPANSION_SEATS, result.minimum)
        }
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `caixa insuficiente volta com necessario e disponivel`() = runTest {
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub(ownerId = owner, cash = 99_999_99)

        val result = service.execute(ClubId(1), owner, 1_000)

        assertIs<ExpandStadiumResult.InsufficientFunds>(result)
        assertEquals(100_000_00, result.required)
        assertEquals(99_999_99, result.available)
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `clube inexistente retorna ClubNotFound`() = runTest {
        coEvery { clubRepo.findById(ClubId(9)) } returns null

        assertIs<ExpandStadiumResult.ClubNotFound>(service.execute(ClubId(9), owner, 1_000))
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `solicitante que nao e dono retorna NotOwner mesmo com pedido invalido`() = runTest {
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub(ownerId = owner)

        // Posse é checada ANTES do mínimo: o clube alheio não recebe resposta de regra.
        assertIs<ExpandStadiumResult.NotOwner>(service.execute(ClubId(1), owner + 1, -10_000))
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `conflito de lock otimista volta como valor, sem retry automatico (issue 89)`() = runTest {
        // A ampliação debita o caixa que o técnico VIU. Se a rodada gravou o
        // clube no meio do caminho, reaplicar em silêncio decidiria por ele.
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub(ownerId = owner, cash = 1_075_000_00)
        coEvery { clubRepo.save(any()) } returns ClubSaveResult.Conflict(ClubId(1))

        val result = service.execute(ClubId(1), owner, 1_000)

        assertIs<ExpandStadiumResult.Conflict>(result)
        assertEquals(ClubId(1), result.clubId)
        coVerify(exactly = 1) { clubRepo.save(any()) }
    }
}
