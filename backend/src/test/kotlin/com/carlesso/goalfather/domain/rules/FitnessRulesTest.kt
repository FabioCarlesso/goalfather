package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.teamStrength
import com.carlesso.goalfather.test.makePlayer
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Fadiga, recuperação e lesões com duração (issue #54) — teste PURO de
 * domínio: sem Spring, sem I/O, com `Random(seed)` fixa. É o critério de
 * aceite "determinístico com seed fixa" da issue.
 */
class FitnessRulesTest {

    private val squad = (1L..14L).map { makePlayer(it) }
    private val starters = (1L..11L).map { PlayerId(it) }.toSet()

    @Test
    fun `titulares perdem stamina na faixa do prototipo e reservas recuperam`() {
        val after = applyRoundFitness(squad, starters, rng = Random(42))
            .associateBy { it.id.value }

        for (id in 1L..11L) {
            val lost = 100 - after.getValue(id).stamina
            assertTrue(
                lost in STARTER_STAMINA_LOSS,
                "titular $id deveria perder ${STARTER_STAMINA_LOSS} de stamina, perdeu $lost",
            )
        }
        // Reservas já estavam em 100: recuperam, mas o teto segura.
        for (id in 12L..14L) {
            assertEquals(100, after.getValue(id).stamina, "reserva $id não deveria passar de 100")
        }
    }

    @Test
    fun `reserva cansado recupera ate o teto de 100`() {
        val bench = listOf(makePlayer(1, stamina = 50), makePlayer(2, stamina = 95))

        val after = applyRoundFitness(bench, starterIds = emptySet(), rng = Random(1))

        assertEquals(50 + BENCH_STAMINA_RECOVERY, after[0].stamina)
        assertEquals(100, after[1].stamina, "recuperação não pode ultrapassar 100")
    }

    @Test
    fun `desgaste de partida respeita o piso e nunca zera o jogador`() {
        // Titular já no piso: jogar de novo não o leva abaixo dele.
        val exhausted = listOf(makePlayer(1, stamina = STAMINA_MATCH_FLOOR))

        val after = applyRoundFitness(exhausted, setOf(PlayerId(1)), rng = Random(7))

        assertEquals(STAMINA_MATCH_FLOOR, after[0].stamina)
    }

    @Test
    fun `mesma seed produz exatamente o mesmo desgaste`() {
        val a = applyRoundFitness(squad, starters, rng = Random(2026))
        val b = applyRoundFitness(squad, starters, rng = Random(2026))

        assertEquals(a.map { it.stamina }, b.map { it.stamina })
    }

    @Test
    fun `seeds diferentes produzem desgastes diferentes`() {
        val a = applyRoundFitness(squad, starters, rng = Random(1))
        val b = applyRoundFitness(squad, starters, rng = Random(2))

        assertTrue(
            a.map { it.stamina } != b.map { it.stamina },
            "seeds distintas deveriam divergir — se falhar, o RNG não está sendo consumido",
        )
    }

    // ─── Lesões com duração ───────────────────────────────────────────────

    @Test
    fun `lesao nova entra em vigor com a duracao sorteada na partida`() {
        val after = applyRoundFitness(
            squad = listOf(makePlayer(1)),
            starterIds = setOf(PlayerId(1)),
            rng = Random(1),
            newInjuries = mapOf(PlayerId(1) to 3),
        )

        assertEquals(Availability.Injured(3), after[0].availability)
        assertTrue(after[0].injured)
    }

    @Test
    fun `lesao em curso decrementa uma rodada por vez ate liberar o jogador`() {
        var player = makePlayer(1, availability = Availability.Injured(2))

        player = applyRoundFitness(listOf(player), emptySet(), rng = Random(1)).single()
        assertEquals(Availability.Injured(1), player.availability)

        player = applyRoundFitness(listOf(player), emptySet(), rng = Random(1)).single()
        assertEquals(Availability.Available, player.availability, "deveria estar liberado")
        assertTrue(!player.injured)
    }

    @Test
    fun `lesao sofrida na rodada nao e decrementada na mesma virada`() {
        // Se o decremento rodasse DEPOIS, uma lesão de 1 rodada nasceria já curada.
        val after = applyRoundFitness(
            squad = listOf(makePlayer(1)),
            starterIds = setOf(PlayerId(1)),
            rng = Random(1),
            newInjuries = mapOf(PlayerId(1) to 1),
        )

        assertEquals(Availability.Injured(1), after[0].availability)
    }

    @Test
    fun `lesao nova nunca encurta a que ja estava em curso`() {
        val after = applyRoundFitness(
            squad = listOf(makePlayer(1, availability = Availability.Injured(4))),
            starterIds = emptySet(),
            rng = Random(1),
            // 4 em curso vira 3 no decremento; a nova de 2 não pode reduzir isso.
            newInjuries = mapOf(PlayerId(1) to 2),
        )

        assertEquals(Availability.Injured(3), after[0].availability)
    }

    @Test
    fun `duracao sorteada fica sempre dentro da faixa`() {
        repeat(200) { seed ->
            assertTrue(drawInjuryDuration(Random(seed)) in INJURY_DURATION)
        }
    }

    @Test
    fun `lesao de zero rodadas e um estado inexistente`() {
        // O sealed impede "lesionado sem duração": não há como construir.
        assertThrows<IllegalArgumentException> { Availability.Injured(0) }
    }

    // ─── Stamina × força do time (critério de aceite da issue) ────────────

    @Test
    fun `rendimento e integral acima do piso de forma`() {
        val fresh = makePlayer(1, overall = 80, stamina = 100)
        val atFloor = makePlayer(2, overall = 80, stamina = STAMINA_FULL_PERFORMANCE)

        assertEquals(80.0, fresh.effectiveOverall())
        assertEquals(80.0, atFloor.effectiveOverall(), "a função é contínua no piso — sem degrau")
    }

    @Test
    fun `rendimento cai proporcionalmente abaixo do piso de forma`() {
        val tired = makePlayer(1, overall = 80, stamina = 35)

        assertEquals(80.0 * 35 / STAMINA_FULL_PERFORMANCE, tired.effectiveOverall())
    }

    @Test
    fun `lineups identicos com staminas distintas tem forcas distintas`() {
        // Mesmo overall, mesma formação — só a forma física muda.
        val rested = Lineup(
            players = (1L..11L).map { makePlayer(it, overall = 80, stamina = 100) },
            formation = Formation.F_4_4_2,
        )
        val worn = Lineup(
            players = (1L..11L).map { makePlayer(it, overall = 80, stamina = 40) },
            formation = Formation.F_4_4_2,
        )

        assertEquals(80.0, rested.teamStrength())
        assertTrue(
            worn.teamStrength() < rested.teamStrength(),
            "time desgastado deveria ser mais fraco: ${worn.teamStrength()} vs ${rested.teamStrength()}",
        )
    }

    // ─── Seed determinística ──────────────────────────────────────────────

    @Test
    fun `seed nao colide entre pares distintos de rodada e clube`() {
        // Regressão: `rodada * 1000 + clube` colidia com id de clube >= 1000 —
        // rodada 1/clube 1000 dava a MESMA seed que rodada 2/clube 0, e os dois
        // elencos cansariam de forma idêntica.
        assertNotEquals(
            fitnessSeed(1, ClubId(1000)),
            fitnessSeed(2, ClubId(0)),
        )

        // Injetiva numa varredura ampla de rodadas × clubes.
        val seeds = (1..40).flatMap { round ->
            (0L..2_000L).map { fitnessSeed(round, ClubId(it)) }
        }
        assertEquals(seeds.size, seeds.toSet().size, "toda combinação deveria dar uma seed única")
    }

    @Test
    fun `seed e estavel para o mesmo par`() {
        assertEquals(fitnessSeed(7, ClubId(3)), fitnessSeed(7, ClubId(3)))
    }

    // ─── Departamento médico ──────────────────────────────────────────────

    @Test
    fun `tratamento devolve stamina respeitando o teto e encurta a lesao`() {
        val treated = applyMedicalTreatment(
            listOf(
                makePlayer(1, stamina = 40),
                makePlayer(2, stamina = 95),
                makePlayer(3, stamina = 60, availability = Availability.Injured(3)),
            ),
        )

        assertEquals(40 + MEDICAL_STAMINA_RECOVERY, treated[0].stamina)
        assertEquals(100, treated[1].stamina, "tratamento não pode ultrapassar 100")
        assertEquals(Availability.Injured(2), treated[2].availability)
    }

    @Test
    fun `tratamento na ultima rodada de lesao libera o jogador`() {
        val treated = applyMedicalTreatment(
            listOf(makePlayer(1, availability = Availability.Injured(1))),
        )

        assertIs<Availability.Available>(treated[0].availability)
    }
}
