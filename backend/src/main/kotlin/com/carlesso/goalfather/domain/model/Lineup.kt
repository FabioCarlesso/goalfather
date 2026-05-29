package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

/**
 * Escalação de até 11 jogadores numa formação.
 *
 * Validação via `init { require(...) }` — falha rápido na construção,
 * não permite estado inválido. `copy()` herda a validação.
 */
@Serializable
data class Lineup(
    val players: List<Player>,
    val formation: Formation,
) {
    init {
        require(players.size <= 11) {
            "Escalação não pode ter mais que 11 jogadores (tem ${players.size})"
        }
    }

    val isComplete: Boolean get() = players.size == 11
}

/**
 * Força agregada do time = média de overall dos jogadores escalados.
 *
 * Extension function — lê como linguagem natural: `home.teamStrength()`.
 * Default 60.0 para lineup vazio mantém o domínio robusto sem precisar
 * de NPE-handling no caller.
 */
fun Lineup.teamStrength(): Double =
    if (players.isEmpty()) 60.0
    else players.sumOf { it.overall }.toDouble() / players.size
