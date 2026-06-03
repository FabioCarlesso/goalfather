package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.application.port.out.ClubClaimRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.UserRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.ClaimResult
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
 * Cobre a reivindicação de clube (issue #19) contra o H2 real: sucesso,
 * conflito sequencial (409) e a CORRIDA (dois usuários, mesmo clube) —
 * resolvida pelo lock otimista (`@Version`) em [ClubClaimPersistenceAdapter].
 */
@SpringBootTest
class ClaimClubIntegrationTest {

    @Autowired private lateinit var claimRepo: ClubClaimRepository
    @Autowired private lateinit var clubRepo: ClubRepository
    @Autowired private lateinit var userRepo: UserRepository

    private fun newUser(name: String): User =
        runBlocking { userRepo.save(User(UserId(0), name, "hash", clubId = null)) }

    private fun anyAvailableClubId(): ClubId =
        runBlocking { clubRepo.findAvailable().first().id }

    @Test
    fun `claim grava ownerId no clube e clubId no usuario`() = runBlocking {
        val user = newUser("claimer-${System.nanoTime()}")
        val clubId = anyAvailableClubId()

        val result = claimRepo.claim(clubId, user.id)

        assertIs<ClaimResult.Success>(result)
        assertEquals(clubId, result.user.clubId)
        assertEquals(user.id.value, clubRepo.findById(clubId)!!.ownerId)
        assertTrue(clubRepo.findAvailable().none { it.id == clubId })
    }

    @Test
    fun `segundo claim no mesmo clube retorna AlreadyClaimed`() = runBlocking {
        val a = newUser("owner-${System.nanoTime()}")
        val b = newUser("late-${System.nanoTime()}")
        val clubId = anyAvailableClubId()

        assertIs<ClaimResult.Success>(claimRepo.claim(clubId, a.id))
        val second = claimRepo.claim(clubId, b.id)

        assertIs<ClaimResult.AlreadyClaimed>(second)
        // O perdedor não fica com clubId gravado.
        assertNull(userRepo.findById(b.id)!!.clubId)
    }

    @Test
    fun `clube inexistente retorna ClubNotFound`(): Unit = runBlocking {
        val user = newUser("nf-${System.nanoTime()}")
        val result = claimRepo.claim(ClubId(999_999), user.id)
        assertIs<ClaimResult.ClubNotFound>(result)
    }

    @Test
    fun `apos claim, salvar o clube nao quebra por lock otimista (regressao ampliar estadio)`() = runBlocking {
        // O claim incrementa o @Version do clube. Salvar o clube depois (ampliar
        // estádio, escalação, compra/venda) precisa preservar essa versão, senão
        // o Hibernate lança StaleObjectStateException → 500.
        val user = newUser("save-${System.nanoTime()}")
        val clubId = anyAvailableClubId()
        assertIs<ClaimResult.Success>(claimRepo.claim(clubId, user.id))

        val club = clubRepo.findById(clubId)!!
        clubRepo.save(club.copy(stadiumCapacity = club.stadiumCapacity + 1000))

        assertEquals(club.stadiumCapacity + 1000, clubRepo.findById(clubId)!!.stadiumCapacity)
    }

    @Test
    fun `dois claims simultaneos no mesmo clube - exatamente um vence`() {
        val a = newUser("race-a-${System.nanoTime()}")
        val b = newUser("race-b-${System.nanoTime()}")
        val clubId = anyAvailableClubId()

        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<ClaimResult>())

        for (userId in listOf(a.id, b.id)) {
            pool.submit {
                start.await()
                results.add(runBlocking { claimRepo.claim(clubId, userId) })
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)

        val successes = results.count { it is ClaimResult.Success }
        val conflicts = results.count { it is ClaimResult.AlreadyClaimed }
        assertEquals(1, successes, "exatamente um claim deve vencer a corrida")
        assertEquals(1, conflicts, "o outro deve receber AlreadyClaimed (409)")
    }
}
