package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.domain.model.world.Reputation
import kotlin.random.Random
import kotlin.random.nextInt

/**
 * Atributos simulados de um jogador (issue #126). Mesmos nomes e faixa de
 * [Player], para virar `Player` sem conversão.
 */
data class PlayerAttributes(
    val overall: Int,
    val pace: Int,
    val shooting: Int,
    val passing: Int,
    val defending: Int,
) {
    init {
        listOf(overall, pace, shooting, passing, defending).forEach {
            require(it in Player.OVERALL_RANGE) { "atributo fora de ${Player.OVERALL_RANGE}: $it" }
        }
    }
}

/**
 * Gera os atributos que **não podem vir de fonte real** (issue #126, política
 * de direitos em `docs/WORLD_DATA.md`): o pack traz só fatos públicos, e o
 * overall e os detalhados saem daqui, por regra nossa — nunca de rating do
 * EA FC/sofifa.
 *
 * **Overall** = força da liga + reputação do clube + curva de idade (com pico
 * mais longo para goleiro) + um ruído pequeno. **Detalhados** = overall +
 * o perfil da posição + ruído: atacante finaliza, zagueiro defende.
 *
 * `Random` entra pelo construtor (regra do CLAUDE.md): a mesma seed produz
 * exatamente os mesmos atributos, então o mundo gerado é reproduzível e
 * testável com igualdade. O gerador CONSOME o `Random` a cada chamada — para
 * que o atributo de um jogador não dependa da ordem em que os outros foram
 * gerados, quem chama pode dar um `Random` por jogador (ex.: seed derivada do
 * id).
 */
class AttributeGenerator(private val random: Random) {

    fun generate(
        position: Position,
        age: Int,
        leagueStrength: LeagueStrength,
        reputation: Reputation,
    ): PlayerAttributes {
        val overall = (expectedOverall(position, age, leagueStrength, reputation) + overallNoise())
            .coerceIn(GENERATED_OVERALL_RANGE)
        val profile = profileOf(position)
        return PlayerAttributes(
            overall = overall,
            pace = detail(overall, profile.pace),
            shooting = detail(overall, profile.shooting),
            passing = detail(overall, profile.passing),
            defending = detail(overall, profile.defending),
        )
    }

    /**
     * Ruído "triangular": a soma de dois dados concentra os valores perto de
     * zero (como 2d6 concentra no 7), então a maioria do elenco fica perto do
     * esperado e poucos destoam. Um `nextInt(-6..6)` só daria a mesma chance a
     * todo desvio.
     */
    private fun overallNoise(): Int = random.nextInt(-3..3) + random.nextInt(-3..3)

    private fun detail(overall: Int, offset: Int): Int =
        (overall + offset + random.nextInt(-DETAIL_NOISE..DETAIL_NOISE)).coerceIn(DETAIL_RANGE)

    /** Deslocamento de cada atributo detalhado em relação ao overall. */
    private data class AttributeProfile(val pace: Int, val shooting: Int, val passing: Int, val defending: Int)

    companion object {
        /**
         * Subfaixa de [Player.OVERALL_RANGE]: nem o pior jogador de um pack
         * profissional é 0, nem o melhor é 99. Fica dentro da faixa do `Player`
         * por construção — `coerceIn` garante sem `require` estourar.
         */
        val GENERATED_OVERALL_RANGE: IntRange = 35..94
        val DETAIL_RANGE: IntRange = 1..99
        private const val DETAIL_NOISE = 3

        /**
         * O overall esperado, SEM ruído — função pura e determinística, separada
         * para os testes conferirem a curva (idade, reputação) sem sorte.
         */
        fun expectedOverall(
            position: Position,
            age: Int,
            leagueStrength: LeagueStrength,
            reputation: Reputation,
        ): Int = leagueStrength.value + reputationOffset(reputation) + ageOffset(position, age)

        /** Reputação 1..100 vira -9..+10: clube grande tem elenco melhor que a média da liga. */
        private fun reputationOffset(reputation: Reputation): Int =
            (reputation.value - Reputation.AVERAGE.value) / 5

        /**
         * Curva de idade: cresce até ~24, platô até o fim do pico, depois cai
         * cada vez mais rápido. Goleiro amadurece mais tarde e dura mais
         * (pico até 33) — é o efeito da POSIÇÃO no overall.
         *
         * `when` com faixas (`in a..b`) no lugar da escada de `if/else if` do Java.
         */
        private fun ageOffset(position: Position, age: Int): Int {
            val peakEnd = if (position == Position.GK) 33 else 30
            return when {
                age <= 17 -> -12
                age in 18..23 -> -(24 - age) * 2
                age <= peakEnd -> 0
                age <= peakEnd + 3 -> -(age - peakEnd) * 2
                else -> (-6 - (age - peakEnd - 3) * 4).coerceAtLeast(-25)
            }
        }

        /**
         * Perfil por posição. `when` exaustivo sem `else`: se uma quinta
         * posição nascer, o compilador aponta aqui.
         */
        private fun profileOf(position: Position): AttributeProfile = when (position) {
            Position.GK -> AttributeProfile(pace = -20, shooting = -35, passing = -10, defending = 5)
            Position.CB -> AttributeProfile(pace = -4, shooting = -18, passing = -5, defending = 8)
            Position.MF -> AttributeProfile(pace = 0, shooting = -2, passing = 6, defending = -5)
            Position.FW -> AttributeProfile(pace = 4, shooting = 8, passing = -3, defending = -22)
        }
    }
}
