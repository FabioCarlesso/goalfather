package com.carlesso.goalfather.domain.event

import com.carlesso.goalfather.domain.model.Retirement
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

    /**
     * `standings` traz UMA tabela por divisão (issue #47), ordenadas da
     * elite para baixo — todas jogam na mesma rodada global.
     *
     * `training` (issue #58) conta a semana de treino de cada clube: o foco
     * aplicado e quem evoluiu ou se machucou nele. Vem de TODOS os clubes,
     * como `finances` — cabe ao cliente filtrar o do técnico. Default vazio
     * para não quebrar quem já consumia o evento (e é o que o replay de uma
     * rodada já encerrada emite: os efeitos não são reaplicados).
     */
    @Serializable
    @SerialName("RoundFinished")
    data class RoundFinished(
        val standings: List<Standings>,
        val finances: List<RoundFinance> = emptyList(),
        val training: List<TrainingReport> = emptyList(),
    ) : RoundEvent

    /**
     * Emitido quando a última rodada do turno é encerrada: a temporada
     * acabou. Carrega o `champion` (líder da elite — divisão 1) e as
     * tabelas finais de todas as divisões para que o cliente celebre o
     * título. O backend já abriu a próxima temporada (promoção/rebaixamento
     * aplicados, rodada 1 + tabelas zeradas) antes de emitir.
     *
     * `retirements` conta a outra história da virada (issue #55): quem
     * pendurou as chuteiras e qual garoto da base assumiu a vaga. Vem de TODOS
     * os clubes — cabe ao cliente filtrar o que interessa ao técnico. Default
     * vazio para não quebrar quem já consumia o evento.
     */
    @Serializable
    @SerialName("SeasonFinished")
    data class SeasonFinished(
        val season: Int,
        val champion: StandingRow,
        val standings: List<Standings>,
        val retirements: List<Retirement> = emptyList(),
    ) : RoundEvent
}
