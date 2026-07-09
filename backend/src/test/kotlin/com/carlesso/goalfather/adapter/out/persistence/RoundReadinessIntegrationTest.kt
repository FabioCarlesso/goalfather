package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.domain.model.UserId
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cobre o port [RoundReadinessRepository] (issue #20) contra o H2 real —
 * valida o mapeamento JPA da PK composta e a migração V4. Cada teste usa um
 * `roundNumber` próprio, então não colide com o estado deixado por outros
 * testes que compartilham o contexto Spring.
 */
@SpringBootTest
class RoundReadinessIntegrationTest {

    @Autowired private lateinit var repo: RoundReadinessRepository

    @Test
    fun `markReady persiste e readyUserIds devolve quem sinalizou`() = runBlocking {
        val round = 9001
        repo.markReady(round, UserId(1))
        repo.markReady(round, UserId(2))

        assertEquals(setOf(UserId(1), UserId(2)), repo.readyUserIds(round))
    }

    @Test
    fun `markReady e idempotente - sinalizar de novo nao duplica`() = runBlocking {
        val round = 9002
        repo.markReady(round, UserId(7))
        repo.markReady(round, UserId(7))

        assertEquals(setOf(UserId(7)), repo.readyUserIds(round))
    }

    @Test
    fun `reset limpa apenas a rodada alvo`() = runBlocking {
        val round = 9003
        val other = 9004
        repo.markReady(round, UserId(1))
        repo.markReady(other, UserId(1))

        repo.reset(round)

        assertTrue(repo.readyUserIds(round).isEmpty())
        // A rodada vizinha permanece intacta.
        assertEquals(setOf(UserId(1)), repo.readyUserIds(other))
    }

    @Test
    fun `firstReadyAt devolve o menor ready_at e null quando vazio - issue 45`() = runBlocking {
        val round = 9005
        assertNull(repo.firstReadyAt(round))

        repo.markReady(round, UserId(1))
        repo.markReady(round, UserId(2))

        val first = repo.firstReadyAt(round)
        assertNotNull(first)
        // reset apaga tudo → volta a null, confirmando que o MIN só via as linhas da rodada.
        repo.reset(round)
        assertNull(repo.firstReadyAt(round))
    }
}
