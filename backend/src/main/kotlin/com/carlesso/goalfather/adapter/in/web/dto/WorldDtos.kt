package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.application.port.`in`.AvailableClub
import com.carlesso.goalfather.application.port.`in`.OnboardingCountry
import com.carlesso.goalfather.application.port.`in`.OnboardingLeague
import com.carlesso.goalfather.domain.model.CompetitionKind
import kotlinx.serialization.Serializable

/*
 * DTOs do onboarding país → liga → clube (issues #19 e #131). Espelham
 * `WorldCountry`, `WorldLeague` e `AvailableClub` do contrato; o domínio
 * (`GameCompetition`, `Club`) não vaza para a API.
 */

@Serializable
data class WorldCountryDto(
    val country: CountryDto,
    val leagues: List<WorldLeagueDto>,
)

@Serializable
data class WorldLeagueDto(
    val id: String,
    val name: String,
    val tier: Int,
    val clubCount: Int,
    val availableClubCount: Int,
    val seasonLabel: String,
)

@Serializable
data class AvailableClubDto(
    val id: Long,
    val name: String,
    val cash: Long,
    val stadiumCapacity: Int,
    val averageOverall: Int,
    /** Divisão em que o clube disputa a temporada (issue #47) — orienta a escolha. */
    val division: Int,
    val country: CountryDto,
    /** Liga que o clube disputa (issue #131). */
    val competitionId: String,
    val competitionName: String,
)

fun OnboardingCountry.toDto(): WorldCountryDto = WorldCountryDto(
    country = country.toDto(),
    leagues = leagues.map { it.toDto() },
)

/**
 * O `tier` vem do papel da liga. `when` sobre o sealed, com a copa como erro:
 * o caso de uso só devolve ligas, então chegar aqui com copa é bug, não dado.
 */
fun OnboardingLeague.toDto(): WorldLeagueDto = WorldLeagueDto(
    id = league.id.value,
    name = league.competition.name,
    tier = when (val kind = league.kind) {
        is CompetitionKind.League -> kind.tier.value
        CompetitionKind.NationalCup, CompetitionKind.Continental ->
            error("Copa ${league.id.value} não é liga do onboarding")
    },
    clubCount = clubCount,
    availableClubCount = availableClubCount,
    seasonLabel = seasonLabel,
)

fun AvailableClub.toDto(): AvailableClubDto = AvailableClubDto(
    id = club.id.value,
    name = club.name,
    cash = club.cash.cents,
    stadiumCapacity = club.stadiumCapacity,
    averageOverall = averageOverall,
    division = club.division.value,
    country = club.country.toDto(),
    competitionId = league.id.value,
    competitionName = league.competition.name,
)
