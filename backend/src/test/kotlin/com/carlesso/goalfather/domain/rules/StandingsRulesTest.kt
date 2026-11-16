package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.test.leagueMatch
import com.carlesso.goalfather.test.testStandings
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.CupLeg
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.TableZone
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Discipline
import com.carlesso.goalfather.test.BRASILEIRAO_TIEBREAKERS
import com.carlesso.goalfather.test.LA_LIGA_TIEBREAKERS
import com.carlesso.goalfather.test.SERIE_A
import com.carlesso.goalfather.test.SERIE_B
import com.carlesso.goalfather.test.md
import kotlin.test.Test
import kotlin.test.assertEquals

class StandingsRulesTest {

    private fun row(clubId: Long, name: String, points: Int = 0) = StandingRow(
        position = 0,
        clubId = ClubId(clubId),
        clubName = name,
        points = points,
    )

    private val initialStandings = testStandings(
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
    ) = leagueMatch(
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
            matchday = md(1),
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
    fun `a faixa e da posicao - quem sobe na tabela herda a faixa do lugar (issue 128)`() {
        val zoned = initialStandings.copy(
            rows = listOf(
                row(1, "Goal Father").copy(position = 1, zone = TableZone.PROMOTION),
                row(2, "Bonsucesso").copy(position = 2),
                row(3, "Vargem").copy(position = 3, zone = TableZone.RELEGATION),
            ),
        )
        // Vargem vence fora e salta do 3º para o 1º; Goal Father cai para o 3º.
        val round = Round(
            matchday = md(1),
            season = 2026,
            matches = listOf(match(1001, homeId = 1, awayId = 3, homeGoals = 0, awayGoals = 2)),
        )
        val result = applyRoundToStandings(round, zoned)

        assertEquals(listOf(3L, 2, 1), result.rows.map { it.clubId.value })
        assertEquals(listOf(TableZone.PROMOTION, TableZone.NONE, TableZone.RELEGATION), result.rows.map { it.zone })
    }

    @Test
    fun `empate da 1 ponto para cada lado`() {
        val round = Round(
            matchday = md(1),
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
            matchday = md(1),
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
        val standings = testStandings(
            season = 2026,
            round = 0,
            rows = listOf(
                row(1, "Z A FC"),
                row(2, "A B FC"),
                row(3, "C FC"),
            ),
        )
        val round = Round(
            matchday = md(1),
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
            matchday = md(5),
            season = 2026,
            matches = listOf(match(5001, homeId = 1, awayId = 2, homeGoals = 1, awayGoals = 0).copy(stage = RoundStage.League(5))),
        )
        val result = applyRoundToStandings(round, initialStandings)
        assertEquals(5, result.round)
        assertEquals(2026, result.season)
    }

    // ─── Copa (issue #61) ─────────────────────────────────────────────────

    private fun finishedMatch(home: Long, away: Long, homeGoals: Int, awayGoals: Int) = leagueMatch(
        matchId = 1,
        homeClubId = ClubId(home),
        awayClubId = ClubId(away),
        homeClubName = "H",
        awayClubName = "A",
        status = RoundStatus.Finished,
        homeGoals = homeGoals,
        awayGoals = awayGoals,
    )

    @Test
    fun `rodada de copa nao mexe na tabela`() {
        val cupRound = Round(
            matchday = md(2),
            season = 2026,
            matches = listOf(finishedMatch(1, 2, 3, 0).copy(stage = RoundStage.Cup("Semifinal", CupLeg.Single))),
        )
        assertEquals(initialStandings, applyRoundToStandings(cupRound, initialStandings))
    }

    @Test
    fun `tabela registra a rodada da liga e nao a data do calendario`() {
        // Data 3 do calendário, 2ª rodada de liga (a data 2 foi de copa).
        val leagueRound = Round(
            matchday = md(3),
            season = 2026,
            matches = listOf(finishedMatch(1, 2, 1, 0).copy(stage = RoundStage.League(2))),
        )
        val updated = applyRoundToStandings(leagueRound, initialStandings)
        assertEquals(2, updated.round)
        assertEquals(3, updated.rows.first { it.clubId == ClubId(1) }.points)
    }

    // ─── Desempates de cada liga (issue #131) ─────────────────────────────

    /** Tabela de 4 clubes zerada, na ordem de id. */
    private fun fourClubs(competition: CompetitionId = SERIE_A) = testStandings(
        competitionId = competition,
        rows = (1L..4L).mapIndexed { i, id -> StandingRow(position = i + 1, clubId = ClubId(id), clubName = "C$id") },
    )

    private fun leagueRound(week: Int, vararg matches: RoundMatch, competition: CompetitionId = SERIE_A) =
        Round(md(week), 2026, matches = matches.map { it.copy(competition = competition) })

    @Test
    fun `Brasileirao desempata por vitorias antes do saldo`() {
        val rounds = listOf(
            leagueRound(1, match(1, 1, 4, 5, 0), match(2, 2, 3, 0, 0)),
            leagueRound(2, match(3, 4, 1, 1, 0), match(4, 3, 2, 1, 1)),
            leagueRound(3, match(5, 2, 4, 1, 0), match(6, 1, 3, 0, 0)),
        )
        // 1: V D E = 4 pts | 2: E E V = 5 pts | 3: E E E = 3 pts, 0 vitória, saldo 0
        // 4: D V D = 3 pts, 1 vitória, saldo -5.
        val brasileirao = rounds.fold(fourClubs()) { t, r -> applyRoundToStandings(r, t, BRASILEIRAO_TIEBREAKERS) }
        assertEquals(listOf(2L, 1L, 4L, 3L), brasileirao.rows.map { it.clubId.value }, "vitórias antes do saldo")

        val padrao = rounds.fold(fourClubs()) { t, r -> applyRoundToStandings(r, t) }
        assertEquals(listOf(2L, 1L, 3L, 4L), padrao.rows.map { it.clubId.value }, "pontos → saldo põe o 3 à frente do 4")
    }

    @Test
    fun `La Liga desempata pelo confronto direto antes do saldo`() {
        val laLiga = CompetitionId("esp-la-liga")
        val rounds = listOf(
            leagueRound(1, match(1, 2, 1, 1, 0), match(2, 3, 4, 0, 0), competition = laLiga),
            leagueRound(2, match(3, 1, 3, 6, 0), match(4, 4, 2, 2, 0), competition = laLiga),
        )
        // 1 e 2 com 3 pontos: 1 tem saldo +5, 2 tem -1 — mas 2 venceu o confronto direto.
        val table = rounds.fold(fourClubs(laLiga)) { t, r -> applyRoundToStandings(r, t, LA_LIGA_TIEBREAKERS) }
        assertEquals(4, table.results.size, "os placares ficam na tabela para o confronto direto")
        assertEquals(listOf(4L, 2L, 1L, 3L), table.rows.map { it.clubId.value })

        val padrao = rounds.fold(fourClubs(laLiga)) { t, r -> applyRoundToStandings(r, t) }
        assertEquals(listOf(4L, 1L, 2L, 3L), padrao.rows.map { it.clubId.value })
    }

    @Test
    fun `cartoes do clube entram na linha e desempatam no Brasileirao`() {
        var table = fourClubs()
        // Dois 0×0: todos com 1 ponto e saldo zero; 1 leva um vermelho, 2 dois amarelos.
        val cards = mapOf(ClubId(1) to Discipline(yellowCards = 1, redCards = 1), ClubId(2) to Discipline(yellowCards = 2))
        table = applyRoundToStandings(leagueRound(1, match(1, 1, 3, 0, 0), match(2, 2, 4, 0, 0)), table, BRASILEIRAO_TIEBREAKERS, cards)
        assertEquals(1, table.rows.single { it.clubId == ClubId(1) }.redCards)
        assertEquals(2, table.rows.single { it.clubId == ClubId(2) }.yellowCards)
        // Os quatro empatados em tudo até o confronto direto: o único com
        // vermelho (1) é o último; 2 (só amarelos) vem depois de 3 e 4 (limpos).
        val order = table.rows.map { it.clubId.value }
        assertEquals(1L, order.last(), "menos vermelhos primeiro: $order")
        assertEquals(2L, order[2], "depois, menos amarelos: $order")
    }

    @Test
    fun `jogo de outra liga no mesmo encaixe nao mexe na tabela`() {
        val other = applyRoundToStandings(
            leagueRound(1, match(1, 1, 2, 3, 0), competition = SERIE_B),
            fourClubs(),
        )
        assertEquals(fourClubs(), other)
    }
}
