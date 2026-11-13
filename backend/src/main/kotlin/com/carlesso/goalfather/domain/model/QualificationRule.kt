package com.carlesso.goalfather.domain.model

/**
 * Como o resultado final de uma competição alimenta outra na virada (issue
 * #128): vaga continental, acesso, rebaixamento e playoff, descritos como
 * DADOS. As regras referenciam competições só pelo [CompetitionId] — moram fora
 * de [Competition] de propósito: a vaga é uma relação ENTRE competições (a
 * Libertadores não sabe quantas vagas o Brasileirão dá; o Brasileirão não sabe
 * que a Libertadores existe), e quem aplica é `applyQualificationRules`.
 *
 * `sealed` pelo mesmo motivo de [CompetitionFormat]: cada regra carrega dados
 * diferentes, e o `when` exaustivo em quem as aplica (movimentação, vagas,
 * faixas da tabela) faz o compilador apontar cada lugar quando uma sexta regra
 * aparecer.
 */
sealed interface QualificationRule {

    /**
     * Os próximos [n] clubes da tabela de [from] ainda SEM vaga ganham vaga em
     * [to], entrando na fase [entryStage]. "Próximos sem vaga" (e não "as
     * posições 1..n") é o que faz a cascata funcionar: o campeão da copa que
     * terminou no G-6 já tem a vaga dele, e a do G-6 desce para o 7º.
     * Várias `TopN` da mesma tabela se empilham na ordem da lista (G-4 na fase
     * de grupos, depois 5º–6º na fase prévia).
     */
    data class TopN(val from: CompetitionId, val n: Int, val to: CompetitionId, val entryStage: String) :
        QualificationRule {
        init {
            require(n >= 1) { "TopN precisa de ao menos 1 vaga: $n" }
            require(from != to) { "Vaga de ${from.value} para ela mesma" }
            require(entryStage.isNotBlank()) { "TopN ${from.value} → ${to.value} sem fase de entrada" }
        }
    }

    /**
     * O campeão de [from] (a copa, ou o líder final de uma liga) ganha vaga em
     * [to]. Vale ANTES de qualquer [TopN], seja qual for a ordem da lista: o
     * título tem prioridade, e a sobra cai na tabela.
     */
    data class Champion(val from: CompetitionId, val to: CompetitionId, val entryStage: String) : QualificationRule {
        init {
            require(from != to) { "Vaga de ${from.value} para ela mesma" }
            require(entryStage.isNotBlank()) { "Champion ${from.value} → ${to.value} sem fase de entrada" }
        }
    }

    /** Os últimos [bottomN] da tabela de [from] jogam [to] na temporada seguinte. */
    data class Relegation(val from: CompetitionId, val bottomN: Int, val to: CompetitionId) : QualificationRule {
        init {
            require(bottomN >= 1) { "Rebaixamento de ao menos 1 clube: $bottomN" }
            require(from != to) { "Rebaixamento de ${from.value} para ela mesma" }
        }
    }

    /** Os primeiros [topN] da tabela de [from] jogam [to] na temporada seguinte. */
    data class Promotion(val from: CompetitionId, val topN: Int, val to: CompetitionId) : QualificationRule {
        init {
            require(topN >= 1) { "Acesso de ao menos 1 clube: $topN" }
            require(from != to) { "Acesso de ${from.value} para ela mesma" }
        }
    }

    /**
     * Playoff de rebaixamento/acesso (Bundesliga, Ligue 1): o [upperPosition]º
     * de [upper] enfrenta o [lowerPosition]º de [lower]; quem vence joga [upper]
     * na temporada seguinte, quem perde joga [lower]. [format] reaproveita o
     * [Legs] do mata-mata: jogo único ou ida e volta.
     */
    data class Playoff(
        val upper: CompetitionId,
        val upperPosition: Int,
        val lower: CompetitionId,
        val lowerPosition: Int,
        val format: Legs = Legs.TWO_LEGGED,
    ) : QualificationRule {
        init {
            require(upper != lower) { "Playoff entre ${upper.value} e ela mesma" }
            require(upperPosition >= 1 && lowerPosition >= 1) { "Posição de playoff < 1: $upperPosition / $lowerPosition" }
        }
    }
}

/**
 * Faixa de uma posição da tabela, para a UI colorir (issue #128). Sai das
 * regras (ver `tableZones`), nunca do frontend. `enum` porque nenhuma faixa
 * carrega dado próprio; [NONE] explícito em vez de `null` deixa o `switch` do
 * TypeScript exaustivo sobre o contrato.
 *
 * As faixas são NOMINAIS: o G-6 pinta as posições 1–6 mesmo quando a cascata
 * (campeão da copa já classificado) leva o 7º — como a tabela da TV.
 */
enum class TableZone {
    NONE,

    /** Vaga em outra competição ([QualificationRule.TopN]) — a continental no MVP. */
    QUALIFICATION,
    PROMOTION,
    PLAYOFF,
    RELEGATION,
}
