package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.event.TrainingEvent
import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.TrainedAttribute
import com.carlesso.goalfather.domain.model.TrainingFocus
import com.carlesso.goalfather.test.makePlayer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Treino semanal com foco escolhido pelo técnico (issue #58) — teste PURO de
 * domínio: sem Spring, sem I/O, com `Random(seed)` fixa. É o critério de
 * aceite "regra pura e determinística com seed fixa, cobrindo cada foco".
 *
 * As asserções são de PROPRIEDADE (quem evoluiu ganhou exatamente +1, o teto
 * é respeitado, jovem evolui mais que veterano) em vez de placares fixos de
 * uma seed: um ajuste de probabilidade não deve quebrar o teste que verifica
 * a regra — só o que verifica a calibragem.
 */
class TrainingRulesTest {

    private fun squadAged(age: Int, size: Int = 20, overall: Int = 70): List<Player> =
        (1L..size.toLong()).map { makePlayer(it, overall = overall, age = age) }

    // ─── DESCANSO ────────────────────────────────────────────────────────

    @Test
    fun `descanso recupera stamina de todo o elenco e nao produz eventos`() {
        val squad = squadAged(age = 20).map { it.copy(stamina = 50) }

        val outcome = train(squad, TrainingFocus.DESCANSO, Random(42))

        assertTrue(outcome.events.isEmpty(), "descanso não evolui nem machuca ninguém")
        for (player in outcome.squad) {
            assertEquals(50 + TrainingFocus.DESCANSO.staminaRecovery, player.stamina)
        }
    }

    @Test
    fun `nenhum foco devolve tudo o que uma partida cansa`() {
        // Invariante de calibragem: se uma semana de descanso zerasse o
        // desgaste de quem jogou, a fadiga da issue #54 (e o sentido de rodar
        // o elenco) desapareceria. O treino alivia, não apaga.
        for (focus in TrainingFocus.entries) {
            assertTrue(
                focus.staminaRecovery < STARTER_STAMINA_LOSS.first,
                "$focus recupera ${focus.staminaRecovery}, o piso de desgaste é ${STARTER_STAMINA_LOSS.first}",
            )
        }
    }

    @Test
    fun `recuperacao de stamina respeita o teto de 100`() {
        val squad = listOf(makePlayer(1, stamina = 95))

        val outcome = train(squad, TrainingFocus.DESCANSO, Random(1))

        assertEquals(100, outcome.squad.single().stamina)
    }

    // ─── Focos técnicos ──────────────────────────────────────────────────

    @Test
    fun `ataque evolui o chute e o overall de quem melhorou`() {
        assertTrainsAttribute(TrainingFocus.ATAQUE, TrainedAttribute.SHOOTING, Player::shooting)
    }

    @Test
    fun `defesa evolui o desarme e o overall de quem melhorou`() {
        assertTrainsAttribute(TrainingFocus.DEFESA, TrainedAttribute.DEFENDING, Player::defending)
    }

    @Test
    fun `fisico evolui a velocidade e ainda devolve stamina`() {
        assertTrainsAttribute(TrainingFocus.FISICO, TrainedAttribute.PACE, Player::pace)

        val squad = squadAged(age = 20).map { it.copy(stamina = 60) }
        val outcome = train(squad, TrainingFocus.FISICO, Random(7))

        for (player in outcome.squad) {
            assertEquals(60 + TrainingFocus.FISICO.staminaRecovery, player.stamina)
        }
    }

    /**
     * Roda várias seeds até aparecer evolução (a chance por jogador é baixa
     * de propósito) e verifica que o ganho foi de exatamente +1 no atributo
     * do foco E no `overall` — é o `overall` que a engine lê, então um ganho
     * que não o tocasse seria decorativo.
     */
    private fun assertTrainsAttribute(
        focus: TrainingFocus,
        attribute: TrainedAttribute,
        read: (Player) -> Int,
    ) {
        val squad = squadAged(age = 20)
        var improvements = 0

        for (seed in 1L..50L) {
            val outcome = train(squad, focus, Random(seed))
            val before = squad.associateBy { it.id }
            val after = outcome.squad.associateBy { it.id }

            for (event in outcome.events.filterIsInstance<TrainingEvent.Improved>()) {
                improvements++
                assertEquals(attribute, event.attribute, "foco $focus treina $attribute")
                val old = before.getValue(event.player.id)
                val new = after.getValue(event.player.id)
                assertEquals(read(old) + TRAINING_ATTRIBUTE_GAIN, read(new))
                assertEquals(old.overall + TRAINING_ATTRIBUTE_GAIN, new.overall)
            }
            for (player in outcome.squad) {
                val old = before.getValue(player.id)
                assertTrue(read(player) >= read(old), "treino nunca piora o atributo treinado")
                // Nenhum foco treina passe: o ganho fica confinado ao atributo
                // do foco (mais o `overall`, que o resume).
                assertEquals(old.passing, player.passing)
            }
        }

        assertTrue(improvements > 0, "em 50 semanas alguém deveria ter evoluído com $focus")
    }

    @Test
    fun `ganho respeita o invariante 0 a 99 e nao inventa evolucao no teto`() {
        val capped = squadAged(age = 18, overall = Player.OVERALL_RANGE.last)

        for (seed in 1L..50L) {
            val outcome = train(capped, TrainingFocus.ATAQUE, Random(seed))

            assertTrue(
                outcome.events.none { it is TrainingEvent.Improved },
                "jogador no teto não 'evolui' — não há o que ganhar",
            )
            for (player in outcome.squad) {
                assertTrue(player.overall in Player.OVERALL_RANGE)
                assertTrue(player.shooting in Player.OVERALL_RANGE)
            }
        }
    }

    @Test
    fun `jovens evoluem mais que veteranos com as mesmas seeds`() {
        val young = squadAged(age = YOUNG_MAX_AGE - 2)
        val veterans = squadAged(age = PEAK_MAX_AGE + 3)

        val youngGains = countImprovements(young, TrainingFocus.ATAQUE)
        val veteranGains = countImprovements(veterans, TrainingFocus.ATAQUE)

        assertTrue(
            youngGains > veteranGains,
            "jovens ($youngGains) deveriam evoluir mais que veteranos ($veteranGains)",
        )
    }

    private fun countImprovements(squad: List<Player>, focus: TrainingFocus): Int =
        (1L..100L).sumOf { seed ->
            train(squad, focus, Random(seed)).events.count { it is TrainingEvent.Improved }
        }

    // ─── Lesão de treino ─────────────────────────────────────────────────

    @Test
    fun `treino intenso machuca de vez em quando e o afastamento e curto`() {
        val squad = squadAged(age = 25)
        var injuries = 0

        for (seed in 1L..50L) {
            val outcome = train(squad, TrainingFocus.FISICO, Random(seed))
            for (event in outcome.events.filterIsInstance<TrainingEvent.Injured>()) {
                injuries++
                assertEquals(TRAINING_INJURY_ROUNDS, event.roundsOut)
                assertEquals(Availability.Injured(TRAINING_INJURY_ROUNDS), event.player.availability)
            }
        }

        assertTrue(injuries > 0, "em 50 semanas de treino físico alguém deveria se machucar")
    }

    @Test
    fun `lesionado nao treina nem se machuca de novo, so recupera`() {
        val injured = makePlayer(1, age = 19, stamina = 40)
            .copy(availability = Availability.Injured(3))
        val squad = listOf(injured)

        for (seed in 1L..50L) {
            val outcome = train(squad, TrainingFocus.FISICO, Random(seed))
            val after = outcome.squad.single()

            assertTrue(outcome.events.isEmpty(), "quem está no departamento médico não treina")
            assertEquals(injured.overall, after.overall)
            assertEquals(injured.pace, after.pace)
            // O contador de rodadas é responsabilidade de `applyRoundFitness` —
            // o treino não pode adiantar nem atrasar a recuperação.
            assertEquals(Availability.Injured(3), after.availability)
            assertEquals(40 + TrainingFocus.FISICO.staminaRecovery, after.stamina)
        }
    }

    // ─── Determinismo ────────────────────────────────────────────────────

    @Test
    fun `mesma seed produz exatamente a mesma semana`() {
        val squad = squadAged(age = 20)

        val a = train(squad, TrainingFocus.ATAQUE, Random(2026))
        val b = train(squad, TrainingFocus.ATAQUE, Random(2026))

        assertEquals(a.squad, b.squad)
        assertEquals(a.events, b.events)
    }

    @Test
    fun `seeds diferentes produzem semanas diferentes`() {
        val squad = squadAged(age = 20, size = 40)

        val a = train(squad, TrainingFocus.ATAQUE, Random(1))
        val b = train(squad, TrainingFocus.ATAQUE, Random(2))

        assertNotEquals(a.squad, b.squad)
    }

    @Test
    fun `seed de treino nao repete a de desgaste nem colide entre clubes`() {
        // Sem o salt, treino e fadiga da mesma rodada/clube sorteariam a mesma
        // sequência — quem cansasse mais evoluiria mais, por acidente.
        assertNotEquals(fitnessSeed(7, ClubId(3)), trainingSeed(7, ClubId(3)))
        assertNotEquals(agingSeed(7, ClubId(3)), trainingSeed(7, ClubId(3)))

        // Empacotamento em faixas disjuntas: rodada 1/clube 1000 não colide
        // com rodada 2/clube 0 (o que um `rodada * 1000 + clube` faria).
        assertNotEquals(trainingSeed(1, ClubId(1000)), trainingSeed(2, ClubId(0)))
    }
}
