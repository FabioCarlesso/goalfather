package com.carlesso.goalfather.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Fase de um mata-mata (issue #61), da mais larga para a final.
 *
 * Como `Posture` e `TrainingFocus`, o comportamento mora no próprio valor:
 * `teams` diz quantos clubes a fase comporta e `prizeCents` quanto ganha
 * quem VENCE um confronto dela — a premiação "por fase avançada" da issue.
 * Uma fase nova (32-avos) entra aqui e o resto do código a enxerga sem caça
 * a `when` espalhado.
 *
 * A escada de prêmios é crescente de propósito: chegar longe paga mais, e o
 * campeão (vencedor da final) leva o maior cheque. O caixa da IA é R$ 500 mil
 * e uma bilheteria cheia rende ~R$ 750 mil; a copa inteira (R$ 1,05 mi para
 * quem joga as 4 fases e vence) pesa como receita sem virar a única que importa.
 */
@Serializable
enum class KnockoutPhase(val teams: Int, val prizeCents: Long) {
    ROUND_OF_32(32, 75_000_00),
    ROUND_OF_16(16, 100_000_00),
    QUARTER_FINALS(8, 150_000_00),
    SEMI_FINALS(4, 250_000_00),
    FINAL(2, 500_000_00),
    ;

    /** Fase seguinte do chaveamento; `null` depois da final. */
    val next: KnockoutPhase? get() = entries.getOrNull(ordinal + 1)

    companion object {
        /** Fase de abertura de uma chave com [bracketSize] vagas (potência de 2). */
        fun opening(bracketSize: Int): KnockoutPhase =
            requireNotNull(entries.firstOrNull { it.teams == bracketSize }) {
                "Chave de $bracketSize vagas não suportada (use 2..32, potência de 2)"
            }
    }
}

/**
 * Clube como a copa o enxerga: id + nome. O nome é COPIADO (não buscado pelo
 * id na leitura) pelo mesmo motivo do `SeasonTopScorer` da issue #60 — a
 * copa vira história e não deve depender do estado atual do clube.
 */
@Serializable
data class CupClub(val id: ClubId, val name: String)

/** Placar de uma disputa de pênaltis, na orientação mandante × visitante. */
@Serializable
data class PenaltyScore(val home: Int, val away: Int) {
    init {
        require(home >= 0 && away >= 0) { "Placar de pênaltis negativo: $home × $away" }
    }
}

/**
 * Resultado de um confronto de mata-mata.
 *
 * O `init` torna irrepresentável o que o mata-mata não admite: um empate SEM
 * pênaltis (não haveria classificado) e pênaltis depois de um jogo que já
 * tinha vencedor. Diferente da faixa de preço do ingresso (regra de
 * balanceamento, issue #59), aqui é invariante estrutural — um `TieResult`
 * sem vencedor não tem significado, então o `require` é o lugar certo.
 */
@Serializable
data class TieResult(
    val homeGoals: Int,
    val awayGoals: Int,
    val penalties: PenaltyScore? = null,
) {
    init {
        require(homeGoals >= 0 && awayGoals >= 0) { "Placar negativo: $homeGoals × $awayGoals" }
        if (homeGoals == awayGoals) {
            requireNotNull(penalties) { "Empate em mata-mata exige pênaltis: $homeGoals × $awayGoals" }
            require(penalties.home != penalties.away) { "Pênaltis não podem terminar empatados" }
        } else {
            require(penalties == null) { "Pênaltis só após empate: $homeGoals × $awayGoals" }
        }
    }

    /** O mandante passou? Pênaltis decidem quando houver, senão o placar. */
    val homeWon: Boolean
        get() = penalties?.let { it.home > it.away } ?: (homeGoals > awayGoals)
}

/**
 * Um confronto da chave — `sealed` porque há DOIS tipos de vaga, e cada um
 * carrega só o que faz sentido para ele:
 *
 * - [Bye]: o clube passa sem jogar. Existe quando o nº de clubes não é
 *   potência de 2 (12 clubes numa chave de 16 deixam 4 folgas). Não tem
 *   adversário nem placar — e o tipo nem permite escrevê-los.
 * - [Fixture]: jogo único. `result` nulo = ainda não disputado.
 *
 * Com um único data class e `away: CupClub?` + `result: TieResult?`, "folga
 * com placar" seria um estado possível. A soma de tipos é a mesma lição de
 * `Availability` (issue #54): estados inválidos irrepresentáveis.
 *
 * `index` é a posição na chave da fase. Os vencedores de `2k` e `2k+1` se
 * enfrentam na fase seguinte — é ela que faz o sorteio inicial definir o
 * caminho inteiro até a final.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface CupTie {
    val index: Int

    /** Quem passou de fase, ou `null` se o confronto ainda não foi decidido. */
    val winner: CupClub?

    @Serializable
    @SerialName("Bye")
    data class Bye(
        override val index: Int,
        val club: CupClub,
    ) : CupTie {
        // Propriedade sem backing field: o kotlinx.serialization não a
        // serializa — é derivada, não estado.
        override val winner: CupClub get() = club
    }

    @Serializable
    @SerialName("Fixture")
    data class Fixture(
        override val index: Int,
        val home: CupClub,
        val away: CupClub,
        val result: TieResult? = null,
    ) : CupTie {
        init {
            require(home.id != away.id) { "Clube não enfrenta a si mesmo: ${home.id.value}" }
        }

        override val winner: CupClub?
            get() = result?.let { if (it.homeWon) home else away }
    }
}

/** Uma fase da copa já sorteada, com seus confrontos na ordem da chave. */
@Serializable
data class CupStage(
    val phase: KnockoutPhase,
    val ties: List<CupTie>,
) {
    init {
        require(ties.size == phase.teams / 2) {
            "Fase $phase tem ${phase.teams / 2} confrontos, recebeu ${ties.size}"
        }
    }

    /** Todos os confrontos decididos (folgas já nascem decididas). */
    val isComplete: Boolean get() = ties.all { it.winner != null }
}

/**
 * A copa de uma temporada (issue #61): mata-mata em jogo único com os mesmos
 * clubes da liga, paralelo ao campeonato.
 *
 * `stages` guarda só as fases JÁ SORTEADAS, em ordem — a fase N+1 nasce
 * quando a N termina (avanço dos vencedores). A sequência completa sai da
 * chave da primeira fase em [phases], o que é tudo que o calendário precisa
 * para reservar as datas da copa logo no início da temporada.
 *
 * Diferente do `SeasonRecord`, a `Cup` muda ao longo da temporada (cada
 * fase disputada produz uma cópia com os resultados) — mas sempre via
 * `copy()`, como o resto do domínio.
 */
@Serializable
data class Cup(
    val season: Int,
    val stages: List<CupStage>,
) {
    init {
        require(stages.isNotEmpty()) { "Copa sem fase sorteada" }
    }

    /** Fases que a copa terá, da abertura à final. */
    val phases: List<KnockoutPhase>
        get() = KnockoutPhase.entries.filter { it.teams <= stages.first().phase.teams }

    /** Fase em disputa — a última sorteada, enquanto não estiver completa. */
    val currentStage: CupStage?
        get() = stages.last().takeUnless { it.isComplete }

    /** Vencedor da final; `null` enquanto a copa não acabou. */
    val champion: CupClub?
        get() = stages.last().takeIf { it.phase == KnockoutPhase.FINAL }?.ties?.single()?.winner
}
