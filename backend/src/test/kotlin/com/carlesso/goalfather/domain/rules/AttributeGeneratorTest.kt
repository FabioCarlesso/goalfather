package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.domain.model.world.Reputation
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Gerador de atributos simulados (issue #126). Domínio puro, sem Spring. */
class AttributeGeneratorTest {

    private val strength = LeagueStrength(72)
    private val reputation = Reputation(60)

    /** Um elenco "qualquer" gerado em sequência pelo mesmo gerador. */
    private fun squadWith(seed: Int): List<PlayerAttributes> {
        val generator = AttributeGenerator(Random(seed))
        return Position.entries.flatMap { position ->
            (18..36 step 3).map { age -> generator.generate(position, age, strength, reputation) }
        }
    }

    @Test
    fun `mesma seed gera exatamente os mesmos atributos`() {
        assertEquals(squadWith(42), squadWith(42))
    }

    @Test
    fun `seeds diferentes geram atributos diferentes`() {
        assertNotEquals(squadWith(42), squadWith(43))
    }

    @Test
    fun `respeita as faixas de Player em qualquer entrada`() {
        val generator = AttributeGenerator(Random(7))
        // Varre os extremos de todas as entradas — inclusive as combinações
        // que empurrariam o overall para fora de 0..99 sem o `coerceIn`.
        for (position in Position.entries) {
            for (age in Player.AGE_RANGE) {
                for (s in listOf(1, 50, 99)) {
                    for (r in listOf(1, 50, 100)) {
                        val attrs = generator.generate(position, age, LeagueStrength(s), Reputation(r))
                        assertTrue(attrs.overall in AttributeGenerator.GENERATED_OVERALL_RANGE, "$attrs")
                        listOf(attrs.pace, attrs.shooting, attrs.passing, attrs.defending).forEach {
                            assertTrue(it in Player.OVERALL_RANGE, "$attrs")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `liga mais forte e clube de mais reputacao dao overall esperado maior`() {
        val base = AttributeGenerator.expectedOverall(Position.MF, 27, LeagueStrength(65), Reputation(50))
        assertTrue(AttributeGenerator.expectedOverall(Position.MF, 27, LeagueStrength(78), Reputation(50)) > base)
        assertTrue(AttributeGenerator.expectedOverall(Position.MF, 27, LeagueStrength(65), Reputation(90)) > base)
        assertTrue(AttributeGenerator.expectedOverall(Position.MF, 27, LeagueStrength(65), Reputation(10)) < base)
    }

    @Test
    fun `curva de idade cresce ate o pico e cai depois, mais tarde para goleiro`() {
        fun expected(position: Position, age: Int) =
            AttributeGenerator.expectedOverall(position, age, strength, Reputation.AVERAGE)

        assertTrue(expected(Position.FW, 18) < expected(Position.FW, 22))
        assertTrue(expected(Position.FW, 22) < expected(Position.FW, 27))
        assertTrue(expected(Position.FW, 34) < expected(Position.FW, 27))
        assertTrue(expected(Position.FW, 38) < expected(Position.FW, 34))
        // Goleiro de 33 ainda está no pico; atacante de 33 já caiu.
        assertEquals(expected(Position.GK, 27), expected(Position.GK, 33))
        assertTrue(expected(Position.GK, 33) > expected(Position.FW, 33))
    }

    @Test
    fun `detalhados seguem o perfil da posicao`() {
        // Média de muitos jogadores para o ruído não decidir o teste.
        fun average(position: Position, pick: (PlayerAttributes) -> Int): Double {
            val generator = AttributeGenerator(Random(1))
            return List(200) { generator.generate(position, 27, strength, reputation) }.map(pick).average()
        }

        assertTrue(average(Position.FW) { it.shooting } > average(Position.CB) { it.shooting })
        assertTrue(average(Position.CB) { it.defending } > average(Position.FW) { it.defending })
        assertTrue(average(Position.MF) { it.passing } > average(Position.GK) { it.passing })
    }

    @Test
    fun `atributos gerados viram Player sem violar invariantes`() {
        val attrs = AttributeGenerator(Random(3)).generate(Position.GK, 30, strength, reputation)
        val player = Player(
            id = PlayerId(1),
            name = "Teste",
            position = Position.GK,
            overall = attrs.overall,
            pace = attrs.pace,
            shooting = attrs.shooting,
            passing = attrs.passing,
            defending = attrs.defending,
            salary = Money.ZERO,
            age = 30,
        )
        assertEquals(attrs.overall, player.overall)
    }
}
