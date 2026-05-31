package com.carlesso.goalfather.domain.event

import com.carlesso.goalfather.domain.model.RoundFinance
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Mensagem do stream de rodada (WS `/ws/round/{n}`).
 *
 * Multiplexa eventos de várias partidas + sinal de fim de rodada.
 * Mapeia o schema `RoundEvent` no `contract/openapi.yaml` (oneOf +
 * discriminator `type`).
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface RoundEvent {

    @Serializable
    @SerialName("MatchUpdate")
    data class MatchUpdate(
        val matchId: Long,
        val event: MatchEvent,
    ) : RoundEvent

    @Serializable
    @SerialName("RoundFinished")
    data class RoundFinished(
        val standings: Standings,
        val finances: List<RoundFinance> = emptyList(),
    ) : RoundEvent

    /**
     * Emitido quando a última rodada do turno é encerrada: a temporada
     * acabou. Carrega o `champion` (líder da tabela final) e a `standings`
     * encerrada para que o cliente celebre o título. O backend já abriu a
     * próxima temporada (rodada 1 + tabela zerada) antes de emitir.
     */
    @Serializable
    @SerialName("SeasonFinished")
    data class SeasonFinished(
        val season: Int,
        val champion: StandingRow,
        val standings: Standings,
    ) : RoundEvent
}
