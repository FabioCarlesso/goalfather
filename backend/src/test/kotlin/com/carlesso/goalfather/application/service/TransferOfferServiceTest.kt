package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.TransferCompletion
import com.carlesso.goalfather.application.port.out.TransferOfferRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.ExpiryReason
import com.carlesso.goalfather.domain.model.OfferId
import com.carlesso.goalfather.domain.model.OfferStatus
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.TransferOffer
import com.carlesso.goalfather.domain.result.OfferResult
import com.carlesso.goalfather.domain.rules.OFFER_DEADLINE_ROUNDS
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TransferOfferServiceTest {

    private val offerRepo: TransferOfferRepository = mockk()
    private val clubRepo: ClubRepository = mockk()
    private val leagueRepo: LeagueRepository = mockk()
    private val service = TransferOfferService(offerRepo, clubRepo, leagueRepo)

    private val buyerOwner = 10L
    private val sellerOwner = 20L

    private val buyer = makeClub(id = 1, name = "Comprador", cash = 1_000_000_00, squadSize = 11, ownerId = buyerOwner)

    /** 12 jogadores (ids 101..112): pode vender um de linha sem ficar inválido. */
    private val seller = makeClub(id = 2, name = "Vendedor", cash = 50_000_00, squadSize = 0, ownerId = sellerOwner)
        .copy(squad = (101L..112L).map { makePlayer(it) })
    private val target = seller.squad[4]

    private val pending = TransferOffer(
        id = OfferId(7),
        buyerClubId = buyer.id,
        buyerClubName = buyer.name,
        sellerClubId = seller.id,
        sellerClubName = seller.name,
        playerId = target.id,
        playerName = target.name,
        amount = 400_000_00,
        roundsLeft = OFFER_DEADLINE_ROUNDS,
    )

    private fun stubClubs(vararg clubs: Club) {
        for (club in clubs) coEvery { clubRepo.findById(club.id) } returns club
    }

    private fun stubRound(status: RoundStatus) {
        coEvery { leagueRepo.findLatest() } returns Round(number = 4, season = 2026, status = status, matches = emptyList())
    }

    // ─── create ─────────────────────────────────────────────────────────

    @Test
    fun `proposta valida e gravada pendente com o prazo e os nomes da foto`() = runTest {
        stubClubs(buyer, seller)
        coEvery { offerRepo.findPendingForPlayer(target.id) } returns emptyList()
        val created = slot<TransferOffer>()
        coEvery { offerRepo.create(capture(created)) } answers { created.captured.copy(id = OfferId(99)) }

        val result = service.create(buyer.id, seller.id, target.id, 400_000_00, requesterId = buyerOwner)

        assertIs<OfferResult.Success>(result)
        assertEquals(OfferId(99), result.offer.id)
        assertEquals(OfferStatus.Pending, created.captured.status)
        assertEquals(OFFER_DEADLINE_ROUNDS, created.captured.roundsLeft)
        assertEquals(target.name, created.captured.playerName)
        assertEquals("Vendedor", created.captured.sellerClubName)
    }

    @Test
    fun `segunda pendente do mesmo comprador pelo mesmo jogador e recusada (revisao PR 94)`() = runTest {
        stubClubs(buyer, seller)
        // Pendente de OUTRO comprador pelo mesmo jogador não impede a proposta.
        val rival = pending.copy(id = OfferId(8), buyerClubId = ClubId(3))
        coEvery { offerRepo.findPendingForPlayer(target.id) } returns listOf(rival, pending)

        val result = service.create(buyer.id, seller.id, target.id, 500_000_00, requesterId = buyerOwner)

        assertEquals(OfferResult.OfferAlreadyPending(pending.id), result)
        coVerify(exactly = 0) { offerRepo.create(any()) }
    }

    @Test
    fun `so o dono do comprador propoe`() = runTest {
        stubClubs(buyer, seller)

        assertEquals(OfferResult.NotOwner, service.create(buyer.id, seller.id, target.id, 400_000_00, requesterId = 999))
        coVerify(exactly = 0) { offerRepo.create(any()) }
    }

    @Test
    fun `regra de negocio bloqueia a criacao sem gravar nada`() = runTest {
        stubClubs(buyer, seller)

        val result = service.create(buyer.id, seller.id, target.id, 5_000_000_00, requesterId = buyerOwner)

        assertIs<OfferResult.InsufficientFunds>(result)
        coVerify(exactly = 0) { offerRepo.create(any()) }
    }

    @Test
    fun `clube inexistente e ClubNotFound`() = runTest {
        coEvery { clubRepo.findById(ClubId(404)) } returns null

        assertEquals(
            OfferResult.ClubNotFound(ClubId(404)),
            service.create(ClubId(404), seller.id, target.id, 400_000_00, requesterId = buyerOwner),
        )
    }

    // ─── accept ─────────────────────────────────────────────────────────

    @Test
    fun `aceite move jogador e dinheiro e expira as rivais`() = runTest {
        stubClubs(buyer, seller)
        stubRound(RoundStatus.Scheduled)
        val rival = pending.copy(id = OfferId(8), buyerClubId = ClubId(3), buyerClubName = "Outro")
        coEvery { offerRepo.findById(pending.id) } returns pending
        coEvery { offerRepo.findPendingForPlayer(target.id) } returns listOf(pending, rival)
        val sellerSlot = slot<Club>()
        val buyerSlot = slot<Club>()
        val rivalsSlot = slot<List<TransferOffer>>()
        coEvery {
            offerRepo.completeTransfer(any(), capture(sellerSlot), capture(buyerSlot), capture(rivalsSlot))
        } returns TransferCompletion.Completed

        val result = service.accept(pending.id, requesterId = sellerOwner)

        assertIs<OfferResult.Success>(result)
        assertEquals(OfferStatus.Accepted, result.offer.status)
        assertEquals(50_000_00 + 400_000_00, sellerSlot.captured.cash)
        assertEquals(11, sellerSlot.captured.squad.size)
        assertEquals(1_000_000_00 - 400_000_00, buyerSlot.captured.cash)
        assertEquals(target.id, buyerSlot.captured.squad.last().id)
        // A própria proposta não é rival de si mesma.
        assertEquals(listOf(OfferId(8)), rivalsSlot.captured.map { it.id })
        assertEquals(OfferStatus.Expired(ExpiryReason.PLAYER_TRANSFERRED), rivalsSlot.captured.single().status)
    }

    @Test
    fun `so o dono do vendedor aceita`() = runTest {
        stubClubs(buyer, seller)
        coEvery { offerRepo.findById(pending.id) } returns pending

        assertEquals(OfferResult.NotOwner, service.accept(pending.id, requesterId = buyerOwner))
    }

    @Test
    fun `aceite bloqueado durante a simulacao da rodada`() = runTest {
        stubClubs(buyer, seller)
        stubRound(RoundStatus.InProgress)
        coEvery { offerRepo.findById(pending.id) } returns pending

        assertEquals(OfferResult.RoundInProgress(4), service.accept(pending.id, requesterId = sellerOwner))
        coVerify(exactly = 0) { offerRepo.completeTransfer(any(), any(), any(), any()) }
    }

    @Test
    fun `aceite revalida as regras - caixa do comprador mudou desde a proposta`() = runTest {
        stubClubs(buyer.copy(cash = 100_000_00), seller)
        stubRound(RoundStatus.Scheduled)
        coEvery { offerRepo.findById(pending.id) } returns pending

        assertIs<OfferResult.InsufficientFunds>(service.accept(pending.id, requesterId = sellerOwner))
    }

    @Test
    fun `aceite de jogador que ja saiu do vendedor expira a proposta (revisao PR 94)`() = runTest {
        // Vendido ao mercado depois da proposta: nenhum aceite daria certo.
        stubClubs(buyer, seller.copy(squad = seller.squad.filterNot { it.id == target.id }))
        stubRound(RoundStatus.Scheduled)
        coEvery { offerRepo.findById(pending.id) } returns pending
        val saved = slot<TransferOffer>()
        coEvery { offerRepo.saveIfPending(capture(saved)) } returns true

        val result = service.accept(pending.id, requesterId = sellerOwner)

        assertEquals(OfferResult.OfferExpired(pending.id, ExpiryReason.PLAYER_TRANSFERRED), result)
        assertEquals(OfferStatus.Expired(ExpiryReason.PLAYER_TRANSFERRED), saved.captured.status)
        coVerify(exactly = 0) { offerRepo.completeTransfer(any(), any(), any(), any()) }
    }

    @Test
    fun `aceite com comprador que voltou a IA e recusado (revisao PR 94)`() = runTest {
        stubClubs(buyer.copy(ownerId = null), seller)
        stubRound(RoundStatus.Scheduled)
        coEvery { offerRepo.findById(pending.id) } returns pending

        assertEquals(OfferResult.BuyerNotHuman(buyer.id), service.accept(pending.id, requesterId = sellerOwner))
        coVerify(exactly = 0) { offerRepo.completeTransfer(any(), any(), any(), any()) }
    }

    @Test
    fun `aceite de proposta expirada devolve OfferExpired com o motivo`() = runTest {
        stubClubs(buyer, seller)
        val expired = pending.copy(status = OfferStatus.Expired(ExpiryReason.DEADLINE))
        coEvery { offerRepo.findById(pending.id) } returns expired

        assertEquals(
            OfferResult.OfferExpired(pending.id, ExpiryReason.DEADLINE),
            service.accept(pending.id, requesterId = sellerOwner),
        )
    }

    @Test
    fun `conflito de clube repete do zero e o perdedor encontra a proposta expirada`() = runTest {
        // A 1ª tentativa perde o lock do vendedor para outro aceite pelo mesmo
        // jogador; a releitura encontra esta proposta já expirada por ele.
        stubClubs(buyer, seller)
        stubRound(RoundStatus.Scheduled)
        coEvery { offerRepo.findById(pending.id) } returnsMany listOf(
            pending,
            pending.copy(status = OfferStatus.Expired(ExpiryReason.PLAYER_TRANSFERRED)),
        )
        coEvery { offerRepo.findPendingForPlayer(target.id) } returns listOf(pending)
        coEvery { offerRepo.completeTransfer(any(), any(), any(), any()) } returns
            TransferCompletion.Conflict(seller.id)

        val result = service.accept(pending.id, requesterId = sellerOwner)

        assertEquals(OfferResult.OfferExpired(pending.id, ExpiryReason.PLAYER_TRANSFERRED), result)
        coVerify(exactly = 1) { offerRepo.completeTransfer(any(), any(), any(), any()) }
    }

    @Test
    fun `proposta resolvida entre a leitura e o commit relida para explicar`() = runTest {
        stubClubs(buyer, seller)
        stubRound(RoundStatus.Scheduled)
        coEvery { offerRepo.findById(pending.id) } returnsMany listOf(
            pending,
            pending.copy(status = OfferStatus.Withdrawn),
        )
        coEvery { offerRepo.findPendingForPlayer(target.id) } returns listOf(pending)
        coEvery { offerRepo.completeTransfer(any(), any(), any(), any()) } returns
            TransferCompletion.OfferNoLongerPending

        assertEquals(
            OfferResult.OfferNotPending(pending.id, OfferStatus.Withdrawn),
            service.accept(pending.id, requesterId = sellerOwner),
        )
    }

    // ─── reject / withdraw ──────────────────────────────────────────────

    @Test
    fun `vendedor recusa`() = runTest {
        stubClubs(buyer, seller)
        coEvery { offerRepo.findById(pending.id) } returns pending
        coEvery { offerRepo.saveIfPending(any()) } returns true

        val result = service.reject(pending.id, requesterId = sellerOwner)

        assertIs<OfferResult.Success>(result)
        assertEquals(OfferStatus.Rejected, result.offer.status)
    }

    @Test
    fun `comprador retira mas nao recusa a propria proposta`() = runTest {
        stubClubs(buyer, seller)
        coEvery { offerRepo.findById(pending.id) } returns pending
        coEvery { offerRepo.saveIfPending(any()) } returns true

        assertEquals(OfferStatus.Withdrawn, assertIs<OfferResult.Success>(service.withdraw(pending.id, buyerOwner)).offer.status)
        assertEquals(OfferResult.NotOwner, service.reject(pending.id, requesterId = buyerOwner))
        assertEquals(OfferResult.NotOwner, service.withdraw(pending.id, requesterId = sellerOwner))
    }

    @Test
    fun `recusa que perde a corrida para um aceite devolve OfferNotPending`() = runTest {
        stubClubs(buyer, seller)
        coEvery { offerRepo.findById(pending.id) } returnsMany listOf(pending, pending.copy(status = OfferStatus.Accepted))
        coEvery { offerRepo.saveIfPending(any()) } returns false

        assertEquals(
            OfferResult.OfferNotPending(pending.id, OfferStatus.Accepted),
            service.reject(pending.id, requesterId = sellerOwner),
        )
    }

    @Test
    fun `proposta inexistente e OfferNotFound`() = runTest {
        coEvery { offerRepo.findById(OfferId(404)) } returns null

        assertEquals(OfferResult.OfferNotFound(OfferId(404)), service.accept(OfferId(404), sellerOwner))
        assertEquals(OfferResult.OfferNotFound(OfferId(404)), service.reject(OfferId(404), sellerOwner))
    }
}
