package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

/**
 * Postura do time na partida (issue #56) — a decisão tática que o técnico
 * toma A CADA rodada, ao lado de quem entra em campo.
 *
 * Os modificadores são **propriedades do próprio valor do enum**, não um
 * `when` espalhado pela engine. Idioma Kotlin: enum é uma classe, então o
 * comportamento mora junto do valor e adicionar uma postura nova não obriga
 * a caçar `when`s pelo código.
 *
 * Semântica dos dois modificadores, do ponto de vista de UM time:
 * - [attackMod] escala a chance de gol DO PRÓPRIO time;
 * - [defenseMod] escala a chance de gol DO ADVERSÁRIO (< 1 = defende melhor).
 *
 * Por isso `OFFENSIVE` tem os DOIS acima de 1: jogo aberto faz gol e toma gol.
 * `DEFENSIVE` tem os dois abaixo de 1 — cria menos, mas concede menos ainda
 * (é esse desequilíbrio, e não o valor absoluto, que dá o bônus defensivo e
 * torna "fechar o time" uma escolha racional contra adversário mais forte).
 */
enum class Posture(val attackMod: Double, val defenseMod: Double) {
    DEFENSIVE(attackMod = 0.82, defenseMod = 0.88),
    BALANCED(attackMod = 1.00, defenseMod = 1.00),
    OFFENSIVE(attackMod = 1.20, defenseMod = 1.12),
}

/**
 * Instruções táticas de um time para a partida.
 *
 * Hoje só a postura, mas é o ponto de extensão natural para pressão, ritmo
 * e marcação — daí o wrapper em vez de um `Posture` solto espalhado pelas
 * assinaturas. `DEFAULT` é o que vale para quem nunca escalou e para os
 * clubes da IA.
 */
@Serializable
data class Tactics(val posture: Posture = Posture.BALANCED) {
    companion object {
        val DEFAULT: Tactics = Tactics()
    }
}
