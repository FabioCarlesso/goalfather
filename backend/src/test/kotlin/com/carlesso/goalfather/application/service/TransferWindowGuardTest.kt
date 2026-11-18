package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.application.port.out.PlayerRegistrationRepository
import com.carlesso.goalfather.application.port.out.TransferCompletion
import com.carlesso.goalfather.application.port.out.TransferOfferRepository
import com.carlesso.goalfather.application.port.out.TransferWindowRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.ExpiryReason
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.OfferId
import com.carlesso.goalfather.domain.model.OfferStatus
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.TransferOffer
import com.carlesso.goalfather.domain.model.cents
import com.carlesso.goalfather.domain.model.world.TransferWindow
import com.carlesso.goalfather.domain.model.world.TransferWindowScope
import com.carlesso.goalfather.domain.result.OfferResult
import com.carlesso.goalfather.domain.result.TransferResult
import com.carlesso.goalfather.domain.rules.OFFER_DEADLINE_SLOTS
import com.carlesso.goalfather.domain.rules.RegistrationLimit
import com.carlesso.goalfather.domain.rules.WindowStatus
import com.carlesso.goalfather.test.InMemoryCatalogRepository
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import com.carlesso.goalfather.test.md
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Janelas de transferência nos serviços (issue #137): compra, venda rápida,
 * proposta (criação e aceite) e a guarda que a virada usa. Sem Spring: os
 * repositórios de janela e de registro são fakes em memória, o relógio é uma
 * variável que o teste move.
 */
class TransferWindowGuardTest {

    private val windows = listOf(
        TransferWindow(Countries.BRAZIL, day(2026, 1, 5), day(2026, 3, 3)),
        TransferWindow(Countries.BRAZIL, day(2026, 3, 4), day(2026, 3, 27), TransferWindowScope.DOMESTIC_ONLY),
        TransferWindow(Countries.BRAZIL, day(2026, 7, 20), day(2026, 9, 11)),
        TransferWindow(Countries.ENGLAND, day(2026, 1, 1), day(2026, 2, 2)),
        TransferWindow(Countries.ENGLAND, day(2026, 6, 15), day(2026, 9, 1)),
    )

    /** Registros em memória: o que o limite conta. */
    private class InMemoryRegistrations : PlayerRegistrationRepository {
        val rows = mutableListOf<Triple<PlayerId, ClubId, LocalDate>>()

        override suspend fun datesOf(playerId: PlayerId): List<LocalDate> =
            rows.filter { it.first == playerId }.map { it.third }

        override suspend fun record(playerId: PlayerId, clubId: ClubId, date: LocalDate) {
            rows += Triple(playerId, clubId, date)
        }
    }

    private val registrations = InMemoryRegistrations()
    private var today: LocalDate = day(2026, 2, 1)
    private val guard = TransferWindowGuard(
        object : TransferWindowRepository {
            override suspend fun findAll(): List<TransferWindow> = windows
        },
        registrations,
    ) { today }

    private val clubRepo: ClubRepository = mockk()
    private val marketRepo: MarketRepository = mockk()

    private fun day(y: Int, m: Int, d: Int): LocalDate = LocalDate.of(y, m, d)

    // ─── Compra no mercado ──────────────────────────────────────────────

    private val buyer = makeClub(id = 1, squadSize = 11, ownerId = 10)
    private val target = makePlayer(99)

    private fun stubBuy() {
        coEvery { clubRepo.findById(buyer.id) } returns buyer
        coEvery { marketRepo.findEntry(target.id) } returns MarketEntry(target, 100_000_00.cents)
        coEvery { marketRepo.claim(target.id) } returns true
        coEvery { clubRepo.save(any()) } answers { ClubSaveResult.Saved(firstArg()) }
    }

    private fun buyService() = BuyPlayerService(
        clubRepo,
        marketRepo,
        leagueRepo = mockk { coEvery { findLatest() } returns null; coEvery { currentStandings() } returns emptyList() },
        catalogRepo = InMemoryCatalogRepository(),
        windows = guard,
    )

    @Test
    fun `compra fora da janela devolve TransferWindowClosed com a proxima abertura e nao mexe em nada`() = runTest {
        stubBuy()
        today = day(2026, 4, 1)

        val result = buyService().execute(buyer.id, target.id)

        assertEquals(TransferResult.TransferWindowClosed(day(2026, 7, 20)), result)
        coVerify(exactly = 0) { marketRepo.claim(any()) }
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `compra na janela registra o jogador e a segunda na mesma janela esbarra no limite`() = runTest {
        stubBuy()
        val service = buyService()

        assertIs<TransferResult.Success>(service.execute(buyer.id, target.id))
        assertEquals(listOf(Triple(target.id, buyer.id, today)), registrations.rows)

        // Mesmo jogador de volta ao mercado (venda rápida) e outra compra na janela.
        assertEquals(
            TransferResult.TransferLimitReached(target.id, RegistrationLimit.ONE_PER_WINDOW),
            service.execute(buyer.id, target.id),
        )
    }

    @Test
    fun `venda rapida fora da janela do vendedor e recusada`() = runTest {
        val seller = makeClub(id = 1, squadSize = 12)
        coEvery { clubRepo.findById(seller.id) } returns seller
        today = day(2026, 5, 1)

        val result = SellPlayerService(
            clubRepo, marketRepo, InMemoryCatalogRepository(), mockk { coEvery { findLatest() } returns null }, windows = guard,
        ).execute(seller.id, seller.squad.last().id)

        assertEquals(TransferResult.TransferWindowClosed(day(2026, 7, 20)), result)
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    // ─── Propostas ──────────────────────────────────────────────────────

    private val offerRepo: TransferOfferRepository = mockk()
    private val leagueRepo: LeagueRepository = mockk()
    private val brSeller: Club = makeClub(id = 2, name = "Vendedor BR", squadSize = 0, ownerId = 20)
        .copy(squad = (101L..112L).map { makePlayer(it) })
    private val enSeller: Club = brSeller.copy(id = ClubId(3), name = "Seller EN", country = Countries.ENGLAND)

    @Test
    fun `na janela so domestica a proposta a clube de outro pais e recusada e a do mesmo pais aceita`() = runTest {
        today = day(2026, 3, 10)
        val service = TransferOfferService(offerRepo, clubRepo, leagueRepo, guard)
        for (club in listOf(buyer, brSeller, enSeller)) coEvery { clubRepo.findById(club.id) } returns club
        coEvery { offerRepo.findPendingForPlayer(any()) } returns emptyList()
        coEvery { offerRepo.create(any()) } answers { firstArg<TransferOffer>().copy(id = OfferId(1)) }
        val player = brSeller.squad[4].id

        assertEquals(
            OfferResult.TransferWindowClosed(day(2026, 7, 20)),
            service.create(buyer.id, enSeller.id, player, 400_000_00.cents, requesterId = 10),
        )
        assertIs<OfferResult.Success>(service.create(buyer.id, brSeller.id, player, 400_000_00.cents, requesterId = 10))
    }

    @Test
    fun `aceite fora da janela do comprador e recusado e a proposta segue pendente`() = runTest {
        today = day(2026, 4, 1)
        val service = TransferOfferService(offerRepo, clubRepo, leagueRepo, guard)
        for (club in listOf(buyer, brSeller)) coEvery { clubRepo.findById(club.id) } returns club
        val offer = offer(brSeller)
        coEvery { offerRepo.findById(offer.id) } returns offer
        coEvery { leagueRepo.findLatest() } returns Round(matchday = md(4), season = 2026, status = RoundStatus.Scheduled, matches = emptyList())

        assertEquals(OfferResult.TransferWindowClosed(day(2026, 7, 20)), service.accept(offer.id, requesterId = 20))
        coVerify(exactly = 0) { offerRepo.completeTransfer(any(), any(), any(), any()) }
        coVerify(exactly = 0) { offerRepo.saveIfPending(any()) }
    }

    @Test
    fun `aceite na janela registra o jogador no comprador`() = runTest {
        val service = TransferOfferService(offerRepo, clubRepo, leagueRepo, guard)
        for (club in listOf(buyer, brSeller)) coEvery { clubRepo.findById(club.id) } returns club
        val offer = offer(brSeller)
        coEvery { offerRepo.findById(offer.id) } returns offer
        coEvery { offerRepo.findPendingForPlayer(offer.playerId) } returns listOf(offer)
        coEvery { leagueRepo.findLatest() } returns Round(matchday = md(4), season = 2026, status = RoundStatus.Scheduled, matches = emptyList())
        coEvery { offerRepo.completeTransfer(any(), any(), any(), any()) } returns TransferCompletion.Completed

        assertIs<OfferResult.Success>(service.accept(offer.id, requesterId = 20))
        assertEquals(listOf(Triple(offer.playerId, buyer.id, today)), registrations.rows)
    }

    // ─── Virada do encaixe e selo ───────────────────────────────────────

    @Test
    fun `quando a janela fecha as pendentes expiram por WINDOW_CLOSED`() = runTest {
        val clubs = listOf(buyer, brSeller, enSeller).associateBy { it.id }
        val domestic = offer(brSeller)
        val international = offer(enSeller).copy(id = OfferId(8))

        // Janela só doméstica: só a proposta a clube de fora morre.
        today = day(2026, 3, 4)
        assertEquals(
            listOf(international.copy(status = OfferStatus.Expired(ExpiryReason.WINDOW_CLOSED))),
            guard.closedByWindow(listOf(domestic, international), clubs),
        )
        // Janela fechada: as duas.
        today = day(2026, 3, 28)
        assertEquals(listOf(domestic.id, international.id), guard.closedByWindow(listOf(domestic, international), clubs).map { it.id })
        // Sem relógio (guarda sem restrição): nada.
        assertEquals(emptyList(), TransferWindowGuard.UNRESTRICTED.closedByWindow(listOf(domestic), clubs))
    }

    @Test
    fun `selo da janela e o da federacao do clube`() = runTest {
        today = day(2026, 6, 20)
        assertEquals(WindowStatus.Closed(day(2026, 7, 20)), guard.status(buyer))
        assertEquals(WindowStatus.Open(day(2026, 9, 1), TransferWindowScope.INTERNATIONAL), guard.status(enSeller))
    }

    private fun offer(seller: Club) = TransferOffer(
        id = OfferId(7),
        buyerClubId = buyer.id,
        buyerClubName = buyer.name,
        sellerClubId = seller.id,
        sellerClubName = seller.name,
        playerId = seller.squad[4].id,
        playerName = seller.squad[4].name,
        amount = 400_000_00.cents,
        slotsLeft = OFFER_DEADLINE_SLOTS,
    )
}
