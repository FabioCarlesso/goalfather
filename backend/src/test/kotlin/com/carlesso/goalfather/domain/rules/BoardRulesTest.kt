package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.BoardExpectation
import com.carlesso.goalfather.domain.model.BoardMood
import com.carlesso.goalfather.domain.model.BoardObjective
import com.carlesso.goalfather.domain.model.BoardVerdict
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Regras puras da diretoria (issue #62) — sem Spring, sem `Random`. */
class BoardRulesTest {

    private fun expectation(target: Int = 1, confidence: Int = INITIAL_BOARD_CONFIDENCE) =
        BoardExpectation(season = 2026, objective = BoardObjective.TITLE, targetPosition = target, confidence = confidence)

    /** Repete [rounds] rodadas terminadas sempre em [position]. */
    private fun BoardExpectation.after(rounds: Int, position: Int): BoardExpectation =
        (1..rounds).fold(this) { acc, _ -> acc.afterRound(position) }

    // ─── Meta ──────────────────────────────────────────────────────────────

    @Test
    fun `meta e deterministica dada a composicao da liga`() {
        // Seis clubes conhecidos na elite: forças 85 > 80 > 76 > 72 > 68 > 64.
        val clubs = listOf(85, 80, 76, 72, 68, 64).mapIndexed { i, overall ->
            makeClub(id = (i + 1).toLong(), overall = overall)
        }

        val first = boardExpectations(2026, clubs)
        // Mesma liga em outra ordem dá exatamente as mesmas metas.
        val again = boardExpectations(2026, clubs.reversed())

        assertEquals(first, again)
        assertEquals(BoardObjective.TITLE, first.getValue(ClubId(1)).objective)
        assertEquals(1, first.getValue(ClubId(1)).targetPosition)
        // Divisão de 6: metade de cima = 3º; meio de tabela = escapar do último quarto (5º).
        assertEquals(listOf(3, 3), listOf(2L, 3L).map { first.getValue(ClubId(it)).targetPosition })
        assertEquals(BoardObjective.TOP_HALF, first.getValue(ClubId(2)).objective)
        assertEquals(BoardObjective.MID_TABLE, first.getValue(ClubId(6)).objective)
        assertEquals(5, first.getValue(ClubId(6)).targetPosition)
        assertTrue(first.values.all { it.confidence == INITIAL_BOARD_CONFIDENCE && it.season == 2026 })
    }

    @Test
    fun `cada clube e ranqueado so contra a propria divisao`() {
        // O mais forte da 2ª divisão é mais fraco que todos da elite, mas é o
        // favorito DA SUA divisão — e a diretoria cobra o título dela.
        val elite = (1L..4L).map { makeClub(id = it, overall = 85) }
        val second = listOf(
            makeClub(id = 5, overall = 74, division = Division(2)),
            makeClub(id = 6, overall = 60, division = Division(2)),
        )

        val boards = boardExpectations(2026, elite + second)

        assertEquals(BoardObjective.TITLE, boards.getValue(ClubId(5)).objective)
        assertEquals(BoardObjective.MID_TABLE, boards.getValue(ClubId(6)).objective)
    }

    @Test
    fun `empate de forca desempata pelo menor id`() {
        val boards = boardExpectations(2026, listOf(makeClub(id = 9), makeClub(id = 3), makeClub(id = 5), makeClub(id = 7)))

        assertEquals(BoardObjective.TITLE, boards.getValue(ClubId(3)).objective)
    }

    @Test
    fun `forca do elenco e a dos 11 melhores e ignora fadiga`() {
        val stars = (1L..11L).map { makePlayer(it, overall = 80, stamina = 10) }
        val bench = (12L..20L).map { makePlayer(it, overall = 50) }
        val club = makeClub().copy(squad = stars + bench)

        assertEquals(80.0, club.squadRating())
    }

    @Test
    fun `elenco desmontado e fraco - vaga vazia conta zero`() {
        // Dois craques e mais ninguém não fazem um elenco forte "na média".
        val gutted = makeClub().copy(squad = listOf(makePlayer(1, overall = 88), makePlayer(2, overall = 86)))
        val full = makeClub(overall = 65)

        assertTrue(gutted.squadRating() < full.squadRating())
        assertEquals(BoardObjective.TITLE, boardExpectations(2026, listOf(gutted.copy(id = ClubId(1)), full.copy(id = ClubId(2))))
            .getValue(ClubId(2)).objective)
    }

    @Test
    fun `candidatos ao titulo sao 3 numa liga de 20 e 1 numa de 6`() {
        assertEquals(BoardObjective.TITLE, objectiveFor(strengthRank = 3, divisionSize = 20))
        assertEquals(BoardObjective.TOP_HALF, objectiveFor(strengthRank = 4, divisionSize = 20))
        assertEquals(BoardObjective.MID_TABLE, objectiveFor(strengthRank = 11, divisionSize = 20))
        assertEquals(BoardObjective.TITLE, objectiveFor(strengthRank = 1, divisionSize = 6))
        assertEquals(BoardObjective.TOP_HALF, objectiveFor(strengthRank = 2, divisionSize = 6))
    }

    @Test
    fun `posicao alvo cabe sempre na divisao e meta modesta fica abaixo da ambiciosa`() {
        for (size in 1..24) {
            val targets = BoardObjective.entries.map { it.targetPosition(size) }
            assertTrue(targets.all { it in 1..size }, "alvo fora da divisão de $size: $targets")
            assertEquals(targets, targets.sorted(), "metas fora de ordem na divisão de $size")
        }
    }

    // ─── Confiança ao longo das rodadas ────────────────────────────────────

    @Test
    fun `lider disparado com meta de titulo satura em 100`() {
        val board = expectation(target = 1).after(rounds = 30, position = 1)

        assertEquals(100, board.confidence)
        assertEquals(BoardMood.CONFIDENT, board.mood)
    }

    @Test
    fun `lanterna com meta de titulo afunda ate 0 e fica em risco`() {
        val board = expectation(target = 1).after(rounds = 10, position = 20)

        assertEquals(0, board.confidence)
        assertEquals(BoardMood.AT_RISK, board.mood)
    }

    @Test
    fun `confianca sobe na meta ou acima e cai abaixo dela`() {
        assertTrue(confidenceDelta(targetPosition = 5, position = 5) > 0)
        assertTrue(confidenceDelta(targetPosition = 5, position = 1) > confidenceDelta(targetPosition = 5, position = 4))
        assertTrue(confidenceDelta(targetPosition = 5, position = 6) < 0)
        assertTrue(confidenceDelta(targetPosition = 5, position = 9) < confidenceDelta(targetPosition = 5, position = 6))
    }

    @Test
    fun `uma rodada so nao decide a temporada`() {
        // Do valor inicial, nem a melhor nem a pior rodada possível cruzam os
        // limiares de humor: é a sequência que conta.
        val best = expectation(target = 20).afterRound(position = 1)
        val worst = expectation(target = 1).afterRound(position = 20)

        assertTrue(best.confidence < CONFIDENT_CONFIDENCE)
        assertTrue(worst.confidence > FIRING_CONFIDENCE)
    }

    // ─── Fim de temporada ──────────────────────────────────────────────────

    @Test
    fun `meta cumprida rende o bonus do objetivo`() {
        val verdict = expectation(target = 3, confidence = 0).verdict(finalPosition = 2)

        // Cumpriu: a confiança baixa não importa mais.
        assertIs<BoardVerdict.TargetMet>(verdict)
        assertEquals(BoardObjective.TITLE.bonusCents, verdict.bonusCents)
    }

    @Test
    fun `fracasso com a confianca no chao demite`() {
        assertEquals(BoardVerdict.Fired, expectation(target = 1, confidence = FIRING_CONFIDENCE).verdict(finalPosition = 6))
    }

    @Test
    fun `tropeco com a diretoria satisfeita so perde o bonus`() {
        assertEquals(BoardVerdict.TargetMissed, expectation(target = 1, confidence = 80).verdict(finalPosition = 2))
    }

    @Test
    fun `balanco da temporada cobre quem tem meta e ignora save sem meta`() {
        val withBoard = makeClub(id = 1, name = "Com meta").copy(board = expectation(target = 1))
        val stale = makeClub(id = 2, name = "Meta velha").copy(board = expectation().copy(season = 2025))
        val legacy = makeClub(id = 3, name = "Sem meta")
        val table = Standings(
            season = 2026,
            round = 5,
            rows = listOf(1L, 2L, 3L).mapIndexed { i, id ->
                StandingRow(position = i + 1, clubId = ClubId(id), clubName = "C$id")
            },
        )

        val reviews = seasonBoardReviews(2026, listOf(table), listOf(legacy, stale, withBoard))

        val review = reviews.single()
        assertEquals(ClubId(1), review.clubId)
        assertEquals("Com meta", review.clubName)
        assertEquals(1, review.finalPosition)
        assertIs<BoardVerdict.TargetMet>(review.verdict)
    }
}
