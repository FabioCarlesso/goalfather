package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.result.TicketPriceResult
import com.carlesso.goalfather.domain.rules.DEFAULT_TICKET_PRICE_CENTS
import com.carlesso.goalfather.domain.rules.MAX_TICKET_PRICE_CENTS
import com.carlesso.goalfather.domain.rules.MIN_TICKET_PRICE_CENTS
import com.carlesso.goalfather.test.makeClub
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Preço do ingresso definido pelo técnico (issue #59). */
class SetTicketPriceServiceTest {

    private val clubRepo: ClubRepository = mockk()
    private val service = SetTicketPriceService(clubRepo)

    private val owner = 7L

    @Test
    fun `grava o preco escolhido no clube`() = runTest {
        val club = makeClub(ownerId = owner)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { clubRepo.updateTicketPrice(ClubId(1), 80_00) } returns club.copy(ticketPriceCents = 80_00)

        val result = service.execute(ClubId(1), owner, 80_00)

        assertIs<TicketPriceResult.Success>(result)
        assertEquals(80_00, result.club.ticketPriceCents)
        coVerify(exactly = 1) { clubRepo.updateTicketPrice(ClubId(1), 80_00) }
    }

    @Test
    fun `clube novo comeca no preco padrao`() = runTest {
        assertEquals(DEFAULT_TICKET_PRICE_CENTS, makeClub().ticketPriceCents)
    }

    @Test
    fun `as bordas da faixa sao aceitas`() = runTest {
        val club = makeClub(ownerId = owner)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { clubRepo.updateTicketPrice(ClubId(1), any()) } answers { club.copy(ticketPriceCents = secondArg()) }

        assertIs<TicketPriceResult.Success>(service.execute(ClubId(1), owner, MIN_TICKET_PRICE_CENTS))
        assertIs<TicketPriceResult.Success>(service.execute(ClubId(1), owner, MAX_TICKET_PRICE_CENTS))
    }

    @Test
    fun `preco fora da faixa volta como valor e nao grava`() = runTest {
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub(ownerId = owner)

        val acima = service.execute(ClubId(1), owner, MAX_TICKET_PRICE_CENTS + 1)
        assertIs<TicketPriceResult.PriceOutOfRange>(acima)
        assertEquals(MAX_TICKET_PRICE_CENTS + 1, acima.price)
        assertEquals(MIN_TICKET_PRICE_CENTS, acima.min)
        assertEquals(MAX_TICKET_PRICE_CENTS, acima.max)

        assertIs<TicketPriceResult.PriceOutOfRange>(
            service.execute(ClubId(1), owner, MIN_TICKET_PRICE_CENTS - 1),
        )
        // Ingresso de graça também está fora da faixa — o piso é R$ 10.
        assertIs<TicketPriceResult.PriceOutOfRange>(service.execute(ClubId(1), owner, 0))

        coVerify(exactly = 0) { clubRepo.updateTicketPrice(any(), any()) }
    }

    @Test
    fun `repetir o preco atual nao grava de novo`() = runTest {
        val club = makeClub(ownerId = owner).copy(ticketPriceCents = 120_00)
        coEvery { clubRepo.findById(ClubId(1)) } returns club

        assertIs<TicketPriceResult.Success>(service.execute(ClubId(1), owner, 120_00))
        coVerify(exactly = 0) { clubRepo.updateTicketPrice(any(), any()) }
    }

    @Test
    fun `clube inexistente retorna ClubNotFound`() = runTest {
        coEvery { clubRepo.findById(ClubId(9)) } returns null

        assertIs<TicketPriceResult.ClubNotFound>(service.execute(ClubId(9), owner, 60_00))
        coVerify(exactly = 0) { clubRepo.updateTicketPrice(any(), any()) }
    }

    @Test
    fun `solicitante que nao e dono retorna NotOwner mesmo com preco invalido`() = runTest {
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub(ownerId = owner)

        // Posse é checada ANTES da faixa: responder PriceOutOfRange aqui
        // contaria a faixa praticada por um clube que o requisitante nem vê.
        assertIs<TicketPriceResult.NotOwner>(
            service.execute(ClubId(1), owner + 1, MAX_TICKET_PRICE_CENTS + 1),
        )
        coVerify(exactly = 0) { clubRepo.updateTicketPrice(any(), any()) }
    }

    @Test
    fun `grava so a coluna do preco - nunca le-modifica-grava o clube (revisao do PR 92)`() = runTest {
        // Entre a leitura e a gravação, a rodada creditou a bilheteria. O preço
        // vai num UPDATE de coluna: o clube devolvido é o RELIDO, com o caixa da
        // rodada, e não há `save` do agregado que pudesse pisar nele — nem
        // conflito a reprocessar.
        val stale = makeClub(ownerId = owner, cash = 1_000_000_00)
        val fresh = stale.copy(cash = 1_250_000_00, ticketPriceCents = 80_00, version = stale.version + 2)
        coEvery { clubRepo.findById(ClubId(1)) } returns stale
        coEvery { clubRepo.updateTicketPrice(ClubId(1), 80_00) } returns fresh

        val result = service.execute(ClubId(1), owner, 80_00)

        assertIs<TicketPriceResult.Success>(result)
        assertEquals(fresh, result.club)
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `clube que some entre a leitura e o UPDATE retorna ClubNotFound`() = runTest {
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub(ownerId = owner)
        coEvery { clubRepo.updateTicketPrice(ClubId(1), 80_00) } returns null

        assertIs<TicketPriceResult.ClubNotFound>(service.execute(ClubId(1), owner, 80_00))
    }
}
