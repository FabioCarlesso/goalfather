package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.Position
import kotlin.random.Random

/**
 * Quem finaliza e quem defende (issue #57). Funções PURAS com o RNG
 * injetado por parâmetro — mesma seed, mesmo autor.
 *
 * Antes desta regra o autor do gol saía de um sorteio uniforme sobre o
 * elenco inteiro, o que fazia o goleiro marcar tanto quanto o centroavante.
 * O protótipo (`prototype/goalfather-web.jsx`, a especificação executável do
 * CLAUDE.md) sorteia só entre FW/MF; aqui a regra é generalizada em pesos
 * por posição, o que preserva a ideia e ainda deixa o zagueiro marcar de
 * vez em quando.
 */

/**
 * Sorteia o autor de uma finalização ponderando por [Position.scoringWeight].
 *
 * Roleta clássica: soma os pesos, sorteia um ponto do intervalo e caminha
 * pela lista descontando até cruzá-lo. Consome exatamente UM `nextDouble()`,
 * o que mantém o stream previsível para uma seed fixa.
 *
 * Devolve `null` quando ninguém pode finalizar (lista vazia ou só goleiros)
 * — ausência modelada no tipo em vez de exception, como manda o CLAUDE.md.
 */
fun List<Player>.drawShooter(rng: Random): Player? {
    val total = sumOf { it.position.scoringWeight }
    if (total <= 0.0) return null

    var ticket = rng.nextDouble() * total
    for (player in this) {
        ticket -= player.position.scoringWeight
        if (ticket < 0.0) return player
    }
    // Só alcançável por arredondamento de ponto flutuante no último passo.
    return last { it.position.scoringWeight > 0.0 }
}

/** Goleiro escalado — o primeiro na posição, `null` em escalação sem GK. */
fun List<Player>.goalkeeper(): Player? = firstOrNull { it.position == Position.GK }
