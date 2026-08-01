package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.serialization.ClubIdSerializer
import kotlinx.serialization.Serializable

/**
 * Identificador tipado de clube. `@JvmInline value class` — zero
 * overhead em runtime, type-safety em tempo de compilação.
 */
@JvmInline
@Serializable(with = ClubIdSerializer::class)
value class ClubId(val value: Long)

/**
 * Clube — agregado de domínio que reúne caixa, estádio, elenco e
 * escalação. `lineup` é opcional: clubes podem existir sem ter
 * escalação salva (fallback aos 11 primeiros do elenco em 4-4-2).
 *
 * Valores monetários em centavos (Long) — mesma convenção do contrato.
 * `ownerId == null` significa controlado pela IA (preparado para
 * multiplayer da Fase 5). `division` é o tier em que o clube disputa a
 * temporada corrente (issue #47) — muda na virada via promoção/rebaixamento.
 */
@Serializable
data class Club(
    val id: ClubId,
    val name: String,
    val cash: Long,
    val stadiumCapacity: Int,
    val squad: List<Player>,
    val lineup: Lineup? = null,
    val ownerId: Long? = null,
    val division: Division = Division.FIRST,
) {
    init {
        require(cash >= 0) { "cash não pode ser negativo: $cash" }
        require(stadiumCapacity >= 0) { "stadiumCapacity não pode ser negativo: $stadiumCapacity" }
    }

    /**
     * Escalação que o time entra em campo — `lineup` salvo, ou
     * fallback dos 11 primeiros do elenco em 4-4-2.
     *
     * O `lineup` salvo guarda uma FOTO dos jogadores do momento em que o
     * técnico escalou, e essa foto envelhece a cada rodada (stamina, lesão,
     * gols, cartões). Por isso os titulares são reidratados pelo id a partir
     * do `squad`, que é a única fonte de verdade do estado do jogador — sem
     * isso a fadiga da issue #54 nunca chegaria à força do time. `mapNotNull`
     * descarta quem saiu do elenco desde a escalação (jogador vendido).
     */
    fun startingLineup(): Lineup {
        val byId = squad.associateBy { it.id }
        return lineup?.let { saved -> saved.copy(players = saved.players.mapNotNull { byId[it.id] }) }
            ?: Lineup(
                formation = Formation.F_4_4_2,
                players = squad.take(11),
            )
    }
}
