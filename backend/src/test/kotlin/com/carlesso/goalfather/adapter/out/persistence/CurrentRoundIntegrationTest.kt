package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.repository.RoundJpaRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * "Rodada atual" depois da virada de temporada (achado da review do PR #78).
 *
 * A PK de `rounds` é só `number`, e a virada reescreve apenas a linha 1: as
 * datas 2..N da temporada que acabou continuam na tabela, `Finished`. Com
 * `findLatest` ordenando só por `number`, a "atual" passava a ser a ÚLTIMA data
 * da temporada anterior — `/round/current` mostrava uma rodada encerrada e
 * `/round/play` respondia `409 ROUND_ALREADY_FINISHED`, travando a liga depois
 * da 1ª temporada. Os testes de serviço não pegavam (mockam o repositório) nem
 * o E2E (o MSW guarda a rodada corrente em memória).
 *
 * Temporadas 97xx, bem acima do seed, e linhas apagadas no fim: enquanto o
 * teste roda elas SÃO a temporada corrente do contexto compartilhado.
 */
@SpringBootTest
class CurrentRoundIntegrationTest {

    @Autowired private lateinit var leagueRepo: LeagueRepository
    @Autowired private lateinit var roundJpa: RoundJpaRepository

    private val dates = 9701..9705

    @Test
    fun `depois da virada a rodada atual e a data 1 da temporada nova, nao a ultima da anterior`() = runBlocking {
        try {
            for (n in dates) leagueRepo.saveRound(Round(n, season = 9700, status = RoundStatus.Finished, matches = emptyList()))
            // Virada: a temporada nova reescreve só a PRIMEIRA data.
            leagueRepo.saveRound(Round(dates.first, season = 9701, matches = emptyList()))

            val current = leagueRepo.findLatest()
            assertEquals(9701, current?.season)
            assertEquals(dates.first, current?.number)
            assertEquals(RoundStatus.Scheduled, current?.status)

            // Dentro da mesma temporada continua valendo a maior data.
            leagueRepo.saveRound(Round(dates.first + 1, season = 9701, matches = emptyList()))
            assertEquals(dates.first + 1, leagueRepo.findLatest()?.number)
        } finally {
            dates.forEach { if (roundJpa.existsById(it)) roundJpa.deleteById(it) }
        }
    }
}
