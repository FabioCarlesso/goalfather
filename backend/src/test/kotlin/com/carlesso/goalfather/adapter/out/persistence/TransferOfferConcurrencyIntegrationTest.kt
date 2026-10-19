package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.TransferOfferRepository
import com.carlesso.goalfather.application.service.TransferOfferService
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.ExpiryReason
import com.carlesso.goalfather.domain.model.OfferStatus
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.result.OfferResult
import com.carlesso.goalfather.test.makePlayer
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Propostas entre técnicos (issue #63) contra o H2 real, no padrão do
 * [MarketConcurrencyIntegrationTest]: o caminho feliz atômico e as CORRIDAS —
 * dois aceites pelo mesmo jogador, e dois técnicos vendendo um ao outro ao
 * mesmo tempo (o cenário de deadlock que a ordem dos locks evita).
 *
 * O service é montado aqui com os repositórios REAIS de clube e proposta e uma
 * liga falsa sem rodada: o contexto Spring é compartilhado com outros testes,
 * que podem deixar a rodada corrente `InProgress` — e a guarda da rodada não é
 * o assunto deste teste. Ids na faixa 970_xxx, sem colidir com seed nem vizinhos.
 */
@SpringBootTest
class TransferOfferConcurrencyIntegrationTest {

    @Autowired private lateinit var clubRepo: ClubRepository
    @Autowired private lateinit var offerRepo: TransferOfferRepository

    private val noRound: LeagueRepository = mockk { coEvery { findLatest() } returns null }
    private val service by lazy { TransferOfferService(offerRepo, clubRepo, noRound) }

    /**
     * Clube humano (dono = [id]) com caixa de sobra e 12 jogadores — um goleiro
     * e onze de linha, ids `id*100+1..12`: pode vender qualquer um de linha
     * sem ficar com elenco inválido.
     */
    private fun newHumanClub(id: Long, cash: Long = 10_000_000_00): Club = runBlocking {
        val squad = (1L..12L).map { n ->
            makePlayer(id * 100 + n, position = if (n == 1L) Position.GK else Position.MF)
        }
        val saved = clubRepo.save(
            Club(
                id = ClubId(id),
                name = "Proposta FC $id",
                cash = cash,
                stadiumCapacity = 10_000,
                squad = squad,
                ownerId = id,
            ),
        )
        assertIs<ClubSaveResult.Saved>(saved).club
    }

    private fun Club.lineMember(n: Long): Player = squad.first { it.id.value == id.value * 100 + n }

    private fun offer(buyer: Club, seller: Club, player: Player, amount: Long) = runBlocking {
        val result = service.create(buyer.id, seller.id, player.id, amount, requesterId = buyer.ownerId!!)
        assertIs<OfferResult.Success>(result).offer
    }

    private fun club(id: ClubId): Club = runBlocking { clubRepo.findById(id)!! }

    /** Dispara [tasks] ao mesmo tempo (latch) e devolve os resultados na ordem de [tasks]. */
    private fun <T> race(vararg tasks: suspend () -> T): List<T> {
        val pool = Executors.newFixedThreadPool(tasks.size)
        val start = CountDownLatch(1)
        val results = Collections.synchronizedMap(mutableMapOf<Int, T>())
        tasks.forEachIndexed { index, task ->
            pool.submit {
                start.await()
                results[index] = runBlocking { task() }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS), "corrida não terminou — deadlock?")
        return tasks.indices.map { results.getValue(it) }
    }

    @Test
    fun `aceite move jogador e dinheiro de uma vez`() = runBlocking {
        val buyer = newHumanClub(970_001)
        val seller = newHumanClub(970_002, cash = 1_000_00)
        val target = seller.lineMember(5)
        val pending = offer(buyer, seller, target, amount = 750_000_00)

        val result = service.accept(pending.id, requesterId = seller.ownerId!!)

        assertIs<OfferResult.Success>(result)
        val buyerAfter = club(buyer.id)
        val sellerAfter = club(seller.id)
        assertTrue(buyerAfter.squad.any { it.id == target.id }, "jogador entra no comprador")
        assertTrue(sellerAfter.squad.none { it.id == target.id }, "jogador sai do vendedor")
        assertEquals(buyer.cash - 750_000_00, buyerAfter.cash)
        assertEquals(seller.cash + 750_000_00, sellerAfter.cash)
        assertEquals(OfferStatus.Accepted, offerRepo.findById(pending.id)!!.status)
    }

    @Test
    fun `dois aceites simultaneos pelo mesmo jogador - exatamente um vence`() {
        val seller = newHumanClub(970_010, cash = 0)
        val buyerA = newHumanClub(970_011)
        val buyerB = newHumanClub(970_012)
        val target = seller.lineMember(5)
        val offerA = offer(buyerA, seller, target, amount = 300_000_00)
        val offerB = offer(buyerB, seller, target, amount = 400_000_00)

        val results = race(
            { service.accept(offerA.id, requesterId = seller.ownerId!!) },
            { service.accept(offerB.id, requesterId = seller.ownerId!!) },
        )

        assertEquals(1, results.count { it is OfferResult.Success }, "exatamente um aceite vence: $results")
        // O perdedor relê a sua proposta e a encontra expirada pelo vencedor.
        val loser = results.single { it !is OfferResult.Success }
        assertIs<OfferResult.OfferExpired>(loser)
        assertEquals(ExpiryReason.PLAYER_TRANSFERRED, loser.reason)

        // Banco consistente: jogador em UM clube, dinheiro de UMA proposta.
        val (winnerOffer, loserOffer) =
            if (results[0] is OfferResult.Success) offerA to offerB else offerB to offerA
        val winner = club(winnerOffer.buyerClubId)
        val loserClub = club(loserOffer.buyerClubId)
        assertTrue(winner.squad.any { it.id == target.id }, "jogador no comprador vencedor")
        assertTrue(loserClub.squad.none { it.id == target.id }, "jogador não duplica no perdedor")
        assertTrue(club(seller.id).squad.none { it.id == target.id }, "jogador saiu do vendedor")
        assertEquals(winnerOffer.amount, club(seller.id).cash, "vendedor recebe UMA proposta")
        assertEquals(10_000_000_00 - winnerOffer.amount, winner.cash)
        assertEquals(10_000_000_00, loserClub.cash, "perdedor não paga nada")
        runBlocking {
            assertEquals(OfferStatus.Accepted, offerRepo.findById(winnerOffer.id)!!.status)
            assertEquals(
                OfferStatus.Expired(ExpiryReason.PLAYER_TRANSFERRED),
                offerRepo.findById(loserOffer.id)!!.status,
            )
        }
    }

    @Test
    fun `dois tecnicos vendendo um ao outro ao mesmo tempo nao travam`() {
        // A vende para B e B vende para A, aceites simultâneos. Sem ordem fixa
        // de lock, cada transação seguraria o próprio clube esperando o outro.
        val a = newHumanClub(970_020)
        val b = newHumanClub(970_021)
        val fromA = a.lineMember(5)
        val fromB = b.lineMember(6)
        val offerToA = offer(buyer = b, seller = a, player = fromA, amount = 200_000_00)
        val offerToB = offer(buyer = a, seller = b, player = fromB, amount = 250_000_00)

        val results = race(
            { service.accept(offerToA.id, requesterId = a.ownerId!!) },
            { service.accept(offerToB.id, requesterId = b.ownerId!!) },
        )

        // As duas transferências são independentes: ambas passam (a que perde
        // o lock do clube relê e reaplica).
        assertTrue(results.all { it is OfferResult.Success }, "as duas devem passar: $results")
        val aAfter = club(a.id)
        val bAfter = club(b.id)
        assertTrue(aAfter.squad.any { it.id == fromB.id } && aAfter.squad.none { it.id == fromA.id })
        assertTrue(bAfter.squad.any { it.id == fromA.id } && bAfter.squad.none { it.id == fromB.id })
        assertEquals(a.cash + 200_000_00 - 250_000_00, aAfter.cash)
        assertEquals(b.cash + 250_000_00 - 200_000_00, bAfter.cash)
    }

    @Test
    fun `recusa e retirada encerram a proposta e nao mexem nos clubes`() = runBlocking {
        val buyer = newHumanClub(970_030)
        val seller = newHumanClub(970_031)
        val rejected = offer(buyer, seller, seller.lineMember(4), amount = 100_000_00)
        val withdrawn = offer(buyer, seller, seller.lineMember(5), amount = 100_000_00)

        assertIs<OfferResult.Success>(service.reject(rejected.id, requesterId = seller.ownerId!!))
        assertIs<OfferResult.Success>(service.withdraw(withdrawn.id, requesterId = buyer.ownerId!!))

        assertEquals(OfferStatus.Rejected, offerRepo.findById(rejected.id)!!.status)
        assertEquals(OfferStatus.Withdrawn, offerRepo.findById(withdrawn.id)!!.status)
        // Estado terminal não volta: aceitar a recusada é erro de valor.
        assertIs<OfferResult.OfferNotPending>(service.accept(rejected.id, requesterId = seller.ownerId!!))
        assertEquals(buyer.cash, club(buyer.id).cash)
        assertEquals(12, club(seller.id).squad.size)
        // As duas aparecem na lista de cada lado, da mais nova para a mais antiga.
        assertEquals(listOf(withdrawn.id, rejected.id), offerRepo.findByClub(seller.id).map { it.id })
    }

    @Test
    fun `prazo vencido na virada nao sobrescreve um aceite`() = runBlocking {
        // `saveIfPending` é o compare-and-set da máquina de estados: o relógio
        // da rodada chegando depois de um aceite não "desaceita" a proposta.
        val buyer = newHumanClub(970_040)
        val seller = newHumanClub(970_041)
        val pending = offer(buyer, seller, seller.lineMember(5), amount = 100_000_00)
        val staleSweep = pending.copy(roundsLeft = 1).afterRound()

        assertIs<OfferResult.Success>(service.accept(pending.id, requesterId = seller.ownerId!!))

        assertEquals(false, offerRepo.saveIfPending(staleSweep))
        assertEquals(OfferStatus.Accepted, offerRepo.findById(pending.id)!!.status)
    }
}
