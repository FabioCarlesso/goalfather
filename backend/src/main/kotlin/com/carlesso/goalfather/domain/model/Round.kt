package com.carlesso.goalfather.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
enum class RoundStatus { Scheduled, InProgress, Finished }

@Serializable
data class RoundMatch(
    val matchId: Long,
    val homeClubId: ClubId,
    val awayClubId: ClubId,
    val homeClubName: String,
    val awayClubName: String,
    val status: RoundStatus = RoundStatus.Scheduled,
    val homeGoals: Int = 0,
    val awayGoals: Int = 0,
    val minute: Int = 0,
    // Divisão da partida (issue #47). Default = elite: rodadas persistidas
    // antes das divisões desserializam sem migração de dados no matches_json.
    val division: Division = Division.FIRST,
    // Disputa de pênaltis (issue #61) — só existe em jogo de copa que
    // terminou empatado. Nulo no resto, inclusive em rodadas antigas.
    val penalties: PenaltyScore? = null,
)

/**
 * O que uma data do calendário disputa (issue #61): rodada de LIGA ou fase de
 * COPA. As duas passam pelo MESMO fluxo de prontidão, claim e stream de
 * rodada (issues #20/#46) — o que muda é só o que se faz com o resultado.
 *
 * `sealed` porque cada variante carrega um dado diferente e os pontos de
 * consumo PRECISAM tratar as duas: aplicar à tabela, cobrar folha, decidir
 * empate nos pênaltis. Um `when` exaustivo sem `else` faz o compilador
 * apontar cada um desses lugares — com um `isCup: Boolean` qualquer `if`
 * esquecido passaria em silêncio.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface RoundStage {

    /**
     * Rodada de pontos corridos. `matchday` é a rodada DA LIGA (1..N-1 do
     * Berger) — com a copa no calendário ela deixa de coincidir com o
     * `Round.number`, que passa a ser a data global da temporada.
     */
    @Serializable
    @SerialName("League")
    data class League(val matchday: Int) : RoundStage {
        init {
            require(matchday >= 1) { "Rodada de liga deve ser >= 1: $matchday" }
        }
    }

    /** Fase da copa disputada nesta data — jogo único, empate vai aos pênaltis. */
    @Serializable
    @SerialName("Cup")
    data class Cup(val phase: KnockoutPhase) : RoundStage
}

/**
 * Uma data do calendário. `number` é a posição GLOBAL na temporada (PK da
 * tabela `rounds`, rota do WS e chave da prontidão) e `stage` diz o que se
 * joga nela. Sem copa, toda data é rodada de liga e `matchday == number` —
 * por isso o default, que também é o que uma linha gravada antes da issue #61
 * (sem `stage_json`) assume.
 */
@Serializable
data class Round(
    val number: Int,
    val season: Int,
    val status: RoundStatus = RoundStatus.Scheduled,
    val matches: List<RoundMatch>,
    val stage: RoundStage = RoundStage.League(number),
) {
    init {
        require(number >= 1) { "Rodada deve ser >= 1: $number" }
    }

    val isFinished: Boolean
        get() = matches.all { it.status == RoundStatus.Finished }

    /** Jogo de mata-mata? Empate nele não existe: decide nos pênaltis. */
    val isKnockout: Boolean
        get() = when (stage) {
            is RoundStage.League -> false
            is RoundStage.Cup -> true
        }
}
