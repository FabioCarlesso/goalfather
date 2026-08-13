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
 *
 * `trainingFocus` (issue #58) é a decisão da SEMANA, e por isso mora no clube
 * e não na `Lineup`: ela vale entre rodadas, para o elenco inteiro, enquanto a
 * escalação é a decisão da PARTIDA. Fica valendo até o técnico trocar — quem
 * nunca escolheu (e todo clube da IA) treina no default, `DESCANSO`.
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
    val trainingFocus: TrainingFocus = TrainingFocus.DEFAULT,
) {
    init {
        require(cash >= 0) { "cash não pode ser negativo: $cash" }
        require(stadiumCapacity >= 0) { "stadiumCapacity não pode ser negativo: $stadiumCapacity" }
    }

    /**
     * Escalação que o time DE FATO entra em campo — `lineup` salvo, ou
     * fallback dos 11 primeiros do elenco em 4-4-2.
     *
     * Duas correções sobre o `lineup` salvo, que é só uma FOTO do momento em
     * que o técnico escalou e envelhece a cada rodada:
     *
     * 1. **Reidratação pelo id a partir do `squad`**, única fonte de verdade
     *    do estado do jogador — sem isso a fadiga da issue #54 nunca chegaria
     *    à força do time. `mapNotNull` descarta quem saiu do elenco desde a
     *    escalação (jogador vendido).
     * 2. **Lesionado não entra em campo.** `SaveLineupService` recusa escalar
     *    lesionado, mas isso vale para o momento de SALVAR: quem se machuca
     *    depois continuaria titular na rodada seguinte se a escalação não
     *    fosse revalidada aqui, no apito inicial.
     *
     * As vagas abertas pelos lesionados são preenchidas por reservas aptos, na
     * ordem do elenco — substituição simples e determinística, não uma
     * auto-escalação inteligente (não tenta casar posição: a validação
     * posicional segue fora do domínio, como no `SaveLineupService`). Sem
     * reservas aptos suficientes o time entra desfalcado, com menos de 11 —
     * que é o resultado correto para um elenco dizimado.
     */
    fun startingLineup(): Lineup {
        val byId = squad.associateBy { it.id }
        val available = squad.filterNot { it.injured }

        val saved = lineup ?: return Lineup(
            formation = Formation.F_4_4_2,
            players = available.take(11),
        )

        val starters = saved.players.mapNotNull { byId[it.id] }.filterNot { it.injured }
        val onField = starters.map { it.id }.toSet()
        val substitutes = available.filterNot { it.id in onField }
        return saved.copy(players = (starters + substitutes).take(11))
    }
}
