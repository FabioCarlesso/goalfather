package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

/**
 * Posição em campo. `abbr` é a sigla em português exibida na UI.
 *
 * `scoringWeight` é o peso relativo da posição no sorteio do autor de uma
 * finalização (issue #57). Números — e não uma lista "quem pode chutar" —
 * porque a diferença entre atacante e zagueiro é de FREQUÊNCIA, não de
 * permissão: zagueiro marca de bola parada, goleiro praticamente nunca.
 * Como peso, a regra fica em um lugar só e o teste consegue verificar a
 * distribuição (ver `drawShooter` em `domain/rules/ScoringRules.kt`).
 *
 * Peso 0.0 no goleiro é literal: ele não é sorteado.
 */
@Serializable
enum class Position(val abbr: String, val scoringWeight: Double) {
    GK("GL", 0.0),
    CB("ZG", 1.0),
    MF("MC", 3.0),
    FW("AT", 6.0),
}
