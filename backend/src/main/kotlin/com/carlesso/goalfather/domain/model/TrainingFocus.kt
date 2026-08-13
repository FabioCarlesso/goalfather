package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

/**
 * Atributo que um foco de treino desenvolve (issue #58).
 *
 * Enum próprio em vez de `KProperty1<Player, Int>` ou de uma `String`: o
 * `when` que aplica o ganho (em `domain/rules/TrainingRules.kt`) fica
 * exaustivo sem `else`, então adicionar um atributo treinável vira erro de
 * compilação no ponto que precisa mudar — não um ramo silenciosamente não
 * tratado.
 */
enum class TrainedAttribute { SHOOTING, DEFENDING, PACE }

/**
 * Foco de treino escolhido pelo técnico para a semana (issue #58).
 *
 * Os efeitos são **propriedades do próprio valor do enum**, não um `when`
 * espalhado pela regra — mesmo idioma de [Posture] e [AgeBand]: enum é uma
 * classe, o comportamento mora junto do valor, e um foco novo (bola parada,
 * entrosamento) entra sem caçar `when`s pelo código.
 *
 * O trade-off que dá graça à escolha:
 * - [ATAQUE]/[DEFESA] desenvolvem um atributo, mas não devolvem forma física
 *   e cobram risco de lesão leve;
 * - [FISICO] fica no meio — recupera parte da stamina e ainda desenvolve
 *   velocidade, mas é o treino que mais machuca;
 * - [DESCANSO] não desenvolve ninguém e é o único sem risco: é a semana de
 *   poupar o elenco.
 *
 * **Calibragem:** nenhuma recuperação alcança o desgaste MÍNIMO de um titular
 * (`STARTER_STAMINA_LOSS.first`, 10). É de propósito — se uma semana de
 * descanso zerasse o cansaço de quem jogou, a fadiga da issue #54 deixaria de
 * existir e com ela o sentido de rodar o elenco e de pagar o departamento
 * médico. O treino alivia a conta, não a apaga (`TrainingRulesTest` guarda o
 * invariante).
 *
 * Os nomes seguem a issue (português), diferente de [Posture]/[Position], que
 * são anteriores — o contrato expõe os mesmos valores, com rótulo de exibição
 * em `x-labels`.
 */
@Serializable
enum class TrainingFocus(
    val trains: TrainedAttribute?,
    val staminaRecovery: Int,
    val injuryRisk: Double,
) {
    ATAQUE(TrainedAttribute.SHOOTING, staminaRecovery = 0, injuryRisk = 0.04),
    DEFESA(TrainedAttribute.DEFENDING, staminaRecovery = 0, injuryRisk = 0.04),
    FISICO(TrainedAttribute.PACE, staminaRecovery = 5, injuryRisk = 0.06),
    DESCANSO(trains = null, staminaRecovery = 8, injuryRisk = 0.0),
    ;

    companion object {
        /** Foco de quem ainda não escolheu — e dos clubes da IA. */
        val DEFAULT: TrainingFocus = DESCANSO
    }
}
