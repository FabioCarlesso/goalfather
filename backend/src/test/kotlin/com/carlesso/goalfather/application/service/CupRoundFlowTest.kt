package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.SeasonHistoryRepository
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.CupTie
import com.carlesso.goalfather.domain.model.KnockoutPhase
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.SeasonRecord
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.TieResult
import com.carlesso.goalfather.domain.rules.cupDrawSeed
import com.carlesso.goalfather.domain.rules.cupRound
import com.carlesso.goalfather.domain.rules.drawCup
import com.carlesso.goalfather.domain.rules.generateRound
import com.carlesso.goalfather.domain.rules.recordRound
import com.carlesso.goalfather.test.InMemoryCupRepository
import com.carlesso.goalfather.test.makeClub
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A copa dentro do fluxo de rodada (issue #61): mesma prontidão, mesmo claim,
 * mesmo stream — o que muda é o que se faz com o resultado.
 *
 * Liga de 4 clubes numa divisão: 3 rodadas de liga e uma copa de 4 (semi +
 * final), o que dá o calendário `L1 · Semi · L2 · Final · L3`.
 */
class CupRoundFlowTest {

    private val clubRepo: ClubRepository = mockk()
    private val leagueRepo: LeagueRepository = mockk()
    private val historyRepo: SeasonHistoryRepository = mockk(relaxed = true)

    private val clubs: List<Club> = (1L..4L).map { makeClub(id = it, name = "C$it", overall = 70 + it.toInt()) }
    private val cup: Cup = drawCup(2026, clubs, Random(cupDrawSeed(2026)))
    private val cupRepo = InMemoryCupRepository(cup)

    private val service = PlayRoundService(
        clubRepo,
        leagueRepo,
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        historyRepo,
        cupRepo,
    )

    private val table = Standings(
        season = 2026,
        round = 0,
        rows = clubs.mapIndexed { i, c -> StandingRow(i + 1, c.id, c.name) },
    )

    private val savedRounds = mutableListOf<Round>()
    private val claimed = slot<Round>()
    private val savedStandings = mutableListOf<Standings>()
    private val savedClubs = mutableListOf<Club>()

    private fun stubLeague(vararg rounds: Round) {
        for (round in rounds) coEvery { leagueRepo.findRound(round.number) } returns round
        coEvery { leagueRepo.currentStandings() } returns listOf(table)
        coEvery { leagueRepo.saveRound(capture(savedRounds)) } just Runs
        coEvery { leagueRepo.finishRound(capture(claimed)) } returns true
        coEvery { leagueRepo.saveStandings(capture(savedStandings)) } just Runs
        // Um stub por clube: `ClubId` é value class e chega ao `answers` já
        // desembrulhado (Long), então `firstArg<ClubId>()` não funcionaria.
        for (club in clubs) coEvery { clubRepo.findById(club.id) } returns club
        coEvery { clubRepo.findAll() } returns clubs
        coEvery { clubRepo.save(capture(savedClubs)) } answers { ClubSaveResult.Saved(firstArg()) }
    }

    @Test
    fun `depois da rodada de liga 1 a data seguinte e a semifinal da copa`() = runTest {
        stubLeague(generateRound(1, 2026, clubs))

        service.stream(1).toList()

        val next = savedRounds.single()
        assertEquals(2, next.number)
        assertEquals(RoundStage.Cup(KnockoutPhase.SEMI_FINALS), next.stage)
        assertEquals(2, next.matches.size)
    }

    @Test
    fun `data de copa decide nos penaltis, nao mexe na tabela e premia quem avanca`() = runTest {
        val semi = cupRound(2, 2026, KnockoutPhase.SEMI_FINALS, cup, clubs)
        stubLeague(semi)

        val events = service.stream(2).toList()

        // Mata-mata: todo jogo encerrado tem vencedor — empate só com pênaltis.
        val finished = claimed.captured
        assertTrue(finished.matches.all { it.status == RoundStatus.Finished })
        for (match in finished.matches) {
            if (match.homeGoals == match.awayGoals) assertNotNull(match.penalties)
            else assertNull(match.penalties)
        }
        // A disputa (quando houve) viajou no stream como evento da partida.
        val shootouts = events.filterIsInstance<RoundEvent.MatchUpdate>().count { it.event is MatchEvent.PenaltyShootout }
        assertEquals(finished.matches.count { it.penalties != null }, shootouts)

        // Tabela intacta: mata-mata não vale ponto.
        assertTrue(savedStandings.single().rows.all { it.points == 0 && it.played == 0 })

        // Chave avançou: semis decididas e final sorteada com os vencedores.
        val updated = assertNotNull(cupRepo.cups[2026])
        val semis = updated.stages.first().ties.filterIsInstance<CupTie.Fixture>()
        assertTrue(semis.all { it.result != null })
        val final = updated.stages.last()
        assertEquals(KnockoutPhase.FINAL, final.phase)
        val finalTie = assertIs<CupTie.Fixture>(final.ties.single())
        assertEquals(semis.map { it.winner }, listOf(finalTie.home, finalTie.away))

        // Prêmio da fase para os dois classificados, no extrato E no caixa; sem folha.
        val roundFinished = events.filterIsInstance<RoundEvent.RoundFinished>().single()
        val winners = semis.mapNotNull { it.winner?.id }.toSet()
        for (finance in roundFinished.finances) {
            val expectedPrize = if (finance.clubId in winners) KnockoutPhase.SEMI_FINALS.prizeCents else 0L
            assertEquals(expectedPrize, finance.cupPrize, "prêmio de ${finance.clubId}")
            assertEquals(0L, finance.salariesPaid, "data de copa não cobra folha")
        }
        val cashOf = savedClubs.associate { it.id to it.cash }
        for (club in clubs) {
            val finance = roundFinished.finances.single { it.clubId == club.id }
            assertEquals(club.cash + finance.ticketRevenue + finance.cupPrize, cashOf.getValue(club.id))
        }

        // Próxima data: rodada de liga 2, pareada pelo Berger da rodada 2.
        val next = savedRounds.single()
        assertEquals(3, next.number)
        assertEquals(RoundStage.League(2), next.stage)
        assertEquals(
            generateRound(2, 2026, clubs).matches.map { it.homeClubId to it.awayClubId },
            next.matches.map { it.homeClubId to it.awayClubId },
        )
    }

    @Test
    fun `rodada de liga depois da copa conta na tabela pela matchday`() = runTest {
        // Semis já disputadas na data 2: a chave está na final.
        val semi = cupRound(2, 2026, KnockoutPhase.SEMI_FINALS, cup, clubs)
        cupRepo.cups[2026] = cup.recordRound(
            semi.copy(matches = semi.matches.map { it.copy(status = RoundStatus.Finished, homeGoals = 1) }),
        ).cup
        val league2 = generateRound(3, 2026, clubs, matchday = 2)
        stubLeague(league2)

        service.stream(3).toList()

        val table = savedStandings.single()
        assertEquals(2, table.round, "tabela conta a rodada da liga, não a data")
        assertEquals(4, table.rows.sumOf { it.played })
        // Depois da liga 2 vem a final da copa.
        assertEquals(RoundStage.Cup(KnockoutPhase.FINAL), savedRounds.single().stage)
    }

    @Test
    fun `campeao da copa vai para o SeasonFinished e para o historico, e a temporada nova sorteia outra copa`() = runTest {
        // Copa já decidida: semis e final com o mandante vencendo.
        var played = cup
        var date = 2
        while (played.currentStage != null) {
            val round = cupRound(date++, 2026, played.currentStage!!.phase, played, clubs)
            played = played.recordRound(
                round.copy(matches = round.matches.map { it.copy(status = RoundStatus.Finished, homeGoals = 2) }),
            ).cup
        }
        val champion = assertNotNull(played.champion)
        cupRepo.cups[2026] = played

        val record = slot<SeasonRecord>()
        coEvery { historyRepo.append(capture(record)) } returns true
        // Data 5 = rodada de liga 3, a última da temporada.
        stubLeague(generateRound(5, 2026, clubs, matchday = 3))

        val events = service.stream(5).toList()

        val seasonFinished = events.filterIsInstance<RoundEvent.SeasonFinished>().single()
        assertEquals(champion, seasonFinished.cupChampion)
        assertEquals(champion, record.captured.cupChampion)

        // Temporada nova: copa sorteada com a seed de 2027 e rodada 1 de liga.
        val nextCup = assertNotNull(cupRepo.cups[2027])
        assertEquals(KnockoutPhase.SEMI_FINALS, nextCup.stages.single().phase)
        assertEquals(RoundStage.League(1), savedRounds.single().stage)
        assertEquals(2027, savedRounds.single().season)
    }

    @Test
    fun `liga sem copa segue so de pontos corridos`() = runTest {
        cupRepo.cups.clear()
        stubLeague(generateRound(1, 2026, clubs))

        service.stream(1).toList()

        assertEquals(RoundStage.League(2), savedRounds.single().stage)
    }

    @Test
    fun `replay de data de copa encerrada nao reaplica resultado nem premio`() = runTest {
        val semi = cupRound(2, 2026, KnockoutPhase.SEMI_FINALS, cup, clubs).copy(status = RoundStatus.Finished)
        stubLeague(semi)

        val events = service.stream(2).toList()

        assertEquals(cup, cupRepo.cups[2026], "replay não toca na chave")
        assertTrue(events.filterIsInstance<RoundEvent.RoundFinished>().single().finances.isEmpty())
    }

    @Test
    fun `resultado de mata-mata gravado bate com o placar e os penaltis da partida`() = runTest {
        val semi = cupRound(2, 2026, KnockoutPhase.SEMI_FINALS, cup, clubs)
        stubLeague(semi)

        service.stream(2).toList()

        val byPair = claimed.captured.matches.associateBy { it.homeClubId to it.awayClubId }
        val semis = assertNotNull(cupRepo.cups[2026]).stages.first().ties.filterIsInstance<CupTie.Fixture>()
        for (tie in semis) {
            val match = byPair.getValue(tie.home.id to tie.away.id)
            assertEquals(TieResult(match.homeGoals, match.awayGoals, match.penalties), tie.result)
        }
        assertTrue(semis.none { it.home.id == ClubId(0) })
    }
}
