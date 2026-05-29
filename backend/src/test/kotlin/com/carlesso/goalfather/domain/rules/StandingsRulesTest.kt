package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import kotlin.test.Test
import kotlin.test.assertEquals

class StandingsRulesTest {

    private fun row(clubId: Long, name: String, points: Int = 0) = StandingRow(
        position = 0,
        clubId = ClubId(clubId),
        clubName = name,
        points = points,
    )

    private val initialStandings = Standings(
        season = 2026,
        round = 0,
        rows = listOf(
            row(1, "Goal Father"),
            row(2, "Bonsucesso"),
            row(3, "Vargem"),
        ),
    )

    private fun match(
        id: Long,
        homeId: Long,
        awayId: Long,
        homeGoals: Int,
        awayGoals: Int,
        status: RoundStatus = RoundStatus.Finished,
    ) = RoundMatch(
        matchId = id,
        homeClubId = ClubId(homeId),
        awayClubId = ClubId(awayId),
        homeClubName = "H$homeId",
        awayClubName = "A$awayId",
        status = status,
        homeGoals = homeGoals,
        awayGoals = awayGoals,
        minute = 90,
    )

    @Test
    fun `vitoria mandante da 3 pontos para home e 0 para away`() {
        val round = Round(
            number = 1,
            season = 2026,
            matches = listOf(match(1001, homeId = 1, awayId = 2, homeGoals = 2, awayGoals = 1)),
        )
        val result = applyRoundToStandings(round, initialStandings)

        val home = result.rows.first { it.clubId == ClubId(1) }
        val away = result.rows.first { it.clubId == ClubId(2) }

        assertEquals(3, home.points)
        assertEquals(1, home.wins)
        assertEquals(1, home.goalDifference)
        assertEquals(0, away.points)
        assertEquals(1, away.losses)
    }

    @Test
    fun `empate da 1 ponto para cada lado`() {
        val round = Round(
            number = 1,
            season = 2026,
            matches = listOf(match(1001, homeId = 1, awayId = 2, homeGoals = 1, awayGoals = 1)),
        )
        val result = applyRoundToStandings(round, initialStandings)

        assertEquals(1, result.rows.first { it.clubId == ClubId(1) }.points)
        assertEquals(1, result.rows.first { it.clubId == ClubId(2) }.points)
        assertEquals(1, result.rows.first { it.clubId == ClubId(1) }.draws)
    }

    @Test
    fun `partidas nao-finalizadas sao ignoradas`() {
        val round = Round(
            number = 1,
            season = 2026,
            matches = listOf(
                match(1001, homeId = 1, awayId = 2, homeGoals = 3, awayGoals = 0, status = RoundStatus.InProgress),
            ),
        )
        val result = applyRoundToStandings(round, initialStandings)
        assertEquals(0, result.rows.first { it.clubId == ClubId(1) }.points)
    }

    @Test
    fun `tiebreak ordena por pontos, saldo, gols pro, nome`() {
        // A e B empatados em pontos. A tem melhor saldo de gols.
        // C tem mais pontos que ambos.
        val standings = Standings(
            season = 2026,
            round = 0,
            rows = listOf(
                row(1, "Z A FC"),
                row(2, "A B FC"),
                row(3, "C FC"),
            ),
        )
        val round = Round(
            number = 1,
            season = 2026,
            matches = listOf(
                match(1001, homeId = 1, awayId = 2, homeGoals = 3, awayGoals = 0), // 1 vence (3p, SG+3)
                match(1002, homeId = 2, awayId = 3, homeGoals = 1, awayGoals = 0), // 2 vence (3p, SG+1)
                match(1003, homeId = 3, awayId = 1, homeGoals = 0, awayGoals = 0), // empate (1p cada)
            ),
        )
        val result = applyRoundToStandings(round, standings)

        // 1: 1V 1E = 4p, SG=+3
        // 2: 1V 1D = 3p, SG=-2
        // 3: 1E 1D = 1p, SG=-1
        assertEquals(ClubId(1), result.rows[0].clubId)
        assertEquals(4, result.rows[0].points)
        assertEquals(ClubId(2), result.rows[1].clubId)
        assertEquals(ClubId(3), result.rows[2].clubId)
        // posições estão preenchidas
        assertEquals(1, result.rows[0].position)
        assertEquals(2, result.rows[1].position)
        assertEquals(3, result.rows[2].position)
    }

    @Test
    fun `round refletido no campo round da Standings retornada`() {
        val round = Round(
            number = 5,
            season = 2026,
            matches = listOf(match(5001, homeId = 1, awayId = 2, homeGoals = 1, awayGoals = 0)),
        )
        val result = applyRoundToStandings(round, initialStandings)
        assertEquals(5, result.round)
        assertEquals(2026, result.season)
    }
}
