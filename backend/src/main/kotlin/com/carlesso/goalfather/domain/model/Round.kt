package com.carlesso.goalfather.domain.model

/**
 * Estado de uma rodada ou de uma partida individual.
 * Espelha `RoundStatus` no contrato OpenAPI.
 */
enum class RoundStatus { Scheduled, InProgress, Finished }

/**
 * Pareamento dentro de uma rodada. Carrega `matchId` próprio (chave
 * estável para WS multiplexado em `/ws/round/{n}`).
 */
data class RoundMatch(
    val matchId: Long,
    val homeClubId: ClubId,
    val awayClubId: ClubId,
    val homeClubName: String,
    val awayClubName: String,
    val status: RoundStatus = RoundStatus.Scheduled,
    val homeGoals: Int = 0,
    val awayGoals: Int = 0,
    val minute: Int = 0,
)

/**
 * Rodada — coleção de partidas + numeração temporal.
 */
data class Round(
    val number: Int,
    val season: Int,
    val status: RoundStatus = RoundStatus.Scheduled,
    val matches: List<RoundMatch>,
) {
    init {
        require(number >= 1) { "Rodada deve ser >= 1: $number" }
    }

    val isFinished: Boolean
        get() = matches.all { it.status == RoundStatus.Finished }
}
