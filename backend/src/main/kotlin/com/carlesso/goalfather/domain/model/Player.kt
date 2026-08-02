package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.serialization.PlayerIdSerializer
import kotlinx.serialization.Serializable

/**
 * Identificador tipado de jogador.
 *
 * `value class` (inline) — zero overhead em runtime, mas o compilador
 * impede passar Long cru onde um PlayerId é esperado. Substitui o
 * "primitive obsession" do mundo Java.
 *
 * No JSON, sai como número simples (via `PlayerIdSerializer`).
 */
@JvmInline
@Serializable(with = PlayerIdSerializer::class)
value class PlayerId(val value: Long)

/**
 * Jogador imutável. Mutações produzem novas instâncias via `copy()`,
 * nunca via setters.
 *
 * `isStar` é derivado (computed property) — não armazenado, recalculado
 * sob demanda. Idioma Kotlin: comportamento perto do dado, sem getter
 * verboso à la Java.
 *
 * `stamina` (0..100) é consumida a cada rodada pelos titulares e recuperada
 * pelos reservas; `availability` diz se o jogador pode ser escalado. Ambas
 * são movidas exclusivamente pelas regras puras de `domain/rules/FitnessRules`
 * (issue #54).
 */
@Serializable
data class Player(
    val id: PlayerId,
    val name: String,
    val position: Position,
    val overall: Int,
    val pace: Int,
    val shooting: Int,
    val passing: Int,
    val defending: Int,
    val stamina: Int = 100,
    val salary: Int,
    val age: Int,
    val goals: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val availability: Availability = Availability.Available,
) {
    val star: Boolean get() = overall >= 82

    /**
     * Atalho de leitura para as checagens de "pode escalar?". Derivado de
     * [availability] — não é um campo, então não há como divergir dela.
     */
    val injured: Boolean get() = availability is Availability.Injured

    init {
        require(overall in OVERALL_RANGE) { "overall fora de $OVERALL_RANGE: $overall" }
        require(stamina in 0..100) { "stamina fora de 0..100: $stamina" }
        require(age in AGE_RANGE) { "age fora de $AGE_RANGE: $age" }
    }

    /**
     * Os invariantes viram constantes nomeadas para que as REGRAS possam
     * truncar nos mesmos limites (ver `domain/rules/AgingRules.kt`) em vez de
     * repetir os literais e torcer para não divergirem. O `require` continua
     * sendo a última linha de defesa, não a primeira.
     */
    companion object {
        val OVERALL_RANGE: IntRange = 0..99
        val AGE_RANGE: IntRange = 15..50
    }
}
