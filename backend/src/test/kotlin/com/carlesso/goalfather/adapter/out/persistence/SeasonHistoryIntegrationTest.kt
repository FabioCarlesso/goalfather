package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.application.port.out.SeasonHistoryRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.SeasonRecord
import com.carlesso.goalfather.domain.model.SeasonStanding
import com.carlesso.goalfather.domain.model.SeasonTopScorer
import com.carlesso.goalfather.domain.model.StandingRow
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cobre o port [SeasonHistoryRepository] (issue #60) contra o H2 real: o
 * mapeamento JPA, a migration V11 e — o ponto da issue — a semântica
 * **append-only**, em que a segunda gravação da mesma temporada não pode
 * sobrescrever a primeira.
 *
 * Cada teste usa temporadas próprias (faixa 9xxx) para não colidir com o
 * estado deixado por outros testes que compartilham o contexto Spring.
 */
@SpringBootTest
class SeasonHistoryIntegrationTest {

    @Autowired private lateinit var repo: SeasonHistoryRepository

    private fun recordOf(season: Int, championId: Long, scorerGoals: Int = 11) = SeasonRecord(
        season = season,
        champion = standingOf(1, championId),
        finalStandings = listOf(standingOf(1, championId), standingOf(2, championId + 1)),
        topScorer = SeasonTopScorer(
            playerId = PlayerId(1),
            playerName = "Renato Silva",
            clubId = ClubId(championId),
            clubName = "C$championId",
            goals = scorerGoals,
        ),
    )

    private fun standingOf(position: Int, clubId: Long) = SeasonStanding(
        division = Division.FIRST,
        row = StandingRow(
            position = position,
            clubId = ClubId(clubId),
            clubName = "C$clubId",
            played = 10,
            points = 20,
        ),
        pointsPercentage = 67,
    )

    @Test
    fun `append grava e o record volta inteiro do banco`() = runBlocking {
        assertTrue(repo.append(recordOf(9101, championId = 1)))

        val found = assertNotNull(repo.findBySeason(9101))
        assertEquals(ClubId(1), found.champion.row.clubId)
        assertEquals(2, found.finalStandings.size)
        assertEquals("Renato Silva", found.topScorer?.playerName)
        assertEquals(67, found.champion.pointsPercentage)
    }

    @Test
    fun `append e append-only - a segunda gravacao da temporada nao sobrescreve`() = runBlocking {
        assertTrue(repo.append(recordOf(9102, championId = 1)))
        // Mesma temporada, campeão diferente: é o cenário de duas réplicas
        // disputando a virada (issue #46). História não se reescreve.
        assertFalse(repo.append(recordOf(9102, championId = 42)))

        assertEquals(ClubId(1), repo.findBySeason(9102)?.champion?.row?.clubId)
    }

    @Test
    fun `findAll devolve da temporada mais recente para a mais antiga`() = runBlocking {
        repo.append(recordOf(9103, championId = 1))
        repo.append(recordOf(9105, championId = 2))
        repo.append(recordOf(9104, championId = 3))

        val seasons = repo.findAll().map { it.season }.filter { it in 9103..9105 }
        assertEquals(listOf(9105, 9104, 9103), seasons)
    }

    @Test
    fun `temporada nunca encerrada nao tem record`() = runBlocking {
        assertNull(repo.findBySeason(9199))
    }
}
