package com.carlesso.goalfather.domain.model

/**
 * Identificador tipado de clube. `@JvmInline value class` — zero
 * overhead em runtime, type-safety em tempo de compilação.
 */
@JvmInline
value class ClubId(val value: Long)

/**
 * Clube — agregado de domínio que reúne caixa, estádio, elenco e
 * escalação. `lineup` é opcional: clubes podem existir sem ter
 * escalação salva (fallback aos 11 primeiros do elenco em 4-4-2).
 *
 * Valores monetários em centavos (Long) — mesma convenção do contrato.
 * `ownerId == null` significa controlado pela IA (preparado para
 * multiplayer da Fase 5).
 */
data class Club(
    val id: ClubId,
    val name: String,
    val cash: Long,
    val stadiumCapacity: Int,
    val squad: List<Player>,
    val lineup: Lineup? = null,
    val ownerId: Long? = null,
) {
    init {
        require(cash >= 0) { "cash não pode ser negativo: $cash" }
        require(stadiumCapacity >= 0) { "stadiumCapacity não pode ser negativo: $stadiumCapacity" }
    }

    /**
     * Escalação que o time entra em campo — `lineup` salvo, ou
     * fallback dos 11 primeiros do elenco em 4-4-2.
     */
    fun startingLineup(): Lineup =
        lineup ?: Lineup(
            formation = Formation.F_4_4_2,
            players = squad.take(11),
        )
}
