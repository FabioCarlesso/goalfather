package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.entity.RoundEntity
import com.carlesso.goalfather.adapter.out.persistence.mapper.toKey
import com.carlesso.goalfather.adapter.out.persistence.repository.CupJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.RoundJpaRepository
import com.carlesso.goalfather.application.port.out.CupRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.CupLeg
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.LegScore
import com.carlesso.goalfather.domain.model.PenaltyScore
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.SeasonCompetitions
import com.carlesso.goalfather.domain.rules.cupMatches
import com.carlesso.goalfather.domain.rules.drawCup
import com.carlesso.goalfather.domain.rules.recordRound
import com.carlesso.goalfather.test.farMatchday
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.md
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Copa e `RoundStage` contra o H2 real (issue #61): migration V12, mapeamento
 * JPA e o round-trip JSON do `sealed` — que é onde um discriminador errado
 * apareceria. Temporadas e rodadas na faixa 94xx, apagadas no fim, para não
 * vazar estado para as suítes que compartilham o contexto.
 */
@SpringBootTest
class CupIntegrationTest {

    @Autowired private lateinit var cups: CupRepository
    @Autowired private lateinit var cupJpa: CupJpaRepository
    @Autowired private lateinit var leagueRepo: LeagueRepository
    @Autowired private lateinit var roundJpa: RoundJpaRepository

    private val clubs = (1L..12L).map { makeClub(id = it, name = "C$it", division = Division(if (it <= 6) 1 else 2)) }

    @Test
    fun `seed sorteia a copa da temporada inicial com 12 clubes na primeira fase`() = runBlocking {
        val seeded = assertNotNull(cups.findBySeason(2026), "DataInitializer deveria sortear a copa de 2026")
        val opening = seeded.stages.first()
        assertEquals("Primeira fase", opening.name)
        assertEquals(4, opening.ties.size)
        assertEquals(4, seeded.phases[1].entrants.size, "os 4 mais fortes entram nas quartas")
    }

    @Test
    fun `copa sobrevive ao round-trip com resultados, penaltis e a fase seguinte`() = runBlocking {
        val season = 9401
        try {
            val cup = drawCup(season, clubs, Random(1))
            val round = Round(md(2), season, matches = cupMatches(md(2), cup, clubs))
            val played = cup.recordRound(
                round.copy(
                    matches = round.matches.map {
                        it.copy(status = RoundStatus.Finished, homeGoals = 0, awayGoals = 0, penalties = PenaltyScore(5, 3))
                    },
                ),
            ).cup

            cups.save(cup)
            cups.save(played) // substitui a mesma temporada, não duplica

            assertEquals(played, cups.findBySeason(season))
            assertEquals(2, cups.findBySeason(season)?.stages?.size)
            assertEquals(season, cups.findLatest()?.season, "a maior temporada é a copa corrente")
        } finally {
            cupJpa.deleteById(season)
        }
    }

    @Test
    fun `temporada sem copa devolve null`() = runBlocking {
        assertNull(cups.findBySeason(9499))
    }

    @Test
    fun `stage de cada partida persiste dentro do encaixe`() = runBlocking {
        val cupDate = farMatchday(9402)
        try {
            val cup = drawCup(2026, clubs, Random(2))
            val matches = cupMatches(cupDate, cup, clubs)
            // A volta carrega o placar da ida no `stage_json` (issue #131).
            val secondLeg = RoundStage.Cup("Quartas de final", CupLeg.Second(LegScore(2, 1)))
            leagueRepo.saveRound(Round(cupDate, 2026, matches = matches.dropLast(1) + matches.last().copy(stage = secondLeg)))
            val reloaded = assertNotNull(leagueRepo.findRound(cupDate))
            assertTrue(reloaded.matches.isNotEmpty())
            assertTrue(reloaded.matches.dropLast(1).all { it.stage == RoundStage.Cup("Primeira fase", CupLeg.Single) })
            assertEquals(secondLeg, reloaded.matches.last().stage)
            assertTrue(reloaded.matches.all { it.competition == SeasonCompetitions.CUP_ID })
        } finally {
            roundJpa.deleteById(cupDate.toKey())
        }
    }
}
