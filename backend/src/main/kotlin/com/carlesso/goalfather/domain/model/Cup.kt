package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.serialization.CompetitionIdSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

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
 * Resultado de um confronto em JOGO ÚNICO.
 *
 * O `init` torna irrepresentável o que o mata-mata não admite: um empate SEM
 * pênaltis (não haveria classificado) e pênaltis depois de um jogo que já
 * tinha vencedor. Diferente da faixa de preço do ingresso (regra de
 * balanceamento, issue #59), aqui é invariante estrutural — um `TieResult`
 * sem vencedor não tem significado, então o `require` é o lugar certo. O par
 * de ida e volta é o `TwoLeggedTieResult` (issue #124), com a mesma lição.
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
 * Qual jogo de um confronto uma partida de copa é (issue #131) — e, com isso,
 * quando o empate dela vai aos pênaltis (`decidesOnPenalties`, `CupRules.kt`):
 *
 * - [Single]: jogo único. Empate no placar → pênaltis.
 * - [First]: a ida. Empate é só um placar; ninguém cobra pênalti.
 * - [Second]: a volta. Pênaltis só se o AGREGADO empatar — por isso ela
 *   carrega o placar da ida ([firstLeg], na orientação da ida).
 *
 * `sealed` e não um `enum` + `firstLeg: LegScore?` solto: "volta sem placar
 * da ida" seria um estado possível, e a decisão dos pênaltis precisaria de um
 * `!!` para lê-lo. Aqui o placar só existe onde ele tem significado. As duas
 * pernas sem dado são `data object` — o singleton do Kotlin com `toString`/
 * `equals` legíveis, serializável como qualquer outra variante.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface CupLeg {
    @Serializable
    @SerialName("Single")
    data object Single : CupLeg

    @Serializable
    @SerialName("First")
    data object First : CupLeg

    @Serializable
    @SerialName("Second")
    data class Second(val firstLeg: LegScore) : CupLeg
}

/**
 * Um confronto da chave — `sealed` porque há DOIS formatos de confronto, e
 * cada um carrega só o que faz sentido para ele:
 *
 * - [Single]: jogo único, um [TieResult] (com pênaltis se empatou).
 * - [TwoLegged]: ida e volta, o placar de cada perna e os pênaltis da volta.
 *
 * Com um data class só e campos opcionais para tudo, "jogo único com placar
 * de volta" seria representável. É a mesma lição de `Availability` (#54).
 *
 * [home] é o mandante do jogo único ou da IDA; na volta o mando se inverte.
 * As folgas da copa antiga (issue #61) deixaram de existir: quem pula fases
 * agora ENTRA numa fase mais tardia (`CupPhase.entrants`), como a Série A
 * na 5ª fase da Copa do Brasil.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface CupTie {
    val index: Int
    val home: CupClub
    val away: CupClub

    /** Quem passou de fase, ou `null` se o confronto ainda não foi decidido. */
    val winner: CupClub?

    /** O jogo que falta disputar; `null` quando o confronto está decidido. */
    val openLeg: CupLeg?

    /** Jogo único ou ida e volta — tem que bater com o da fase. */
    val legs: Legs

    @Serializable
    @SerialName("Single")
    data class Single(
        override val index: Int,
        override val home: CupClub,
        override val away: CupClub,
        val result: TieResult? = null,
    ) : CupTie {
        init {
            require(home.id != away.id) { "Clube não enfrenta a si mesmo: ${home.id.value}" }
        }

        // Propriedades sem backing field: o kotlinx.serialization não as
        // serializa — são derivadas, não estado.
        override val winner: CupClub?
            get() = result?.let { if (it.homeWon) home else away }

        override val openLeg: CupLeg? get() = if (result == null) CupLeg.Single else null

        override val legs: Legs get() = Legs.SINGLE
    }

    /**
     * Ida e volta. [firstLeg] na orientação da ida ([home] recebe); [secondLeg]
     * e [penalties] na orientação da volta ([away] recebe). O `init` delega ao
     * `TwoLeggedTieResult` a invariante do confronto decidido (agregado
     * empatado exige pênaltis; decidido no placar não admite) e recusa uma
     * volta sem ida.
     */
    @Serializable
    @SerialName("TwoLegged")
    data class TwoLegged(
        override val index: Int,
        override val home: CupClub,
        override val away: CupClub,
        val firstLeg: LegScore? = null,
        val secondLeg: LegScore? = null,
        val penalties: PenaltyScore? = null,
    ) : CupTie {
        init {
            require(home.id != away.id) { "Clube não enfrenta a si mesmo: ${home.id.value}" }
            require(secondLeg == null || firstLeg != null) { "Volta registrada sem a ida: ${home.id.value} × ${away.id.value}" }
            require(secondLeg != null || penalties == null) { "Pênaltis só depois da volta: ${home.id.value} × ${away.id.value}" }
            result // constrói o resultado só para validar a invariante dele
        }

        /** O confronto decidido, ou `null` enquanto falta a volta. */
        val result: TwoLeggedTieResult?
            get() = secondLeg?.let { second ->
                TwoLeggedTieResult(home.id, away.id, requireNotNull(firstLeg), second, penalties)
            }

        /** Gols de [home] nos jogos já disputados (0 antes da ida). */
        val homeAggregate: Int get() = (firstLeg?.homeGoals ?: 0) + (secondLeg?.awayGoals ?: 0)

        /** Gols de [away] nos jogos já disputados. */
        val awayAggregate: Int get() = (firstLeg?.awayGoals ?: 0) + (secondLeg?.homeGoals ?: 0)

        override val winner: CupClub?
            get() = result?.let { if (it.winner == home.id) home else away }

        override val openLeg: CupLeg?
            get() = when {
                firstLeg == null -> CupLeg.First
                secondLeg == null -> CupLeg.Second(firstLeg)
                else -> null
            }

        override val legs: Legs get() = Legs.TWO_LEGGED
    }
}

/**
 * Uma fase do PLANO da copa (issue #131): o `KnockoutStage` do formato com os
 * clubes que ENTRAM nela já definidos no sorteio inicial ([entrants]) — os
 * mais fortes nas fases mais tardias. As fases seguintes à primeira só são
 * SORTEADAS quando a anterior termina, mas quem entra em cada uma já se sabe.
 */
@Serializable
data class CupPhase(
    val name: String,
    val teams: Int,
    val legs: Legs,
    val entrants: List<CupClub>,
) {
    init {
        require(name.isNotBlank()) { "Fase de copa sem nome" }
        require(teams >= 2 && teams % 2 == 0) { "Fase $name precisa de nº par de clubes >= 2: $teams" }
        require(entrants.size <= teams) { "Fase $name: ${entrants.size} entrantes para $teams vagas" }
    }

    val ties: Int get() = teams / 2
}

/** Uma fase da copa já sorteada, com seus confrontos na ordem do sorteio. */
@Serializable
data class CupStage(
    val name: String,
    val ties: List<CupTie>,
) {
    /** Todos os confrontos decididos. */
    val isComplete: Boolean get() = ties.all { it.winner != null }
}

/**
 * A copa de uma temporada (issues #61 e #131): mata-mata em fases nomeadas,
 * cada uma em jogo único ou ida e volta, com clubes ENTRANDO em fases
 * diferentes — o formato da Copa do Brasil.
 *
 * - [phases] é o plano inteiro (o formato `Knockout` da competição + quem
 *   entra em cada fase), fixado no sorteio inicial.
 * - [stages] guarda só as fases JÁ SORTEADAS, alinhadas por índice com
 *   [phases] — a fase N+1 é sorteada quando a N termina, com os vencedores
 *   + os entrantes dela.
 * - [drawSeed] é a semente desses sorteios futuros. Sai do `Random` do
 *   sorteio inicial, então a copa inteira é função da seed da temporada —
 *   sem guardar o `Random` (que não é serializável) nem depender de quando
 *   cada fase termina.
 *
 * Diferente do `SeasonRecord`, a `Cup` muda ao longo da temporada (cada
 * perna disputada produz uma cópia com os resultados) — mas sempre via
 * `copy()`, como o resto do domínio.
 */
@Serializable
data class Cup(
    val season: Int,
    @Serializable(with = CompetitionIdSerializer::class)
    val competitionId: CompetitionId,
    val competitionName: String,
    val phases: List<CupPhase>,
    val stages: List<CupStage>,
    val drawSeed: Long,
) {
    init {
        require(competitionName.isNotBlank()) { "Copa sem nome: ${competitionId.value}" }
        require(phases.isNotEmpty()) { "Copa sem fases" }
        require(stages.size in 1..phases.size) { "Copa com ${stages.size} fases sorteadas de ${phases.size}" }
        format // valida a aritmética das fases (vencedores + entrantes = vagas)
        // `zip` dá os pares (fase sorteada, fase do plano) sem índice manual.
        stages.zip(phases).forEach { (stage, phase) ->
            require(stage.name == phase.name) { "Fase sorteada ${stage.name} fora do plano (${phase.name})" }
            require(stage.ties.size == phase.ties) { "Fase ${phase.name} tem ${phase.ties} confrontos: ${stage.ties.size}" }
            require(stage.ties.all { it.legs == phase.legs }) { "Fase ${phase.name} é ${phase.legs}" }
        }
        require(stages.dropLast(1).all { it.isComplete }) { "Só a última fase sorteada pode estar em aberto" }
    }

    /** O plano no formato genérico da issue #124 — o que o calendário enxerga. */
    val format: CompetitionFormat.Knockout
        get() = CompetitionFormat.Knockout(phases.map { KnockoutStage(it.name, it.teams, it.legs, it.entrants.size) })

    /**
     * Quantos encaixes do calendário a copa ocupa: UMA perna por encaixe —
     * jogo único conta 1, ida e volta conta 2 —, somando as fases. O formato
     * da Copa do Brasil do MVP dá 1 + 2 + 2 + 2 + 2 + 1 = 10.
     */
    val calendarRounds: Int get() = phases.sumOf { it.legs.matches }

    /** Fase em disputa — a última sorteada, enquanto não estiver completa. */
    val currentStage: CupStage?
        get() = stages.last().takeUnless { it.isComplete }

    /** O plano da fase em disputa; `null` quando a copa acabou. */
    val currentPhase: CupPhase?
        get() = currentStage?.let { phases[stages.lastIndex] }

    /** Vencedor da final; `null` enquanto a copa não acabou. */
    val champion: CupClub?
        get() = stages.takeIf { it.size == phases.size }?.last()?.ties?.singleOrNull()?.winner
}
