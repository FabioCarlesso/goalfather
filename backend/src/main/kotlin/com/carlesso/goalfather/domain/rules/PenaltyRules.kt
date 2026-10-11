package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.event.PenaltyKick
import com.carlesso.goalfather.domain.model.PenaltyScore
import com.carlesso.goalfather.domain.model.Player
import kotlin.random.Random

/**
 * Disputa de pênaltis do mata-mata (issue #61). Funções PURAS com o RNG por
 * parâmetro: a engine passa o MESMO `Random` da partida, já depois do minuto
 * 90, então mesma seed = mesmo jogo = mesma disputa.
 *
 * Formato oficial: série de [PENALTY_SERIES] cobranças alternadas (mandante
 * primeiro), encerrada antes quando um lado já não alcança o outro, e depois
 * morte súbita em pares até alguém errar sozinho.
 */

/** Cobranças por lado na série regular. */
const val PENALTY_SERIES: Int = 5

/**
 * Faixa da chance de conversão. O piso garante que até o pior batedor contra
 * o melhor goleiro converte mais da metade — pênalti é vantagem de quem bate —
 * e o teto que nenhum duelo vira formalidade. Os dois juntos também deixam a
 * morte súbita terminar rápido: a chance de um par empatar fica abaixo de 83%.
 */
val PENALTY_CONVERSION_RANGE: ClosedFloatingPointRange<Double> = 0.55..0.92

/** Conversão de um duelo equilibrado (batedor e goleiro de mesmo nível). */
private const val PENALTY_BASE_CONVERSION = 0.75

/** Quanto cada ponto de overall de vantagem do batedor sobre o goleiro pesa. */
private const val PENALTY_SKILL_WEIGHT = 0.005

/** Goleiro ausente (time sem GK escalado): qualquer um no gol, e mal. */
private const val NO_GOALKEEPER_OVERALL = 40.0

/**
 * Teto de pares na morte súbita. Com a conversão em [PENALTY_CONVERSION_RANGE],
 * a chance de chegar aqui é da ordem de 0,83^1000 — o `check` documenta a
 * premissa de terminação em vez de deixar um laço sem fim teórico.
 */
private const val MAX_SUDDEN_DEATH_PAIRS = 1_000

/** Resultado da disputa: o placar e as cobranças, na ordem em que saíram. */
data class Shootout(
    val score: PenaltyScore,
    val kicks: List<PenaltyKick>,
)

/**
 * Ordem de batedores: quem mais finaliza no jogo (peso de posição da issue
 * #57 — atacante, meia, zagueiro) bate primeiro, e o melhor de cada posição na
 * frente. O goleiro, com peso 0, vai para o fim da fila. O `id` desempata para
 * a ordem ser estável. Na morte súbita a fila recomeça do primeiro.
 */
fun List<Player>.penaltyTakers(): List<Player> = sortedWith(
    compareByDescending<Player> { it.position.scoringWeight }
        .thenByDescending { it.overall }
        .thenBy { it.id.value },
)

/**
 * Chance de [taker] converter contra [keeper]. Usa o overall EFETIVO dos dois
 * (issue #54): perna cansada no fim de um calendário apertado também erra
 * pênalti — é a sinergia da copa com a fadiga que a issue #61 menciona.
 */
fun penaltyConversion(taker: Player, keeper: Player?): Double {
    val keeperOverall = keeper?.effectiveOverall() ?: NO_GOALKEEPER_OVERALL
    val edge = taker.effectiveOverall() - keeperOverall
    return (PENALTY_BASE_CONVERSION + edge * PENALTY_SKILL_WEIGHT).coerceIn(PENALTY_CONVERSION_RANGE)
}

/**
 * Disputa os pênaltis entre os onze de cada lado. Sempre devolve um vencedor
 * — é a garantia que o mata-mata precisa (`TieResult` recusa empate).
 *
 * Lado sem ninguém em campo perde a cobrança (`playerId` nulo, sem consumir o
 * RNG). Se NENHUM dos dois tem quem bata, não há disputa possível e a vaga sai
 * num sorteio — caso degenerado (dois elencos vazios), mas que não pode travar
 * a copa.
 */
fun penaltyShootout(home: List<Player>, away: List<Player>, rng: Random): Shootout {
    val homeTakers = home.penaltyTakers()
    val awayTakers = away.penaltyTakers()
    if (homeTakers.isEmpty() && awayTakers.isEmpty()) {
        val homeWins = rng.nextBoolean()
        return Shootout(PenaltyScore(home = if (homeWins) 1 else 0, away = if (homeWins) 0 else 1), emptyList())
    }

    val homeKeeper = home.goalkeeper()
    val awayKeeper = away.goalkeeper()
    val kicks = mutableListOf<PenaltyKick>()

    // Contadores locais: a disputa é um laço curto e sequencial, e `var` aqui
    // lê melhor que um `fold` com estado composto. Nada escapa da função.
    var homeScore = 0
    var awayScore = 0
    var homeTaken = 0
    var awayTaken = 0

    fun kick(isHome: Boolean) {
        val takers = if (isHome) homeTakers else awayTakers
        val taken = if (isHome) homeTaken else awayTaken
        val taker = takers.takeIf { it.isNotEmpty() }?.let { it[taken % it.size] }
        val keeper = if (isHome) awayKeeper else homeKeeper
        val scored = taker != null && rng.nextDouble() < penaltyConversion(taker, keeper)

        kicks += PenaltyKick(playerId = taker?.id, home = isHome, scored = scored)
        if (isHome) {
            homeTaken++
            if (scored) homeScore++
        } else {
            awayTaken++
            if (scored) awayScore++
        }
    }

    // Decidida na série: um lado fez mais do que o outro ainda consegue alcançar.
    fun decidedInSeries(): Boolean =
        homeScore > awayScore + (PENALTY_SERIES - awayTaken) ||
            awayScore > homeScore + (PENALTY_SERIES - homeTaken)

    for (i in 1..PENALTY_SERIES) {
        kick(isHome = true)
        if (decidedInSeries()) break
        kick(isHome = false)
        if (decidedInSeries()) break
    }

    var pairs = 0
    while (homeScore == awayScore) {
        check(++pairs <= MAX_SUDDEN_DEATH_PAIRS) { "Morte súbita sem fim — conversão fora da faixa?" }
        kick(isHome = true)
        kick(isHome = false)
    }

    return Shootout(PenaltyScore(home = homeScore, away = awayScore), kicks)
}
