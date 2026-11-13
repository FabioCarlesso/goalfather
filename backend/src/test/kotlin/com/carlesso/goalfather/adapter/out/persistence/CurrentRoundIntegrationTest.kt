package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.mapper.toKey
import com.carlesso.goalfather.adapter.out.persistence.repository.RoundJpaRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.GameWeek
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.Slot
import com.carlesso.goalfather.test.md
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

    @Test
    fun `a rodada atual e o encaixe mais adiante no tempo, atravessando temporadas`() = runBlocking {
        // Encaixes de uma temporada que acabou + o 1º da seguinte (issue #125):
        // a PK é o encaixe, então nada é reescrito na virada.
        val old = (1..5).map { Matchday(GameWeek(3970, it * 8), Slot.WEEKEND) }
        val nextSeason = Matchday(GameWeek(3971, 2), Slot.WEEKEND)
        val all = old + nextSeason + nextSeason.next()
        try {
            for (m in old) leagueRepo.saveRound(Round(m, season = 3970, status = RoundStatus.Finished, matches = emptyList()))
            leagueRepo.saveRound(Round(nextSeason, season = 3971, matches = emptyList()))

            val current = leagueRepo.findLatest()
            assertEquals(3971, current?.season)
            assertEquals(nextSeason, current?.matchday)
            assertEquals(RoundStatus.Scheduled, current?.status)
            // As datas da temporada encerrada continuam lá — são história.
            assertEquals(RoundStatus.Finished, leagueRepo.findRound(old.last())?.status)

            // O encaixe seguinte ao domingo é a quarta da semana SEGUINTE.
            leagueRepo.saveRound(Round(nextSeason.next(), season = 3971, matches = emptyList()))
            assertEquals(nextSeason.next(), leagueRepo.findLatest()?.matchday)
        } finally {
            all.forEach { if (roundJpa.existsById(it.toKey())) roundJpa.deleteById(it.toKey()) }
        }
    }

    @Test
    fun `dentro da semana o fim de semana vem depois do meio`() = runBlocking {
        val week = GameWeek(3972, 10)
        val mid = Matchday(week, Slot.MIDWEEK)
        val end = Matchday(week, Slot.WEEKEND)
        try {
            leagueRepo.saveRound(Round(end, season = 3972, matches = emptyList()))
            leagueRepo.saveRound(Round(mid, season = 3972, matches = emptyList()))
            assertEquals(end, leagueRepo.findLatest()?.matchday)
        } finally {
            listOf(mid, end).forEach { if (roundJpa.existsById(it.toKey())) roundJpa.deleteById(it.toKey()) }
        }
    }
}
