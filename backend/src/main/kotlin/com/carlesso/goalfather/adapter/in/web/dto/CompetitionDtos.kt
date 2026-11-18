package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.application.port.`in`.CompetitionMatchday
import com.carlesso.goalfather.application.port.`in`.CompetitionOverview
import com.carlesso.goalfather.domain.model.ClubSchedule
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.CompetitionScorer
import com.carlesso.goalfather.domain.model.Confederation
import com.carlesso.goalfather.domain.model.NextMatch
import com.carlesso.goalfather.domain.model.ScheduleWeek
import kotlinx.serialization.Serializable

/*
 * DTOs da navegação pelo mundo (issue #133). Espelham `CompetitionSummary`,
 * `CompetitionDetail`, `CompetitionMatchday`, `CompetitionScorer`,
 * `ScheduleWeek` e `ClubSchedule` do contrato; o domínio (`GameCompetition`,
 * `CompetitionFormat` sealed) não vaza para a API.
 */

/**
 * O formato da competição achatado num enum: a UI só precisa saber QUAL é o
 * formato para escolher as visões (tabela, chave, grupos); os detalhes de cada
 * variante do `sealed CompetitionFormat` ficam no domínio.
 */
@Serializable
enum class CompetitionFormatType { ROUND_ROBIN, KNOCKOUT, GROUPS_THEN_KNOCKOUT, SWISS_LEAGUE }

/** O papel da competição. `CONTINENTAL` já está no contrato para a Libertadores (#132). */
@Serializable
enum class CompetitionKindType { LEAGUE, NATIONAL_CUP, CONTINENTAL }

@Serializable
data class CompetitionSummaryDto(
    val id: String,
    val name: String,
    val format: CompetitionFormatType,
    val kind: CompetitionKindType,
    val country: CountryDto? = null,
    val confederation: Confederation,
    val tier: Int? = null,
    val season: Int? = null,
    val seasonLabel: String? = null,
    val clubCount: Int,
)

@Serializable
data class CompetitionDetailDto(
    val id: String,
    val name: String,
    val format: CompetitionFormatType,
    val kind: CompetitionKindType,
    val country: CountryDto? = null,
    val confederation: Confederation,
    val tier: Int? = null,
    val season: Int? = null,
    val seasonLabel: String? = null,
    val clubCount: Int,
    /** Nomes do enum `Tiebreaker` do domínio, na ordem em que a tabela os aplica. */
    val tiebreakers: List<String>,
    val foreignPlayerLimit: Int? = null,
)

@Serializable
data class CompetitionMatchdayDto(
    val matchday: MatchdayDto,
    val matches: List<RoundMatchDto>,
)

@Serializable
data class CompetitionScorerDto(
    val playerId: Long,
    val playerName: String,
    val clubId: Long,
    val clubName: String,
    val goals: Int,
)

@Serializable
data class ScheduleWeekDto(
    val year: Int,
    val week: Int,
    /** Segunda-feira, ISO-8601. */
    val start: String,
    /** Domingo, ISO-8601. */
    val end: String,
    val fifaWindow: Boolean,
    val matches: List<NextMatchDto>,
)

@Serializable
data class ClubScheduleDto(
    val clubId: Long,
    val current: MatchdayDto? = null,
    val weeks: List<ScheduleWeekDto>,
)

/** `when` exaustivo: um formato novo no domínio quebra a compilação aqui até ganhar nome no contrato. */
fun CompetitionFormat.toType(): CompetitionFormatType = when (this) {
    is CompetitionFormat.RoundRobin -> CompetitionFormatType.ROUND_ROBIN
    is CompetitionFormat.Knockout -> CompetitionFormatType.KNOCKOUT
    is CompetitionFormat.GroupsThenKnockout -> CompetitionFormatType.GROUPS_THEN_KNOCKOUT
    is CompetitionFormat.SwissLeague -> CompetitionFormatType.SWISS_LEAGUE
}

fun CompetitionKind.toType(): CompetitionKindType = when (this) {
    is CompetitionKind.League -> CompetitionKindType.LEAGUE
    CompetitionKind.NationalCup -> CompetitionKindType.NATIONAL_CUP
}

/** Divisão no país — só as ligas têm. */
private val CompetitionKind.tierOrNull: Int?
    get() = when (this) {
        is CompetitionKind.League -> tier.value
        CompetitionKind.NationalCup -> null
    }

fun CompetitionOverview.toSummaryDto(): CompetitionSummaryDto = CompetitionSummaryDto(
    id = game.id.value,
    name = game.competition.name,
    format = game.competition.format.toType(),
    kind = game.kind.toType(),
    country = game.country.toDto(),
    confederation = game.country.confederation,
    tier = game.kind.tierOrNull,
    season = season,
    seasonLabel = seasonLabel,
    clubCount = clubCount,
)

fun CompetitionOverview.toDetailDto(): CompetitionDetailDto = CompetitionDetailDto(
    id = game.id.value,
    name = game.competition.name,
    format = game.competition.format.toType(),
    kind = game.kind.toType(),
    country = game.country.toDto(),
    confederation = game.country.confederation,
    tier = game.kind.tierOrNull,
    season = season,
    seasonLabel = seasonLabel,
    clubCount = clubCount,
    tiebreakers = game.competition.tiebreakers.map { it.name },
    foreignPlayerLimit = game.competition.foreignPlayerLimit,
)

fun CompetitionMatchday.toDto(): CompetitionMatchdayDto =
    CompetitionMatchdayDto(matchday = matchday.toDto(), matches = matches.map { it.toDto() })

fun CompetitionScorer.toDto(): CompetitionScorerDto = CompetitionScorerDto(
    playerId = playerId.value,
    playerName = playerName,
    clubId = clubId.value,
    clubName = clubName,
    goals = goals,
)

private fun NextMatch.toDto(): NextMatchDto = NextMatchDto(matchday = matchday.toDto(), match = match.toDto())

private fun ScheduleWeek.toDto(): ScheduleWeekDto = ScheduleWeekDto(
    year = week.year,
    week = week.week,
    start = week.monday.toString(),
    end = week.sunday.toString(),
    fifaWindow = fifaWindow,
    matches = matches.map { it.toDto() },
)

fun ClubSchedule.toDto(): ClubScheduleDto = ClubScheduleDto(
    clubId = clubId.value,
    current = current?.toDto(),
    weeks = weeks.map { it.toDto() },
)
