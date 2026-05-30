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
    val injured: Boolean = false,
) {
    val star: Boolean get() = overall >= 82

    init {
        require(overall in 0..99) { "overall fora de 0..99: $overall" }
        require(stamina in 0..100) { "stamina fora de 0..100: $stamina" }
        require(age in 15..50) { "age fora de 15..50: $age" }
    }
}
