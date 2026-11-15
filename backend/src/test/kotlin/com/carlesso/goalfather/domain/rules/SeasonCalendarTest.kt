package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.application.seed.initialLeague
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.CupTie
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.CupLeg
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.Slot
import com.carlesso.goalfather.test.COPA_DO_BRASIL
import com.carlesso.goalfather.test.SERIE_A
import com.carlesso.goalfather.test.SERIE_A_NAME
import com.carlesso.goalfather.test.SERIE_B
import com.carlesso.goalfather.test.SERIE_B_NAME
import com.carlesso.goalfather.test.brazilCatalog
import com.carlesso.goalfather.test.makeClub
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plano da temporada de um país pequeno (issues #125/#131): Série A e Série B
 * de 6 clubes em turno único + a Copa do Brasil, encaixadas na semana pelo
 * agendador. O mundo do MVP em tamanho real está em [WorldCalendarTest].
 */
class SeasonCalendarTest {

    private val twelve = (1L..12L).map { makeClub(id = it, name = "C$it", division = Division(if (it <= 6) 1 else 2)) }
    private val catalog = brazilCatalog(legs = 1)
    private val copa = catalog.single { it.id == COPA_DO_BRASIL }
    private val cup = assertNotNull(drawNationalCup(2026, copa, twelve, Random(nationalCupDrawSeed(2026, COPA_DO_BRASIL))))
    private val seasons = mapOf(Countries.BRAZIL.code to 2026)
    private val plan = worldPlan(catalog, twelve, seasons, mapOf(COPA_DO_BRASIL to cup))

    private fun stagesAt(index: Int) =
        plan.roundAt(plan.schedule.matchdays[index], twelve).matches.map { it.stage }.toSet()

    @Test
    fun `seed - 5 rodadas de liga por divisao e 4 fases de copa, cada uma no seu encaixe`() {
        val league = plan.schedule.rounds.filter { it.competition == SERIE_A }
        val cupRounds = plan.schedule.rounds.filter { it.competition == COPA_DO_BRASIL }
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
        val finalAt = assertNotNull(plan.schedule.lastOf(COPA_DO_BRASIL))
        val lastLeague = assertNotNull(plan.schedule.lastOf(SERIE_A))
        assertTrue(finalAt < lastLeague, "o campeão da copa chega à virada")
        assertTrue(plan.schedule.matchdays.all { it.date.year == 2026 })
    }

    @Test
    fun `copa pausa nas datas FIFA e a liga brasileira nao precisa`() {
        val fifa = interimFifaWindows(2026)
        val cupRounds = plan.schedule.rounds.filter { it.competition == COPA_DO_BRASIL }
        assertTrue(cupRounds.none { fifa.reserves(it.matchday.week) })
        val league = plan.competitions.getValue(SERIE_A).season
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
        val matches = plan.roundAt(cupSlot, twelve).matches
        assertEquals(4, matches.size) // 12 clubes: 8 na Primeira fase (4 jogos), 4 entram nas quartas
        assertTrue(matches.all { it.stage == RoundStage.Cup("Primeira fase", CupLeg.Single) && it.isKnockout })
    }

    @Test
    fun `plano e funcao pura dos clubes - recalcular da o mesmo calendario`() {
        assertEquals(plan, worldPlan(catalog, twelve.shuffled(Random(3)), seasons, mapOf(COPA_DO_BRASIL to cup)))
    }

    @Test
    fun `mundo do seed cabe no calendario`() {
        val league = initialLeague()
        val round = assertNotNull(league.firstRound())
        assertEquals(6, round.matches.size)
        assertEquals(setOf(SERIE_A, SERIE_B), round.matches.map { it.competition }.toSet())
        assertTrue(round.matches.all { it.stage == RoundStage.League(1) })
    }

    // ─── Próximo jogo do clube ────────────────────────────────────────────

    @Test
    fun `proximo jogo do clube pula encaixes em que ele nao joga`() {
        // Clube que só entra nas quartas: depois da rodada 1, o próximo jogo é
        // a rodada 2 da liga, não o encaixe da Primeira fase da copa.
        val bye = cup.phases[1].entrants.first().id
        val next = assertNotNull(plan.nextMatchOf(bye, plan.schedule.matchdays[0], twelve))
        assertEquals(plan.schedule.matchdays[2], next.matchday)
        assertEquals(RoundStage.League(2), next.match.stage)
        val division = twelve.single { it.id == bye }.division
        assertEquals(if (division == Division.FIRST) SERIE_A_NAME else SERIE_B_NAME, next.match.competitionName)
    }

    @Test
    fun `proximo jogo de quem esta na copa e o confronto da fase corrente`() {
        val playing = cup.stages.first().ties
            .filterIsInstance<CupTie.Single>()
            .first().home.id
        val next = assertNotNull(plan.nextMatchOf(playing, plan.schedule.matchdays[0], twelve))
        assertEquals(plan.schedule.matchdays[1], next.matchday)
        assertEquals(COPA_DO_BRASIL, next.match.competition)
    }

    @Test
    fun `com a fase da copa em jogo nenhum encaixe de copa adiante e conhecido`() {
        val playing = cup.stages.first().ties
            .filterIsInstance<CupTie.Single>()
            .first().home.id
        val next = assertNotNull(plan.nextMatchOf(playing, plan.schedule.matchdays[1], twelve, cupsInPlay = setOf(COPA_DO_BRASIL)))
        assertTrue(next.match.stage is RoundStage.League)
    }

    @Test
    fun `sem jogo adiante devolve null`() {
        assertNull(plan.nextMatchOf(ClubId(1), plan.schedule.matchdays.last(), twelve))
    }
}
