package com.carlesso.goalfather.domain.event

import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Posture
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Eventos de uma partida — `sealed interface` Kotlin.
 *
 * Espelha o schema `MatchEvent` no `contract/openapi.yaml`
 * (`oneOf` + `discriminator: type`). `@JsonClassDiscriminator("type")`
 * faz o kotlinx.serialization emitir/aceitar exatamente esse formato:
 * `{"type": "Goal", "minute": 23, ...}` — bate com o que o frontend já consome.
 *
 * Consumidores usam `when (event) is ...` EXAUSTIVO (sem `else`).
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface MatchEvent {
    val minute: Int

    /**
     * Apito inicial. Além de nome e força, carrega a postura com que cada
     * time entrou em campo (issue #56) — é o que permite ao técnico ver, no
     * placar, que o adversário se fechou (ou se abriu) contra ele.
     */
    @Serializable
    @SerialName("KickOff")
    data class KickOff(
        override val minute: Int = 0,
        val homeClubName: String,
        val awayClubName: String,
        val homeStrength: Double,
        val awayStrength: Double,
        val homePosture: Posture = Posture.BALANCED,
        val awayPosture: Posture = Posture.BALANCED,
    ) : MatchEvent

    @Serializable
    @SerialName("Goal")
    data class Goal(
        override val minute: Int,
        val scorerId: PlayerId,
        val home: Boolean,
    ) : MatchEvent

    @Serializable
    @SerialName("Card")
    data class Card(
        override val minute: Int,
        val playerId: PlayerId,
        val red: Boolean,
    ) : MatchEvent

    /**
     * Lesão em campo. `roundsOut` é o afastamento sorteado com o RNG da
     * PRÓPRIA partida (issue #54): a duração viaja no evento, então quem
     * aplica os efeitos da rodada não precisa re-sortear nada — e o replay
     * de um stream reproduz exatamente a mesma lesão.
     */
    @Serializable
    @SerialName("Injury")
    data class Injury(
        override val minute: Int,
        val playerId: PlayerId,
        val roundsOut: Int,
    ) : MatchEvent

    @Serializable
    @SerialName("Save")
    data class Save(override val minute: Int) : MatchEvent

    @Serializable
    @SerialName("FullTime")
    data class FullTime(
        override val minute: Int = 90,
        val homeGoals: Int,
        val awayGoals: Int,
    ) : MatchEvent
}
