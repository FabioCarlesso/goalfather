package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.rules.INITIAL_BOARD_CONFIDENCE
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Objetivo que a diretoria impõe para a temporada (issue #62), do mais
 * ambicioso para o mais modesto.
 *
 * O bônus por cumprir a meta é propriedade do VALOR — mesmo idioma de
 * `TrainingFocus` e `Posture`: o `enum class` do Kotlin carrega dado por
 * constante, então "quanto vale cumprir o título" mora junto do próprio
 * título, sem um `when` espalhado pelo service. Meta mais difícil paga mais.
 */
enum class BoardObjective(val bonusCents: Long) {
    /** Elenco entre os mais fortes da divisão: só o título serve. */
    TITLE(300_000_00),

    /** Elenco da metade de cima: terminar na metade de cima da tabela. */
    TOP_HALF(200_000_00),

    /** Elenco modesto: terminar no meio da tabela, longe do fundo. */
    MID_TABLE(100_000_00),
}

/**
 * Meta da diretoria para UMA temporada do clube (issue #62): o objetivo, a
 * posição que o materializa e a confiança corrente (0..100) no técnico.
 *
 * A meta é FIXADA no início da temporada (`boardExpectations` em
 * `domain/rules/BoardRules.kt`) e só a [confidence] anda depois disso, rodada a
 * rodada — `copy()` produz a expectativa nova, como todo o domínio. A posição
 * alvo é relativa à divisão em que o clube joga a temporada, que não muda até a
 * virada; por isso a divisão não é repetida aqui.
 *
 * Os `require` guardam invariantes ESTRUTURAIS (posição ≥ 1, confiança na
 * escala), não regra de balanceamento — o limiar de demissão, por exemplo, vive
 * nas regras e pode mudar sem invalidar uma linha antiga.
 */
@Serializable
data class BoardExpectation(
    val season: Int,
    val objective: BoardObjective,
    val targetPosition: Int,
    val confidence: Int = INITIAL_BOARD_CONFIDENCE,
) {
    init {
        require(targetPosition >= 1) { "targetPosition deve ser >= 1: $targetPosition" }
        require(confidence in CONFIDENCE_RANGE) { "confidence fora de $CONFIDENCE_RANGE: $confidence" }
    }

    companion object {
        val CONFIDENCE_RANGE: IntRange = 0..100
    }
}

/**
 * Humor da diretoria, derivado da confiança (ver `mood` em `BoardRules`). Existe
 * para a UI não reimplementar os limiares: ela pinta o painel a partir do valor.
 */
enum class BoardMood { CONFIDENT, WARY, AT_RISK }

/**
 * O que a diretoria decide ao fim da temporada (issue #62). `sealed` porque cada
 * desfecho carrega dados diferentes — só a meta cumprida tem bônus — e o `when`
 * exaustivo do chamador garante que ninguém esquece de tratar a demissão.
 * Valor, não exception: ser demitido é desfecho normal do jogo (CLAUDE.md).
 *
 * `data object` nas variantes sem estado, como `Availability.Available`.
 * Serializa com o discriminador `type`, espelhando o `oneOf` do contrato.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface BoardVerdict {

    /** Meta cumprida: o clube recebe [bonusCents] em caixa. */
    @Serializable
    @SerialName("TargetMet")
    data class TargetMet(val bonusCents: Long) : BoardVerdict

    /** Meta perdida, mas a diretoria ainda confia: o técnico fica, sem bônus. */
    @Serializable
    @SerialName("TargetMissed")
    data object TargetMissed : BoardVerdict

    /** Meta perdida com a confiança no chão: o técnico é demitido. */
    @Serializable
    @SerialName("Fired")
    data object Fired : BoardVerdict
}

/**
 * Balanço da diretoria sobre UM clube numa temporada encerrada (issue #62) —
 * viaja no `RoundEvent.SeasonFinished`, de todos os clubes, como as
 * aposentadorias. O nome é copiado: o clube do técnico demitido volta ao pool
 * da IA, mas a notícia continua sendo dele.
 *
 * Não é `@Serializable` (issue #96): no fio vira `BoardReviewDto`.
 */
data class BoardReview(
    val clubId: ClubId,
    val clubName: String,
    val season: Int,
    val objective: BoardObjective,
    val targetPosition: Int,
    val finalPosition: Int,
    val confidence: Int,
    val verdict: BoardVerdict,
)

/**
 * Demissão registrada no usuário (issue #62). Na v1 single-player é o "game
 * over": o clube volta para a IA, o técnico fica sem clube e a tela de
 * demissão lê daqui o que contar. Some quando ele reivindica um clube novo.
 *
 * Achatado de propósito (sem o `BoardVerdict`): só existe para o veredito
 * `Fired`, então repetir a variante seria guardar uma informação que não varia.
 */
@Serializable
data class Dismissal(
    val clubId: ClubId,
    val clubName: String,
    val season: Int,
    val objective: BoardObjective,
    val targetPosition: Int,
    val finalPosition: Int,
)

/**
 * Leitura do painel da diretoria (issue #62): a meta da temporada corrente e
 * onde o clube está agora. `matchday` = rodadas de liga já disputadas — 0
 * antes da estreia, quando a posição ainda é só a ordem inicial da tabela.
 */
data class BoardStatus(
    val clubId: ClubId,
    val expectation: BoardExpectation,
    val currentPosition: Int,
    val divisionSize: Int,
    val matchday: Int,
)
