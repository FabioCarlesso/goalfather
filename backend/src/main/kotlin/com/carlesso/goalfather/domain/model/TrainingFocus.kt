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
 * - [ATAQUE]/[DEFESA] desenvolvem um atributo e quase não devolvem forma
 *   física, além de cobrarem risco de lesão leve;
 * - [FISICO] fica no meio — recupera bem a stamina e ainda desenvolve
 *   velocidade, mas é o treino que mais machuca;
 * - [DESCANSO] não desenvolve ninguém e é o único sem risco: é a semana de
 *   poupar o elenco.
 *
 * **Calibragem (as duas pontas importam):**
 *
 * 1. Nenhuma recuperação alcança o desgaste MÍNIMO de um titular
 *    (`STARTER_STAMINA_LOSS.first`, 10). Se uma semana de descanso zerasse o
 *    cansaço de quem jogou, a fadiga da issue #54 deixaria de existir e com
 *    ela o sentido de rodar o elenco e de pagar o departamento médico.
 * 2. Nenhuma recuperação é ZERO. Com 0, o titular ficava preso EXATAMENTE no
 *    piso de partida (`STAMINA_MATCH_FLOOR`, 40) rodada após rodada, enquanto
 *    todo clube da IA — sempre em [DESCANSO] — se estabilizava em 48: uma
 *    equipe humana permanentemente ~17% mais fraca em `effectiveOverall` pelo
 *    resto da temporada, em troca de uns poucos +1. Escolher um foco técnico
 *    tem que custar caro, não ser autossabotagem.
 *
 * `TrainingRulesTest` guarda as duas pontas — o intervalo, não os números.
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
    ATAQUE(TrainedAttribute.SHOOTING, staminaRecovery = 3, injuryRisk = 0.04),
    DEFESA(TrainedAttribute.DEFENDING, staminaRecovery = 3, injuryRisk = 0.04),
    FISICO(TrainedAttribute.PACE, staminaRecovery = 6, injuryRisk = 0.06),
    DESCANSO(trains = null, staminaRecovery = 8, injuryRisk = 0.0),
    ;

    init {
        // `TrainingRules.train` só sorteia lesão para foco que treina atributo
        // (é o treino pesado que machuca). Um foco sem atributo declarando
        // risco teria esse risco silenciosamente ignorado — melhor não deixar
        // a configuração existir do que descobrir pelo bug.
        require(trains != null || injuryRisk == 0.0) {
            "$name não treina atributo, então não pode declarar injuryRisk=$injuryRisk"
        }
    }

    companion object {
        /** Foco de quem ainda não escolheu — e dos clubes da IA. */
        val DEFAULT: TrainingFocus = DESCANSO
    }
}
