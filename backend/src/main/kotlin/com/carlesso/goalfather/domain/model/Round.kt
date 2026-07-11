package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class RoundStatus { Scheduled, InProgress, Finished }

@Serializable
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
    // Divisão da partida (issue #47). Default = elite: rodadas persistidas
    // antes das divisões desserializam sem migração de dados no matches_json.
    val division: Division = Division.FIRST,
)

@Serializable
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
