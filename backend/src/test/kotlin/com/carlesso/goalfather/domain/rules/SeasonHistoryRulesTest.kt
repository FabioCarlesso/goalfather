package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.SeasonRecord
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Regras do histórico de temporadas (issue #60) — puras, sem Spring e sem
 * repositório: recebem tabelas e clubes, devolvem o snapshot.
 */
class SeasonHistoryRulesTest {

    private fun row(
        position: Int,
        clubId: Long,
        points: Int = 0,
        played: Int = 0,
        name: String = "C$clubId",
    ) = StandingRow(
        position = position,
        clubId = ClubId(clubId),
        clubName = name,
        played = played,
        points = points,
    )

    private fun table(division: Int, vararg rows: StandingRow) = Standings(
        season = 2026,
        round = 3,
        division = Division(division),
        rows = rows.toList(),
    )

    @Test
    fun `campeao e o lider da elite mesmo com as divisoes fora de ordem`() {
        val record = seasonRecordOf(
            season = 2026,
            // Segunda divisão PRIMEIRO: a regra ordena, não confia na entrada.
            finalStandings = listOf(
                table(2, row(1, 10, points = 30), row(2, 11)),
                table(1, row(1, 1, points = 20), row(2, 2)),
            ),
            clubs = emptyList(),
        )

        assertEquals(ClubId(1), record.champion.row.clubId)
        assertEquals(Division.FIRST, record.champion.division)
        // A classificação sai na ordem de exibição: elite inteira, depois a 2ª.
        assertEquals(
            listOf(1 to 1L, 1 to 2L, 2 to 10L, 2 to 11L),
            record.finalStandings.map { it.division.value to it.row.clubId.value },
        )
    }

    @Test
    fun `aproveitamento e a fracao dos pontos disputados`() {
        val record = seasonRecordOf(
            season = 2026,
            finalStandings = listOf(
                table(
                    1,
                    row(1, 1, points = 9, played = 3), // 100%
                    row(2, 2, points = 5, played = 3), // 5/9 → 56%
                    row(3, 3, points = 0, played = 0), // sem jogo, sem divisão por zero
                ),
            ),
            clubs = emptyList(),
        )

        assertEquals(listOf(100, 56, 0), record.finalStandings.map { it.pointsPercentage })
    }

    @Test
    fun `artilheiro sai do elenco de qualquer clube inclusive da IA`() {
        val human = makeClub(id = 1, name = "Humano FC", squadSize = 0, ownerId = 7).copy(
            squad = listOf(makePlayer(1).copy(goals = 8)),
        )
        val ai = makeClub(id = 2, name = "IA FC", squadSize = 0).copy(
            squad = listOf(makePlayer(2).copy(name = "Renato Silva", goals = 12)),
        )

        val record = seasonRecordOf(2026, listOf(table(1, row(1, 1), row(2, 2))), listOf(human, ai))

        val scorer = assertNotNull(record.topScorer)
        assertEquals("Renato Silva", scorer.playerName)
        assertEquals(12, scorer.goals)
        assertEquals(ClubId(2), scorer.clubId)
        assertEquals("IA FC", scorer.clubName)
    }

    @Test
    fun `empate na artilharia resolve pelo menor id e temporada sem gol nao tem artilheiro`() {
        val clubs = listOf(
            makeClub(id = 1, squadSize = 0).copy(
                squad = listOf(makePlayer(9).copy(goals = 5), makePlayer(4).copy(goals = 5)),
            ),
        )
        val tables = listOf(table(1, row(1, 1)))

        assertEquals(4L, seasonRecordOf(2026, tables, clubs).topScorer?.playerId?.value)

        val semGol = listOf(makeClub(id = 1, squadSize = 3))
        assertNull(seasonRecordOf(2026, tables, semGol).topScorer, "ninguém marcou ⇒ sem artilheiro")
    }

    @Test
    fun `carreira conta temporadas titulos e a melhor campanha`() {
        val records = listOf(
            recordOf(2026, championId = 2, myPosition = 3, myDivision = 1, myPoints = 20),
            recordOf(2027, championId = 1, myPosition = 1, myDivision = 1, myPoints = 30),
            recordOf(2028, championId = 3, myPosition = 1, myDivision = 2, myPoints = 40),
        )

        val career = assertNotNull(careerOf(ClubId(1), records))

        assertEquals(3, career.seasonsPlayed)
        assertEquals(listOf(2027), career.titles)
        // Título da elite ganha do 1º lugar na segunda divisão: divisão pesa
        // mais que posição, mesmo com menos pontos.
        assertEquals(2027, career.bestCampaign?.season)
    }

    @Test
    fun `clube sem temporada encerrada nao tem carreira`() {
        val records = listOf(recordOf(2026, championId = 2, myPosition = 1, myDivision = 1, myPoints = 30))

        assertNull(careerOf(ClubId(99), records), "clube que nunca fechou temporada não tem carreira")
        assertNull(careerOf(ClubId(1), emptyList()), "liga sem história não tem carreira")
    }

    /** Record com o clube 1 na posição/divisão pedidas e [championId] no topo da elite. */
    private fun recordOf(
        season: Int,
        championId: Long,
        myPosition: Int,
        myDivision: Int,
        myPoints: Int,
    ): SeasonRecord {
        val mine = row(myPosition, 1, points = myPoints, played = 33, name = "Meu FC")
        val champion = row(1, championId, points = 99, played = 33)
        val tables = when {
            myDivision != 1 -> listOf(table(1, champion), table(myDivision, mine))
            championId == 1L -> listOf(table(1, mine))
            else -> listOf(table(1, champion, mine))
        }
        return seasonRecordOf(season, tables.map { it.copy(season = season) }, emptyList())
    }
}
