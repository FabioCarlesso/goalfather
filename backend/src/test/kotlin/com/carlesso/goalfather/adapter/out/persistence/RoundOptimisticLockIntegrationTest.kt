package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.entity.RoundEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundStatusEnum
import com.carlesso.goalfather.adapter.out.persistence.repository.RoundJpaRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.OptimisticLockingFailureException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cobre o lock otimista da rodada (issue #46) contra o H2 real — valida o
 * `@Version` da [RoundEntity], a migração V6 e o contrato dos transitions do
 * [LeagueRepository]. Cada teste usa um `number` próprio, então não colide com
 * o seed nem com os outros testes que compartilham o contexto Spring.
 */
@SpringBootTest
class RoundOptimisticLockIntegrationTest {

    @Autowired private lateinit var leagueRepo: LeagueRepository

    @Autowired private lateinit var roundRepo: RoundJpaRepository

    private fun round(number: Int) = Round(number = number, season = 2026, matches = emptyList())

    @Test
    fun `a segunda gravacao a partir da MESMA leitura e rejeitada`() {
        // Reproduz o TOCTOU multi-instância de forma determinística: dois nós
        // leem a rodada na mesma versão; A comita; B tenta gravar com a versão
        // que leu e o Hibernate rejeita (`WHERE number = ? AND version = ?`
        // afeta 0 linhas). É exatamente o que o `Mutex` in-JVM não cobria.
        val number = 9101
        roundRepo.saveAndFlush(RoundEntity(number = number, season = 2026))

        val versionLidaPorAmbos = roundRepo.findById(number).orElseThrow().version

        // Nó A transiciona e comita.
        val nodeA = roundRepo.findById(number).orElseThrow().apply { status = RoundStatusEnum.InProgress }
        roundRepo.saveAndFlush(nodeA)

        // Nó B ainda segura a leitura antiga.
        val nodeB = RoundEntity(
            number = number,
            season = 2026,
            status = RoundStatusEnum.Finished,
            version = versionLidaPorAmbos,
        )
        assertFailsWith<OptimisticLockingFailureException> { roundRepo.saveAndFlush(nodeB) }

        // O estado do vencedor sobrevive intacto.
        assertEquals(RoundStatusEnum.InProgress, roundRepo.findById(number).orElseThrow().status)
    }

    @Test
    fun `finishRound so devolve true para o primeiro chamador`(): Unit = runBlocking {
        val number = 9102
        leagueRepo.saveRound(round(number))

        val finished = round(number).copy(status = RoundStatus.Finished)
        assertTrue(leagueRepo.finishRound(finished), "o primeiro chamador vence a corrida")
        assertFalse(leagueRepo.finishRound(finished), "o segundo desiste sem aplicar efeitos")

        assertEquals(RoundStatus.Finished, leagueRepo.findRound(number)?.status)
    }

    @Test
    fun `startRound so devolve true na transicao Scheduled - InProgress`(): Unit = runBlocking {
        val number = 9103
        leagueRepo.saveRound(round(number))

        assertTrue(leagueRepo.startRound(number), "destrava a rodada agendada")
        assertFalse(leagueRepo.startRound(number), "já estava InProgress: idempotente")

        assertEquals(RoundStatus.InProgress, leagueRepo.findRound(number)?.status)
    }

    @Test
    fun `transicao em rodada inexistente devolve false`(): Unit = runBlocking {
        assertFalse(leagueRepo.startRound(9199))
        assertFalse(leagueRepo.finishRound(round(9199).copy(status = RoundStatus.Finished)))
    }

    @Test
    fun `saveRound preserva a versao da linha existente`(): Unit = runBlocking {
        // Regressão: mapear para uma entidade nova (version=0) faria todo
        // `saveRound` sobre rodada já versionada estourar stale state — a mesma
        // armadilha que o clube pegou na issue #19.
        val number = 9104
        leagueRepo.saveRound(round(number))
        leagueRepo.startRound(number) // version → 1

        leagueRepo.saveRound(round(number).copy(status = RoundStatus.Scheduled))

        assertEquals(RoundStatus.Scheduled, leagueRepo.findRound(number)?.status)
        assertTrue(roundRepo.findById(number).orElseThrow().version >= 2)
    }
}
