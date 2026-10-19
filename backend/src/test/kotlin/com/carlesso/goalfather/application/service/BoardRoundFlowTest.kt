package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubClaimRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.model.BoardExpectation
import com.carlesso.goalfather.domain.model.BoardObjective
import com.carlesso.goalfather.domain.model.BoardVerdict
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Dismissal
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.rules.INITIAL_BOARD_CONFIDENCE
import com.carlesso.goalfather.test.InMemoryCupRepository
import com.carlesso.goalfather.test.makeClub
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Diretoria no fluxo da rodada (issue #62): a confiança anda a cada rodada de
 * liga e a virada aplica bônus ou demissão.
 *
 * A rodada joga com clubes FORA das tabelas (ids 100/101), o mesmo truque do
 * teste de promoção/rebaixamento: nenhum ponto muda, então a posição de cada
 * clube depois da rodada é exatamente a da tabela montada aqui — o teste lê a
 * regra, não a sorte da engine.
 */
class BoardRoundFlowTest {

    private val clubRepo: ClubRepository = mockk()
    private val leagueRepo: LeagueRepository = mockk()
    private val claimRepo: ClubClaimRepository = mockk(relaxed = true)
    private val service = PlayRoundService(
        clubRepo,
        leagueRepo,
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        InMemoryCupRepository(),
        claimRepo,
    )

    private val savedClubs = mutableListOf<Club>()

    private fun titleBoard(confidence: Int = INITIAL_BOARD_CONFIDENCE) =
        BoardExpectation(season = 2026, objective = BoardObjective.TITLE, targetPosition = 1, confidence = confidence)

    private fun table(vararg ids: Long, division: Division = Division.FIRST) = Standings(
        season = 2026,
        round = 0,
        rows = ids.mapIndexed { i, id -> StandingRow(position = i + 1, clubId = ClubId(id), clubName = "C$id") },
        division = division,
    )

    private fun stubRound(clubs: List<Club>, vararg standings: Standings) {
        coEvery { leagueRepo.findRound(1) } returns Round(
            number = 1,
            season = 2026,
            matches = listOf(RoundMatch(1001, ClubId(100), ClubId(101), "X", "Y")),
        )
        coEvery { leagueRepo.currentStandings() } returns standings.toList()
        coEvery { clubRepo.findById(any()) } returns null
        coEvery { clubRepo.findAll() } returns clubs
        coEvery { clubRepo.save(capture(savedClubs)) } answers { ClubSaveResult.Saved(firstArg()) }
        coEvery { leagueRepo.saveRound(any()) } just Runs
        coEvery { leagueRepo.finishRound(any()) } returns true
        coEvery { leagueRepo.saveStandings(any()) } just Runs
    }

    private fun lastSaved(id: Long): Club = savedClubs.last { it.id == ClubId(id) }

    @Test
    fun `rodada de liga sobe a confianca do lider e derruba a do lanterna`() = runTest {
        // 4 clubes ⇒ turno de 3 rodadas: a rodada 1 não vira a temporada.
        val clubs = (1L..4L).map { makeClub(id = it, name = "C$it").copy(board = titleBoard()) }
        stubRound(clubs, table(1, 2, 3, 4))

        service.stream(1).toList()

        // Líder com meta de título: na meta, sobe. Lanterna com a mesma meta:
        // 3 posições abaixo, cai — e mais rápido do que o líder sobe.
        val leader = assertNotNull(lastSaved(1).board).confidence
        val last = assertNotNull(lastSaved(4).board).confidence
        assertTrue(leader > INITIAL_BOARD_CONFIDENCE, "líder deveria ganhar confiança: $leader")
        assertTrue(last < INITIAL_BOARD_CONFIDENCE, "lanterna deveria perder confiança: $last")
        assertTrue(INITIAL_BOARD_CONFIDENCE - last > leader - INITIAL_BOARD_CONFIDENCE)
    }

    @Test
    fun `divisao que folga na rodada nao mexe na confianca`() = runTest {
        // A rodada só tem jogo da 1ª divisão (100×101). A 2ª está parada — como
        // a divisão menor nas últimas datas de um calendário desigual.
        val first = (1L..4L).map { makeClub(id = it, name = "C$it").copy(board = titleBoard()) }
        val idle = (5L..6L).map {
            makeClub(id = it, name = "C$it", division = Division(2)).copy(board = titleBoard())
        }
        stubRound(first + idle, table(1, 2, 3, 4), table(5, 6, division = Division(2)))

        service.stream(1).toList()

        assertTrue(assertNotNull(lastSaved(1).board).confidence > INITIAL_BOARD_CONFIDENCE)
        // Lanterna da divisão parada: abaixo da meta, mas sem jogo não perde
        // nada — nem é regravado, porque a rodada não mudou o clube (se a
        // confiança tivesse andado, haveria um save com ela).
        val idleSaves = savedClubs.filter { it.id in idle.map(Club::id) }
        assertTrue(idleSaves.all { it.board?.confidence == INITIAL_BOARD_CONFIDENCE }, "divisão parada: $idleSaves")
    }

    @Test
    fun `clube sem meta (save anterior a issue) recebe a meta na primeira rodada`() = runTest {
        val clubs = listOf(
            makeClub(id = 1, name = "Forte", overall = 85),
            makeClub(id = 2, name = "Medio", overall = 75),
            makeClub(id = 3, name = "Fraco", overall = 70),
            makeClub(id = 4, name = "Pior", overall = 65),
        )
        stubRound(clubs, table(1, 2, 3, 4))

        service.stream(1).toList()

        // Elenco mais forte da divisão ⇒ meta de título, já avaliada na rodada.
        val board = assertNotNull(lastSaved(1).board)
        assertEquals(2026, board.season)
        assertEquals(BoardObjective.TITLE, board.objective)
        assertTrue(board.confidence > INITIAL_BOARD_CONFIDENCE)
    }

    @Test
    fun `virada paga o bonus de quem cumpriu a meta e demite quem afundou (issue 62)`() = runTest {
        // 2 clubes ⇒ turno de 1 rodada: a rodada 1 é a última da temporada.
        val champion = makeClub(id = 1, name = "Campeao", ownerId = 7).copy(board = titleBoard())
        val flop = makeClub(id = 2, name = "Fiasco", ownerId = 8).copy(board = titleBoard(confidence = 10))
        stubRound(listOf(champion, flop), table(1, 2))

        val events = service.stream(1).toList()

        val reviews = events.filterIsInstance<RoundEvent.SeasonFinished>().single().boardReviews
        val met = assertIs<BoardVerdict.TargetMet>(reviews.single { it.clubId == ClubId(1) }.verdict)
        assertEquals(BoardObjective.TITLE.bonusCents, met.bonusCents)
        val flopReview = reviews.single { it.clubId == ClubId(2) }
        assertEquals(BoardVerdict.Fired, flopReview.verdict)
        assertEquals(2, flopReview.finalPosition)

        // Bônus entra no caixa; a temporada nova já tem meta e confiança zerada.
        assertEquals(champion.cash + met.bonusCents, lastSaved(1).cash)
        val nextBoard = assertNotNull(lastSaved(1).board)
        assertEquals(2027, nextBoard.season)
        assertEquals(INITIAL_BOARD_CONFIDENCE, nextBoard.confidence)

        // Demitido: o clube volta à IA (claim ao contrário) com a demissão registrada.
        coVerify(exactly = 1) {
            claimRepo.release(
                ClubId(2),
                Dismissal(
                    clubId = ClubId(2),
                    clubName = "Fiasco",
                    season = 2026,
                    objective = BoardObjective.TITLE,
                    targetPosition = 1,
                    finalPosition = 2,
                ),
            )
        }
        coVerify(exactly = 0) { claimRepo.release(ClubId(1), any()) }
    }

    @Test
    fun `clube da IA demitido nao libera nada`() = runTest {
        val leader = makeClub(id = 1, name = "Lider").copy(board = titleBoard())
        val aiFlop = makeClub(id = 2, name = "IA").copy(board = titleBoard(confidence = 0))
        stubRound(listOf(leader, aiFlop), table(1, 2))

        val events = service.stream(1).toList()

        val review = events.filterIsInstance<RoundEvent.SeasonFinished>().single()
            .boardReviews.single { it.clubId == ClubId(2) }
        assertEquals(BoardVerdict.Fired, review.verdict)
        coVerify(exactly = 0) { claimRepo.release(any(), any()) }
    }
}
