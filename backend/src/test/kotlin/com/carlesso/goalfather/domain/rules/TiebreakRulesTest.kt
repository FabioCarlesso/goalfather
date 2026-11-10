package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Discipline
import com.carlesso.goalfather.domain.model.PlayedMatch
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Tiebreaker
import com.carlesso.goalfather.domain.model.Tiebreaker.DRAWING_OF_LOTS
import com.carlesso.goalfather.domain.model.Tiebreaker.FEWER_RED_CARDS
import com.carlesso.goalfather.domain.model.Tiebreaker.FEWER_YELLOW_CARDS
import com.carlesso.goalfather.domain.model.Tiebreaker.GOALS_FOR
import com.carlesso.goalfather.domain.model.Tiebreaker.GOAL_DIFFERENCE
import com.carlesso.goalfather.domain.model.Tiebreaker.HEAD_TO_HEAD
import com.carlesso.goalfather.domain.model.Tiebreaker.HEAD_TO_HEAD_GOAL_DIFFERENCE
import com.carlesso.goalfather.domain.model.Tiebreaker.POINTS
import com.carlesso.goalfather.domain.model.Tiebreaker.WINS
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TiebreakRulesTest {

    private fun row(id: Long, name: String, points: Int, wins: Int = 0, gd: Int = 0, gf: Int = 0) = StandingRow(
        position = 0,
        clubId = ClubId(id),
        clubName = name,
        points = points,
        wins = wins,
        goalDifference = gd,
        goalsFor = gf,
    )

    private fun names(rows: List<StandingRow>) = rows.map { it.clubName }

    private val brasileirao = listOf(
        POINTS, WINS, GOAL_DIFFERENCE, GOALS_FOR, HEAD_TO_HEAD, FEWER_RED_CARDS, FEWER_YELLOW_CARDS, DRAWING_OF_LOTS,
    )

    @Test
    fun `vitorias desempatam antes do saldo no Brasileirao, e o saldo antes das vitorias na Premier`() {
        val rows = listOf(
            row(1, "Mais vitórias", points = 50, wins = 15, gd = 5),
            row(2, "Mais saldo", points = 50, wins = 14, gd = 20),
        )
        assertEquals(listOf("Mais vitórias", "Mais saldo"), names(rankTable(rows, brasileirao)))
        assertEquals(listOf("Mais saldo", "Mais vitórias"), names(rankTable(rows, listOf(POINTS, GOAL_DIFFERENCE, WINS))))
    }

    @Test
    fun `confronto direto entre tres empatados e um mini-campeonato so entre eles`() {
        // A, B e C com os mesmos pontos e saldo. Entre eles: A venceu B, B venceu C, C empatou com A.
        // Mini-tabela: A 4 pts, B 3 pts, C 1 pt. Saldo geral favoreceria C.
        val rows = listOf(
            row(1, "A", points = 40, gd = 0),
            row(2, "B", points = 40, gd = 0),
            row(3, "C", points = 40, gd = 0),
            row(4, "D", points = 30),
        )
        val results = listOf(
            PlayedMatch(ClubId(1), ClubId(2), 1, 0),
            PlayedMatch(ClubId(2), ClubId(3), 2, 0),
            PlayedMatch(ClubId(3), ClubId(1), 1, 1),
            // Jogo contra quem não está empatado não entra no confronto direto.
            PlayedMatch(ClubId(3), ClubId(4), 9, 0),
        )
        val ranked = rankTable(rows, listOf(POINTS, HEAD_TO_HEAD, GOAL_DIFFERENCE), results)

        assertEquals(listOf("A", "B", "C", "D"), names(ranked))
        assertEquals(listOf(1, 2, 3, 4), ranked.map { it.position })
    }

    @Test
    fun `La Liga usa o saldo do confronto direto antes do saldo geral`() {
        val rows = listOf(row(1, "X", points = 60, gd = 30), row(2, "Y", points = 60, gd = 10))
        // Mesmos pontos no confronto direto (vitória de cada um), saldo do confronto favorece Y.
        val results = listOf(PlayedMatch(ClubId(1), ClubId(2), 1, 0), PlayedMatch(ClubId(2), ClubId(1), 3, 0))
        val laLiga = listOf(POINTS, HEAD_TO_HEAD, HEAD_TO_HEAD_GOAL_DIFFERENCE, GOAL_DIFFERENCE)

        assertEquals(listOf("Y", "X"), names(rankTable(rows, laLiga, results)))
    }

    @Test
    fun `menos cartoes vermelhos e depois amarelos desempatam`() {
        val rows = listOf(row(1, "Violento", 10), row(2, "Limpo", 10), row(3, "Amarelado", 10))
        val discipline = mapOf(
            ClubId(1) to Discipline(yellowCards = 1, redCards = 3),
            ClubId(3) to Discipline(yellowCards = 9, redCards = 0),
        )
        val ranked = rankTable(rows, listOf(POINTS, FEWER_RED_CARDS, FEWER_YELLOW_CARDS), discipline = discipline)
        assertEquals(listOf("Limpo", "Amarelado", "Violento"), names(ranked))
    }

    @Test
    fun `sorteio decide o empate total de forma deterministica pela seed`() {
        val rows = (1..6L).map { row(it, "Clube $it", points = 10) }
        val a = rankTable(rows, brasileirao, lots = Random(99))
        val b = rankTable(rows.reversed(), brasileirao, lots = Random(99))
        assertEquals(names(a), names(b))
    }

    @Test
    fun `sem criterio sobrando, nome e depois id decidem — a ordem antiga da liga`() {
        val rows = listOf(row(2, "Bravo", 10), row(1, "Alfa", 10))
        assertEquals(listOf("Alfa", "Bravo"), names(rankTable(rows, listOf(POINTS))))
        // Sem `Random`, o sorteio cai no desempate final.
        assertEquals(listOf("Alfa", "Bravo"), names(rankTable(rows, listOf(POINTS, DRAWING_OF_LOTS))))
    }

    @Test
    fun `criterios da liga do jogo sao pontos, saldo e gols pro`() {
        assertEquals(listOf(Tiebreaker.POINTS, Tiebreaker.GOAL_DIFFERENCE, Tiebreaker.GOALS_FOR), LEAGUE_COMPETITION_TIEBREAKERS)
    }
}
