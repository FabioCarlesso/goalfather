package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.model.QualificationRule.Champion
import com.carlesso.goalfather.domain.model.QualificationRule.Promotion
import com.carlesso.goalfather.domain.model.QualificationRule.Relegation
import com.carlesso.goalfather.domain.model.QualificationRule.TopN

/**
 * As regras de vaga do mundo do MVP (issue #128), com os ids do pack do mundo.
 *
 * **Interino**, como [SeasonCompetitions]: quando o mundo do MVP sair do pack
 * (issue #131), estas regras passam a ser dados do pack lidos pelo
 * `WorldDataProvider`, e este objeto deixa de existir. Ficam aqui para que a
 * regra seja testada com as competições reais antes de o mundo estar no ar.
 *
 * **Corte documentado — ligas europeias fechadas:** o pack do MVP não traz as
 * segundas divisões europeias (issue #146), então Premier League, La Liga,
 * Serie A, Bundesliga e Ligue 1 não rebaixam nem disputam playoff: [EUROPE] é
 * vazio. Champions e Liga Europa também ficam para a #145, então nenhuma liga
 * europeia dá vaga continental ainda. A regra da Bundesliga (16º × 3º da
 * 2. Bundesliga, ida e volta) é exatamente um [QualificationRule.Playoff] e
 * entra junto da 2. Bundesliga.
 */
object WorldQualificationRules {
    val BRA_SERIE_A = CompetitionId("bra-serie-a")
    val BRA_SERIE_B = CompetitionId("bra-serie-b")
    val BRA_COPA_DO_BRASIL = CompetitionId("bra-copa-do-brasil")
    val CONMEBOL_LIBERTADORES = CompetitionId("conmebol-libertadores")

    const val LIBERTADORES_GROUP_STAGE = "Fase de grupos"
    const val LIBERTADORES_SECOND_STAGE = "Segunda fase"

    /**
     * Brasil, simplificado do regulamento CBF/CONMEBOL 2026: o campeão da Copa
     * do Brasil e o G-4 vão à fase de grupos da Libertadores; 5º e 6º, à segunda
     * fase; Série A e Série B trocam 4 clubes. Vagas de campeões continentais e
     * a Sul-Americana entram com a #132/#145.
     */
    val BRAZIL: List<QualificationRule> = listOf(
        Champion(from = BRA_COPA_DO_BRASIL, to = CONMEBOL_LIBERTADORES, entryStage = LIBERTADORES_GROUP_STAGE),
        TopN(from = BRA_SERIE_A, n = 4, to = CONMEBOL_LIBERTADORES, entryStage = LIBERTADORES_GROUP_STAGE),
        TopN(from = BRA_SERIE_A, n = 2, to = CONMEBOL_LIBERTADORES, entryStage = LIBERTADORES_SECOND_STAGE),
        Relegation(from = BRA_SERIE_A, bottomN = 4, to = BRA_SERIE_B),
        Promotion(from = BRA_SERIE_B, topN = 4, to = BRA_SERIE_A),
    )

    /** Ligas europeias do MVP: fechadas até a issue #146 (ver o corte acima). */
    val EUROPE: List<QualificationRule> = emptyList()

    val ALL: List<QualificationRule> = BRAZIL + EUROPE
}
