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

    /**
     * Cartão. `home` diz de que lado saiu — sem isso as estatísticas de
     * disciplina do [FullTime] não teriam como separar os times, já que o
     * `playerId` sozinho não revela a que elenco pertence (issue #57).
     */
    @Serializable
    @SerialName("Card")
    data class Card(
        override val minute: Int,
        val playerId: PlayerId,
        val red: Boolean,
        val home: Boolean,
    ) : MatchEvent

    /**
     * Chute para fora (issue #57). Existe no protótipo desde sempre e
     * faltava aqui: sem ele, um jogo sem gols vira uma sequência de defesas
     * e o feed não transmite pressão. Carrega o autor pelo mesmo sorteio
     * ponderado do gol.
     */
    @Serializable
    @SerialName("Miss")
    data class Miss(
        override val minute: Int,
        val playerId: PlayerId,
        val home: Boolean,
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

    /**
     * Defesa do goleiro (issue #57). `home` é o lado de QUEM DEFENDEU — a
     * finalização veio do outro time. `goalkeeperId` é nulo apenas quando o
     * lado entrou em campo sem goleiro escalado (elenco incompleto); nulo
     * modelado no tipo em vez de um id "0" mágico.
     */
    @Serializable
    @SerialName("Save")
    data class Save(
        override val minute: Int,
        val goalkeeperId: PlayerId?,
        val home: Boolean,
    ) : MatchEvent

    /**
     * Apito final. Além do placar, carrega o sumário da partida (issue #57):
     * finalizações, defesas e cartões de cada lado, derivados dos próprios
     * eventos emitidos — ver [matchStats].
     */
    @Serializable
    @SerialName("FullTime")
    data class FullTime(
        override val minute: Int = 90,
        val homeGoals: Int,
        val awayGoals: Int,
        val stats: MatchStats,
    ) : MatchEvent
}
