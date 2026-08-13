package com.carlesso.goalfather.domain.event

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.TrainedAttribute
import com.carlesso.goalfather.domain.model.TrainingFocus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * O que aconteceu com um jogador na semana de treino (issue #58).
 *
 * `sealed interface` pelo mesmo motivo de `AgingOutcome`: o desfecho já vem
 * interpretado ("evoluiu o chute", "puxou a musculatura") em vez de obrigar o
 * cliente a comparar o elenco antes/depois para descobrir o que mudou. Cada
 * variante carrega o [player] JÁ com o efeito aplicado — é ele que a UI
 * mostra.
 *
 * Serializa como `{"type": "Improved", ...}`, espelhando o `oneOf` +
 * `discriminator` do `contract/openapi.yaml` — mesma convenção de
 * `MatchEvent`, `RoundEvent` e `Availability`.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface TrainingEvent {
    val player: Player

    /** Ganhou um ponto no atributo do foco (e no `overall` que o resume). */
    @Serializable
    @SerialName("Improved")
    data class Improved(
        override val player: Player,
        val attribute: TrainedAttribute,
    ) : TrainingEvent

    /** Saiu machucado do treino — afastamento curto. */
    @Serializable
    @SerialName("Injured")
    data class Injured(
        override val player: Player,
        val roundsOut: Int,
    ) : TrainingEvent
}

/**
 * Extrato do treino de UM clube na rodada (issue #58): qual foi o foco e o
 * que ele produziu. Viaja no `RoundEvent.RoundFinished` para todos os clubes
 * — igual às finanças, cabe ao cliente filtrar o que interessa ao técnico.
 *
 * O relatório existe mesmo sem eventos: "treinei FISICO e ninguém evoluiu"
 * é informação, e sem ele a UI não teria como distinguir isso de "não houve
 * treino".
 */
@Serializable
data class TrainingReport(
    val clubId: ClubId,
    val focus: TrainingFocus,
    val events: List<TrainingEvent> = emptyList(),
)
