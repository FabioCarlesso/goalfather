package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.application.seed.initialLeague
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CupTie
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.KnockoutPhase
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.SeasonCompetitions
import com.carlesso.goalfather.domain.model.Slot
import com.carlesso.goalfather.test.makeClub
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plano da temporada do mundo atual (issue #125): liga de cada divisão + copa,
 * encaixadas na semana pelo agendador.
 */
class SeasonCalendarTest {

    private val twelve = (1L..12L).map { makeClub(id = it, name = "C$it", division = Division(if (it <= 6) 1 else 2)) }
    private val cup = drawCup(2026, twelve, Random(cupDrawSeed(2026)))
    private val plan = seasonPlan(2026, twelve, cup)

    private fun stagesAt(index: Int) =
        plan.roundAt(plan.schedule.matchdays[index], twelve, cup).matches.map { it.stage }.toSet()

    @Test
    fun `seed - 5 rodadas de liga por divisao e 4 fases de copa, cada uma no seu encaixe`() {
        val league = plan.schedule.rounds.filter { it.competition == SeasonCompetitions.leagueId(Division.FIRST) }
        val cupRounds = plan.schedule.rounds.filter { it.competition == SeasonCompetitions.CUP_ID }
        assertEquals(5, league.size)
        assertEquals(4, cupRounds.size)
        // As duas divisões jogam a mesma rodada no mesmo domingo; a copa, na quarta.
        assertEquals(9, plan.schedule.matchdays.size)
        assertTrue(league.all { it.matchday.slot == Slot.WEEKEND })
        assertTrue(cupRounds.all { it.matchday.slot == Slot.MIDWEEK })
    }

    @Test
    fun `primeiro encaixe e de liga em janeiro e a copa termina antes da ultima rodada`() {
        assertEquals(setOf<RoundStage>(RoundStage.League(1)), stagesAt(0))
        assertEquals(1, plan.first?.date?.monthValue)
        val finalAt = assertNotNull(plan.schedule.lastOf(SeasonCompetitions.CUP_ID))
        val lastLeague = assertNotNull(plan.schedule.lastOf(SeasonCompetitions.leagueId(Division.FIRST)))
        assertTrue(finalAt < lastLeague, "o campeão da copa chega à virada")
        assertTrue(plan.schedule.matchdays.all { it.date.year == 2026 })
    }

    @Test
    fun `copa pausa nas datas FIFA e a liga brasileira nao precisa`() {
        val fifa = interimFifaWindows(2026)
        val cupRounds = plan.schedule.rounds.filter { it.competition == SeasonCompetitions.CUP_ID }
        assertTrue(cupRounds.none { fifa.reserves(it.matchday.week) })
        val league = plan.competitions.getValue(SeasonCompetitions.leagueId(Division.FIRST)).season
        assertTrue(!league.pausesOnFifaWindows)
    }

    @Test
    fun `liga e copa se intercalam como antes - L C L C L C L C L`() {
        val kinds = plan.schedule.matchdays.indices.map { i ->
            if (stagesAt(i).all { it is RoundStage.League }) "L" else "C"
        }
        assertEquals("LCLCLCLCL", kinds.joinToString(""))
    }

    @Test
    fun `encaixe de copa traz os confrontos em aberto da fase corrente`() {
        val cupSlot = plan.schedule.matchdays[1]
        val matches = plan.roundAt(cupSlot, twelve, cup).matches
        assertEquals(4, matches.size) // 12 clubes numa chave de 16: 4 jogos e 4 folgas
        assertTrue(matches.all { it.stage == RoundStage.Cup(KnockoutPhase.ROUND_OF_16) && it.isKnockout })
    }

    @Test
    fun `plano e funcao pura dos clubes - recalcular da o mesmo calendario`() {
        assertEquals(plan, seasonPlan(2026, twelve.shuffled(Random(3)), cup))
    }

    @Test
    fun `mundo do seed cabe no calendario`() {
        val league = initialLeague()
        val round = assertNotNull(league.firstRound())
        assertEquals(6, round.matches.size)
        assertTrue(round.matches.all { it.stage == RoundStage.League(1) })
    }

    // ─── Próximo jogo do clube ────────────────────────────────────────────

    @Test
    fun `proximo jogo do clube pula encaixes em que ele nao joga`() {
        // Clube que folga na 1ª fase da copa: depois da rodada 1, o próximo jogo
        // é a rodada 2 da liga, não o encaixe de copa.
        val bye = cup.stages.first().ties
            .filterIsInstance<CupTie.Bye>()
            .first().club.id
        val next = assertNotNull(plan.nextMatchOf(bye, plan.schedule.matchdays[0], twelve, cup))
        assertEquals(plan.schedule.matchdays[2], next.matchday)
        assertEquals(RoundStage.League(2), next.match.stage)
        val division = twelve.single { it.id == bye }.division
        assertEquals(SeasonCompetitions.leagueName(division), next.match.competitionName)
    }

    @Test
    fun `proximo jogo de quem esta na copa e o confronto da fase corrente`() {
        val playing = cup.stages.first().ties
            .filterIsInstance<CupTie.Fixture>()
            .first().home.id
        val next = assertNotNull(plan.nextMatchOf(playing, plan.schedule.matchdays[0], twelve, cup))
        assertEquals(plan.schedule.matchdays[1], next.matchday)
        assertEquals(SeasonCompetitions.CUP_ID, next.match.competition)
    }

    @Test
    fun `com a fase da copa em jogo nenhum encaixe de copa adiante e conhecido`() {
        val playing = cup.stages.first().ties
            .filterIsInstance<CupTie.Fixture>()
            .first().home.id
        val next = assertNotNull(plan.nextMatchOf(playing, plan.schedule.matchdays[1], twelve, cup, cupStageInPlay = true))
        assertTrue(next.match.stage is RoundStage.League)
    }

    @Test
    fun `sem jogo adiante devolve null`() {
        assertNull(plan.nextMatchOf(ClubId(1), plan.schedule.matchdays.last(), twelve, cup))
    }
}
