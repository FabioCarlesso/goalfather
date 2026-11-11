package com.carlesso.goalfather.config

import com.carlesso.goalfather.adapter.out.persistence.repository.ClubJpaRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.CupRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.seed.initialLeague
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.rules.seasonPlan
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Boot sobre um save anterior ao calendário semanal (issue #125, revisão da
 * PR #150): a V17 recria `rounds` vazia, e o [DataInitializer] precisa
 * reiniciar a temporada corrente no calendário novo — senão a liga fica sem
 * encaixe e parada para sempre. Sem Spring: os ports são mocks do MockK.
 */
class DataInitializerTest {

    private val league = initialLeague()
    private val clubRepo = mockk<ClubJpaRepository> { every { count() } returns league.clubs.size.toLong() }
    private val clubs = mockk<ClubRepository>(relaxed = true) { coEvery { findAll() } returns league.clubs }
    private val leagueRepo = mockk<LeagueRepository>(relaxed = true)
    private val cups = mockk<CupRepository>(relaxed = true)
    private val initializer = DataInitializer(clubRepo, mockk(relaxed = true), clubs, leagueRepo, cups)

    /** Tabela no meio da temporada: alguém já jogou, como num save antigo. */
    private fun midSeasonStandings(): List<Standings> = league.initialStandings().map { s ->
        s.copy(round = 2, rows = s.rows.map { it.copy(played = 2, points = 3) })
    }

    @Test
    fun `save sem encaixe corrente reinicia a temporada no primeiro encaixe do plano`() {
        coEvery { leagueRepo.findLatest() } returns null
        coEvery { leagueRepo.currentStandings() } returns midSeasonStandings()
        val savedRound = slot<Round>()
        val savedCup = slot<Cup>()
        coEvery { leagueRepo.saveRound(capture(savedRound)) } returns Unit
        coEvery { cups.save(capture(savedCup)) } returns Unit
        val savedStandings = mutableListOf<Standings>()
        coEvery { leagueRepo.saveStandings(capture(savedStandings)) } returns Unit

        initializer.run()

        val expectedFirst = seasonPlan(league.season, league.clubs, savedCup.captured).first
        assertEquals(expectedFirst, savedRound.captured.matchday)
        assertEquals(RoundStatus.Scheduled, savedRound.captured.status)
        assertEquals(league.season, savedCup.captured.season)
        assertTrue(savedStandings.isNotEmpty() && savedStandings.all { st -> st.rows.all { it.played == 0 } }, "tabelas zeradas")
    }

    @Test
    fun `save que ja tem encaixe corrente nao e reiniciado`() {
        coEvery { leagueRepo.findLatest() } returns mockk<Round>()
        coEvery { leagueRepo.currentStandings() } returns midSeasonStandings()

        initializer.run()

        coVerify(exactly = 0) { leagueRepo.saveRound(any()) }
        coVerify(exactly = 0) { cups.save(any()) }
        coVerify(exactly = 0) { leagueRepo.saveStandings(any()) }
    }
}
