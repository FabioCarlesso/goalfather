package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Player
import kotlin.random.Random

/**
 * Envelhecimento de elenco na virada de temporada (issue #55). Funções PURAS —
 * sem Spring, sem I/O — com a aleatoriedade INJETADA por parâmetro: a mesma
 * seed produz sempre a mesma temporada, o que permite testar a virada inteira
 * sem subir contexto.
 *
 * A regra dá horizonte de longo prazo ao jogo: até então `Player.age` era um
 * número decorativo e um veterano de 34 valia exatamente o mesmo que um jovem
 * de 21 com o mesmo `overall`. Agora a idade é um ativo que se valoriza (até os
 * 23), estabiliza (24–29) e se deprecia (30+) — e um dia acaba.
 */

/** Última idade da faixa em que o jogador ainda está em formação. */
const val YOUNG_MAX_AGE: Int = 23

/** Última idade do auge — daí em diante o corpo começa a cobrar. */
const val PEAK_MAX_AGE: Int = 29

/**
 * A partir desta idade um jogador de `overall` baixo pendura as chuteiras.
 * "Baixo" é [RETIREMENT_MAX_OVERALL]: quem ainda rende segue jogando, como o
 * veterano de elite que estica a carreira até os 40.
 */
const val RETIREMENT_MIN_AGE: Int = 36

/** Teto de `overall` que ainda condena um trintão à aposentadoria. */
const val RETIREMENT_MAX_OVERALL: Int = 70

/**
 * Teto duro de carreira: ninguém joga além disso, por melhor que seja.
 *
 * Também é o que mantém o invariante `age in 15..50` do [Player] fora de
 * perigo — sem um teto, um craque eterno acabaria estourando o `require` na
 * virada de temporada de número trinta e poucos.
 */
const val FORCED_RETIREMENT_AGE: Int = 41

/**
 * Faixa etária e a variação de atributos que ela sorteia por temporada.
 *
 * Enum com propriedade (idioma Kotlin: o enum carrega o dado em vez de um
 * `when` espalhado por aí) e as faixas são ASSIMÉTRICAS de propósito: um jovem
 * pode estagnar, mas nunca desabar; um veterano pode ter um último bom ano,
 * mas não vira craque aos 35. O valor esperado de cada faixa conta a história —
 * +1 no jovem, 0 no auge, −1 no veterano.
 */
enum class AgeBand(val overallDelta: IntRange) {
    /** Até [YOUNG_MAX_AGE]: em formação, tende a evoluir. */
    YOUNG(-1..3),

    /** [YOUNG_MAX_AGE]+1 a [PEAK_MAX_AGE]: auge, oscila pouco. */
    PEAK(-1..1),

    /** Acima de [PEAK_MAX_AGE]: tende a regredir. */
    VETERAN(-3..1),
    ;

    companion object {
        fun of(age: Int): AgeBand = when {
            age <= YOUNG_MAX_AGE -> YOUNG
            age <= PEAK_MAX_AGE -> PEAK
            else -> VETERAN
        }
    }
}

/**
 * O que aconteceu com um jogador na virada de temporada.
 *
 * `sealed interface` em vez de `Player?` (que era a assinatura sugerida na
 * issue): o `null` só diria "sumiu", sem distinguir quem evoluiu de quem
 * regrediu, e obrigaria todo caller a recalcular a diferença de `overall` para
 * contar a história. Aqui o `when` é exaustivo e o compilador cobra o caso novo
 * se um dia aparecer um (empréstimo, promoção da base).
 *
 * Todas as variantes carregam o [player] resultante da virada — inclusive
 * [Retired], onde ele serve para o extrato de fim de temporada ("Fulano, 37,
 * se aposentou") mesmo tendo saído do elenco.
 */
sealed interface AgingOutcome {
    val player: Player

    /** Ganhou atributos nesta temporada. */
    data class Evolved(override val player: Player, val overallDelta: Int) : AgingOutcome

    /** Mais um ano de idade, mesmo nível técnico. */
    data class Steady(override val player: Player) : AgingOutcome

    /** Perdeu atributos nesta temporada. */
    data class Regressed(override val player: Player, val overallDelta: Int) : AgingOutcome

    /** Saiu do elenco de vez — libera a folha salarial. */
    data class Retired(override val player: Player) : AgingOutcome
}

/**
 * Passa UMA temporada para o jogador: +1 ano de idade e uma variação de
 * atributos sorteada na faixa da [AgeBand] correspondente à idade NOVA.
 *
 * A faixa é decidida pela idade já incrementada porque é ela que vale na
 * temporada que começa — quem faz 24 anos entra no auge já nesta virada.
 *
 * O `overall` e os quatro atributos derivados andam juntos, no mesmo delta:
 * um único sorteio por jogador mantém o consumo do RNG previsível (a ordem do
 * elenco basta para reproduzir a temporada) e evita que `overall` descole dos
 * atributos que deveriam sustentá-lo.
 */
fun Player.ageOneSeason(rng: Random): AgingOutcome {
    // Quem já bateu o teto de carreira se aposenta SEM sortear nada: envelhecer
    // mais um ano poderia estourar o invariante de idade do Player, e nenhum
    // delta mudaria o desfecho.
    if (age >= FORCED_RETIREMENT_AGE) return AgingOutcome.Retired(this)

    val nextAge = age + 1
    val delta = AgeBand.of(nextAge).overallDelta.random(rng)
    val aged = copy(
        age = nextAge,
        overall = shift(overall, delta),
        pace = shift(pace, delta),
        shooting = shift(shooting, delta),
        passing = shift(passing, delta),
        defending = shift(defending, delta),
    )

    return when {
        aged.retires() -> AgingOutcome.Retired(aged)
        delta > 0 -> AgingOutcome.Evolved(aged, delta)
        delta < 0 -> AgingOutcome.Regressed(aged, delta)
        else -> AgingOutcome.Steady(aged)
    }
}

/**
 * Envelhece o elenco inteiro numa passada. Consome o RNG na ordem do elenco:
 * dois elencos iguais com a mesma seed produzem exatamente a mesma temporada.
 */
fun ageSquadOneSeason(squad: List<Player>, rng: Random): List<AgingOutcome> =
    squad.map { it.ageOneSeason(rng) }

/**
 * Quem continua no clube na temporada nova. O `when` exaustivo é o ponto:
 * a aposentadoria não é um `null` a ser filtrado por engano, é uma variante
 * que o compilador obriga a tratar.
 */
fun List<AgingOutcome>.remainingSquad(): List<Player> = mapNotNull { outcome ->
    when (outcome) {
        is AgingOutcome.Retired -> null
        is AgingOutcome.Evolved,
        is AgingOutcome.Steady,
        is AgingOutcome.Regressed,
        -> outcome.player
    }
}

/**
 * Seed determinística do envelhecimento de um clube numa temporada.
 *
 * Mesmo empacotamento em faixas disjuntas do `Long` de [fitnessSeed] (somar os
 * dois números colidiria assim que um id de clube crescesse), mais um salt: sem
 * ele, `agingSeed(7, clube)` e `fitnessSeed(7, clube)` dariam a MESMA sequência
 * na temporada 7/rodada 7. XOR com constante é bijetivo, então a injetividade
 * do empacotamento se mantém.
 */
fun agingSeed(season: Int, clubId: ClubId): Long =
    ((season.toLong() shl 32) xor clubId.value) xor AGING_SEED_SALT

/** "AGING" em hexa — só para descolar os streams de aging e fitness. */
private const val AGING_SEED_SALT: Long = 0x4147_494E_47L

/** Aposenta o veterano que já não rende — ou qualquer um no teto duro de carreira. */
private fun Player.retires(): Boolean =
    age >= FORCED_RETIREMENT_AGE || (age >= RETIREMENT_MIN_AGE && overall < RETIREMENT_MAX_OVERALL)

/**
 * Move um atributo respeitando o invariante `0..99` do [Player] — o `require`
 * do construtor é a última linha de defesa, mas quem trunca é a regra.
 */
private fun shift(value: Int, delta: Int): Int = (value + delta).coerceIn(0, 99)
