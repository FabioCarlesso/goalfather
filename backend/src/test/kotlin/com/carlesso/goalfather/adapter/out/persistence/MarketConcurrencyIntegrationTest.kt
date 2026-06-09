package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.entity.MarketEntryEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PlayerEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PositionEnum
import com.carlesso.goalfather.adapter.out.persistence.repository.MarketEntryJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.PlayerJpaRepository
import com.carlesso.goalfather.application.port.`in`.BuyPlayerUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.TransferResult
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cobre a compra de jogador no mercado (issue #21) contra o H2 real,
 * focada na CORRIDA: dois clubes humanos tentam comprar o MESMO jogador
 * simultaneamente. O lock otimista (`@Version` em [MarketEntryEntity],
 * traduzido em [MarketPersistenceAdapter]) garante que apenas um vence —
 * o outro recebe `PlayerNotAvailable`, sem deixar o DB inconsistente.
 */
@SpringBootTest
class MarketConcurrencyIntegrationTest {

    @Autowired private lateinit var buyUseCase: BuyPlayerUseCase
    @Autowired private lateinit var marketRepo: MarketRepository
    @Autowired private lateinit var clubRepo: ClubRepository
    @Autowired private lateinit var playerJpa: PlayerJpaRepository
    @Autowired private lateinit var marketJpa: MarketEntryJpaRepository

    /** Cria um clube comprador vazio, com caixa de sobra para a compra. */
    private fun newBuyerClub(id: Long): ClubId = runBlocking {
        clubRepo.save(
            Club(
                id = ClubId(id),
                name = "Buyer $id",
                cash = 10_000_000_00,
                stadiumCapacity = 10_000,
                squad = emptyList(),
            ),
        ).id
    }

    /** Coloca um jogador livre (clubId = null) no mercado e devolve seu id. */
    private fun newMarketPlayer(id: Long, price: Long): PlayerId {
        playerJpa.save(
            PlayerEntity(id = id, clubId = null, name = "Alvo $id", position = PositionEnum.FW, overall = 80),
        )
        marketJpa.save(MarketEntryEntity(playerId = id, price = price))
        return PlayerId(id)
    }

    @Test
    fun `compra normal remove o jogador do mercado e o coloca no clube`() = runBlocking {
        val buyer = newBuyerClub(950_010)
        val target = newMarketPlayer(950_011, price = 200_000_00)

        val result = buyUseCase.execute(buyer, target)

        assertIs<TransferResult.Success>(result)
        assertNull(marketRepo.findEntry(target), "jogador deve sair do mercado")
        assertTrue(clubRepo.findById(buyer)!!.squad.any { it.id == target }, "jogador deve entrar no elenco")
    }

    @Test
    fun `dois compradores simultaneos do mesmo jogador - exatamente um vence`() {
        val buyerA = newBuyerClub(950_020)
        val buyerB = newBuyerClub(950_021)
        val target = newMarketPlayer(950_022, price = 150_000_00)

        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<TransferResult>())

        for (buyer in listOf(buyerA, buyerB)) {
            pool.submit {
                start.await()
                results.add(runBlocking { buyUseCase.execute(buyer, target) })
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)

        val successes = results.count { it is TransferResult.Success }
        val notAvailable = results.count { it is TransferResult.PlayerNotAvailable }
        assertEquals(1, successes, "exatamente uma compra deve vencer a corrida")
        assertEquals(1, notAvailable, "o outro comprador deve receber PlayerNotAvailable")

        // DB consistente: o jogador saiu do mercado e está em EXATAMENTE um clube.
        val winner = (results.first { it is TransferResult.Success } as TransferResult.Success).club.id
        val loser = if (winner == buyerA) buyerB else buyerA
        runBlocking {
            assertNull(marketRepo.findEntry(target), "vencida a corrida, o jogador não fica no mercado")
            assertTrue(clubRepo.findById(winner)!!.squad.any { it.id == target }, "jogador deve estar no clube vencedor")
            assertTrue(clubRepo.findById(loser)!!.squad.none { it.id == target }, "jogador não pode estar no clube perdedor")
        }
    }
}
