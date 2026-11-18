package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.application.port.`in`.ClubCallUps
import com.carlesso.goalfather.application.port.`in`.NationalTeamsOverview
import com.carlesso.goalfather.application.port.`in`.WindowStatus
import com.carlesso.goalfather.application.port.`in`.WindowSummary
import com.carlesso.goalfather.domain.model.Confederation
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.NationalMatch
import com.carlesso.goalfather.domain.model.NationalMatchKind
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.rules.NationalTableRow
import kotlinx.serialization.Serializable

/**
 * Seleções como a API as descreve (issue #134) — espelham os schemas do bloco
 * "#134 Seleções" do contrato. Os códigos de país viram `CountryDto` (nome e
 * bandeira) aqui, no adapter: o domínio só guarda o trigrama.
 */

@Serializable
data class FifaWindowSummaryDto(
    val start: String,
    val end: String,
    val status: WindowStatus,
    val matchSlots: Int,
)

@Serializable
data class CalledUpPlayerDto(
    val playerId: Long,
    val name: String,
    val position: Position,
    val overall: Int,
    val clubId: Long,
    val clubName: String,
    val country: CountryDto,
)

@Serializable
data class ClubCallUpsDto(
    /** Ausente (não `null`) num save sem calendário, por `explicitNulls = false`. */
    val window: FifaWindowSummaryDto? = null,
    val players: List<CalledUpPlayerDto>,
)

@Serializable
data class NationalMatchDto(
    val home: CountryDto,
    val away: CountryDto,
    val kind: NationalMatchKind,
    val matchNumber: Int,
    val homeGoals: Int,
    val awayGoals: Int,
    val qualifierRound: Int? = null,
)

@Serializable
data class NationalTableRowDto(
    val position: Int,
    val country: CountryDto,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int,
    val points: Int,
)

@Serializable
data class QualifiersTableDto(
    val cycle: Int,
    val roundsPlayed: Int,
    val totalRounds: Int,
    val rows: List<NationalTableRowDto>,
)

@Serializable
data class NationalTeamsOverviewDto(
    val windows: List<FifaWindowSummaryDto>,
    val nextWindow: FifaWindowSummaryDto? = null,
    val lastWindow: FifaWindowSummaryDto? = null,
    val lastResults: List<NationalMatchDto>,
    val qualifiers: QualifiersTableDto,
    val ranking: List<NationalTableRowDto>,
)

fun WindowSummary.toDto(): FifaWindowSummaryDto = FifaWindowSummaryDto(
    start = window.start.toString(),
    end = window.end.toString(),
    status = status,
    matchSlots = matchSlots,
)

fun ClubCallUps.toDto(): ClubCallUpsDto = ClubCallUpsDto(
    window = window?.toDto(),
    players = players.map { (player, country) ->
        CalledUpPlayerDto(
            playerId = player.playerId.value,
            name = player.name,
            position = player.position,
            overall = player.overall,
            clubId = player.clubId.value,
            clubName = player.clubName,
            country = country.toDto(),
        )
    },
)

fun NationalTeamsOverview.toDto(): NationalTeamsOverviewDto {
    // Função local: enxerga `countries` sem passá-lo a cada chamada.
    fun country(code: CountryCode): CountryDto = countryOf(code, countries).toDto()

    fun List<NationalTableRow>.rows(): List<NationalTableRowDto> = mapIndexed { index, row ->
        NationalTableRowDto(
            position = index + 1,
            country = country(row.country),
            played = row.played,
            won = row.won,
            drawn = row.drawn,
            lost = row.lost,
            goalsFor = row.goalsFor,
            goalsAgainst = row.goalsAgainst,
            goalDifference = row.goalDifference,
            points = row.points,
        )
    }

    fun NationalMatch.toDto() = NationalMatchDto(
        home = country(home),
        away = country(away),
        kind = kind,
        matchNumber = matchNumber,
        homeGoals = homeGoals,
        awayGoals = awayGoals,
        qualifierRound = qualifierRound,
    )

    return NationalTeamsOverviewDto(
        windows = windows.map { it.toDto() },
        nextWindow = nextWindow?.toDto(),
        lastWindow = lastWindow?.toDto(),
        lastResults = lastResults.map { it.toDto() },
        qualifiers = QualifiersTableDto(
            cycle = qualifiers.cycle,
            roundsPlayed = qualifiers.roundsPlayed,
            totalRounds = qualifiers.totalRounds,
            rows = qualifiers.table.rows(),
        ),
        ranking = ranking.rows(),
    )
}

/**
 * País de um código. Um trigrama sem país no save (não acontece com o pack)
 * vira um país "anônimo" com o próprio código como nome, em vez de derrubar a
 * resposta inteira; sem `isoAlpha2`, a bandeira sai vazia.
 */
private fun countryOf(code: CountryCode, countries: Map<CountryCode, Country>): Country =
    countries[code] ?: Country(code, code.value, Confederation.CONMEBOL, "")
