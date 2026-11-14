package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.entity.RoundEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundStatusEnum
import com.carlesso.goalfather.adapter.out.persistence.mapper.toKey
import com.carlesso.goalfather.adapter.out.persistence.repository.RoundJpaRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.test.farMatchday
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.OptimisticLockingFailureException
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cobre o lock otimista da rodada (issue #46) contra o H2 real — valida o
 * `@Version` da [RoundEntity], a migração V6 e o contrato dos transitions do
 * [LeagueRepository]. Cada teste usa um encaixe próprio, então não colide com
 * o seed nem com os outros testes que compartilham o contexto Spring.
 */
@SpringBootTest
class RoundOptimisticLockIntegrationTest {

    @Autowired private lateinit var leagueRepo: LeagueRepository

    @Autowired private lateinit var roundRepo: RoundJpaRepository

    private fun round(number: Int) = Round(matchday = farMatchday(number), season = 2026, matches = emptyList())

    @Test
    fun `a segunda gravacao a partir da MESMA leitura e rejeitada`() {
        // Reproduz o TOCTOU multi-instância de forma determinística: dois nós
        // leem a rodada na mesma versão; A comita; B tenta gravar com a versão
        // que leu e o Hibernate rejeita (`WHERE number = ? AND version = ?`
        // afeta 0 linhas). É exatamente o que o `Mutex` in-JVM não cobria.
        val number = 9101
        roundRepo.saveAndFlush(RoundEntity(id = farMatchday(number).toKey(), season = 2026))

        val versionLidaPorAmbos = roundRepo.findById(farMatchday(number).toKey()).orElseThrow().version

        // Nó A transiciona e comita.
        val nodeA = roundRepo.findById(farMatchday(number).toKey()).orElseThrow().apply { status = RoundStatusEnum.InProgress }
        roundRepo.saveAndFlush(nodeA)

        // Nó B ainda segura a leitura antiga.
        val nodeB = RoundEntity(
            id = farMatchday(number).toKey(),
            season = 2026,
            status = RoundStatusEnum.Finished,
            version = versionLidaPorAmbos,
        )
        assertFailsWith<OptimisticLockingFailureException> { roundRepo.saveAndFlush(nodeB) }

        // O estado do vencedor sobrevive intacto.
        assertEquals(RoundStatusEnum.InProgress, roundRepo.findById(farMatchday(number).toKey()).orElseThrow().status)
    }

    @Test
    fun `finishRound so devolve true para o primeiro chamador`(): Unit = runBlocking {
        val number = 9102
        leagueRepo.saveRound(round(number))
        leagueRepo.startRound(farMatchday(number)) // só encaixe liberado termina

        val finished = round(number).copy(status = RoundStatus.Finished)
        assertTrue(leagueRepo.finishRound(finished), "o primeiro chamador vence a corrida")
        assertFalse(leagueRepo.finishRound(finished), "o segundo desiste sem aplicar efeitos")

        assertEquals(RoundStatus.Finished, leagueRepo.findRound(farMatchday(number))?.status)
    }

    @Test
    fun `finishRound recusa encaixe ainda agendado`(): Unit = runBlocking {
        // Stream órfão: a coroutine da rodada sobrevive ao cliente e, se o
        // estado foi recriado (reset do profile e2e), o mesmo encaixe
        // reaparece Scheduled. Ele não pode aplicar efeitos na liga nova.
        val number = 9107
        leagueRepo.saveRound(round(number))

        assertFalse(leagueRepo.finishRound(round(number).copy(status = RoundStatus.Finished)))
        assertEquals(RoundStatus.Scheduled, leagueRepo.findRound(farMatchday(number))?.status)
    }

    @Test
    fun `startRound so devolve true na transicao Scheduled - InProgress`(): Unit = runBlocking {
        val number = 9103
        leagueRepo.saveRound(round(number))

        assertTrue(leagueRepo.startRound(farMatchday(number)), "destrava a rodada agendada")
        assertFalse(leagueRepo.startRound(farMatchday(number)), "já estava InProgress: idempotente")

        assertEquals(RoundStatus.InProgress, leagueRepo.findRound(farMatchday(number))?.status)
    }

    @Test
    fun `transicao em rodada inexistente devolve false`(): Unit = runBlocking {
        assertFalse(leagueRepo.startRound(farMatchday(9199)))
        assertFalse(leagueRepo.finishRound(round(9199).copy(status = RoundStatus.Finished)))
    }

    @Test
    fun `saveRound preserva a versao da linha existente`(): Unit = runBlocking {
        // Regressão: mapear para uma entidade nova (version=0) faria todo
        // `saveRound` sobre rodada já versionada estourar stale state — a mesma
        // armadilha que o clube pegou na issue #19.
        val number = 9104
        leagueRepo.saveRound(round(number))
        leagueRepo.startRound(farMatchday(number)) // version → 1

        leagueRepo.saveRound(round(number).copy(status = RoundStatus.Scheduled))

        assertEquals(RoundStatus.Scheduled, leagueRepo.findRound(farMatchday(number))?.status)
        // Exatamente 2 (insert=0, startRound=1, saveRound=2). `>=` esconderia um
        // incremento duplo — o defeito que este teste existe para pegar.
        assertEquals(2L, roundRepo.findById(farMatchday(number).toKey()).orElseThrow().version)
    }

    @Test
    fun `temporada nova nao reescreve o encaixe da anterior e claim atrasado nao vence`(): Unit = runBlocking {
        // Antes da issue #125 a PK era só o número da rodada e a virada
        // reescrevia a linha 1 com `season+1` — o claim precisava comparar a
        // temporada para não aplicar efeitos em dobro. Com a PK = encaixe, a
        // temporada seguinte grava OUTRA linha; o perdedor atrasado bate no
        // guard de status da linha dele, que já está Finished.
        val number = 9105
        leagueRepo.saveRound(round(number))
        leagueRepo.startRound(farMatchday(number))
        assertTrue(leagueRepo.finishRound(round(number).copy(status = RoundStatus.Finished)))

        val nextSeason = Round(matchday = farMatchday(9155), season = 2027, matches = emptyList())
        leagueRepo.saveRound(nextSeason)

        assertFalse(
            leagueRepo.finishRound(round(number).copy(status = RoundStatus.Finished)),
            "claim atrasado não pode vencer",
        )
        assertEquals(RoundStatus.Finished, leagueRepo.findRound(farMatchday(number))?.status)
        assertEquals(RoundStatus.Scheduled, leagueRepo.findRound(nextSeason.matchday)?.status)
        roundRepo.deleteById(nextSeason.matchday.toKey())
    }

    @Test
    fun `duas THREADS reais disputando finishRound resultam em um unico vencedor`() {
        // Concorrência de verdade (fora de runTest): duas threads alinhadas por
        // uma barreira chamam finishRound na mesma rodada. O `false` do perdedor
        // pode vir do guard de status (se a outra já commitou) OU da
        // OptimisticLockException do @Version (se as duas leram antes de qualquer
        // commit) — o desfecho é o mesmo e é o que importa: exatamente um vence.
        val number = 9106
        runBlocking {
            leagueRepo.saveRound(round(number))
            leagueRepo.startRound(farMatchday(number))
        }
        val finished = round(number).copy(status = RoundStatus.Finished)

        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val task = Callable {
                barrier.await()
                runBlocking { leagueRepo.finishRound(finished) }
            }
            val a = pool.submit(task)
            val b = pool.submit(task)
            val winners = listOf(a.get(), b.get()).count { it }

            assertEquals(1, winners, "exatamente uma thread pode encerrar a rodada")
            assertEquals(RoundStatus.Finished, runBlocking { leagueRepo.findRound(farMatchday(number)) }?.status)
        } finally {
            pool.shutdownNow()
        }
    }
}
