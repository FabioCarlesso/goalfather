package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.CareerCampaign
import com.carlesso.goalfather.domain.model.ClubCareer
import com.carlesso.goalfather.domain.model.CalendarView
import com.carlesso.goalfather.domain.model.KnockoutPhase
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.ReadinessStatus
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.SeasonRecord
import com.carlesso.goalfather.domain.model.SeasonStanding
import com.carlesso.goalfather.domain.model.SeasonTopScorer
import com.carlesso.goalfather.domain.model.Slot
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.TableZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * DTOs da liga — `GET /api/league/standings`, `/history`, `/round/...` (issue
 * #97). Fecham a regra de `Responses.kt` no último controller que ainda
 * devolvia o domínio: o JSON batia com o contrato, mas por coincidência, não
 * por construção — um campo novo no domínio vazaria sem passar pelo
 * `openapi.yaml`, e uma computed property que o contrato declarasse não
 * chegaria (a família de defeito do `Player.star`, #56/#72).
 *
 * `Division` e `ClubId`/`PlayerId` são value classes no domínio; aqui viram
 * `Int`/`Long` crus, que é o que o contrato declara.
 *
 * Reusados do domínio, pelo critério de `Responses.kt` (enum cuja forma JSON já
 * é EXATAMENTE a do contrato): `RoundStatus` e `KnockoutPhase`.
 *
 * `ReadinessStatus.allReady`/`canStart` são computed properties do domínio
 * (getter sem backing field, que o kotlinx não serializa). O DTO os MATERIALIZA
 * (issue #98), no padrão do `star` de `PlayerDto`: o gate do `POST /round/play`
 * chega pronto ao cliente, que deixa de reimplementar a regra.
 */
@Serializable
data class StandingRowDto(
    val position: Int,
    val clubId: Long,
    val clubName: String,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int,
    val points: Int,
    /** Faixa da posição (issue #128): o enum do domínio vira o nome, como o contrato declara. */
    val zone: TableZone,
)

@Serializable
data class StandingsDto(
    val season: Int,
    /** `Int` cru: o contrato declara `division` como `integer`, sem o value class. */
    val division: Int,
    val round: Int,
    val promotionSpots: Int,
    val relegationSpots: Int,
    val rows: List<StandingRowDto>,
)

// ─── Histórico (issue #60) ────────────────────────────────────────────────

@Serializable
data class SeasonStandingDto(
    val division: Int,
    val row: StandingRowDto,
    val pointsPercentage: Int,
)

@Serializable
data class SeasonTopScorerDto(
    val playerId: Long,
    val playerName: String,
    val clubId: Long,
    val clubName: String,
    val goals: Int,
)

@Serializable
data class SeasonRecordDto(
    val season: Int,
    val champion: SeasonStandingDto,
    val finalStandings: List<SeasonStandingDto>,
    /** Ausente (não `null`) quando ninguém marcou na temporada — `explicitNulls = false`. */
    val topScorer: SeasonTopScorerDto? = null,
    /** Ausente quando não houve copa ou ela não chegou à final. */
    val cupChampion: CupClubDto? = null,
)

@Serializable
data class CareerCampaignDto(
    val season: Int,
    val standing: SeasonStandingDto,
)

@Serializable
data class ClubCareerDto(
    val clubId: Long,
    val clubName: String,
    val seasonsPlayed: Int,
    val titles: List<Int>,
    val cupTitles: List<Int>,
    /** Só falta com a carreira vazia. */
    val bestCampaign: CareerCampaignDto? = null,
)

// ─── Rodada ───────────────────────────────────────────────────────────────

/**
 * Espelha o `oneOf` + `discriminator: type` de `RoundStage` no contrato. O
 * sealed de domínio também é `@Serializable` (persistência em `stage_json`),
 * mas é ESTE `@SerialName` que fixa o nome que o frontend lê: mudar o domínio
 * para gravar outro nome não pode quebrar o `switch (stage.type)` da UI.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface RoundStageDto {

    @Serializable
    @SerialName("League")
    data class League(val matchday: Int) : RoundStageDto

    @Serializable
    @SerialName("Cup")
    data class Cup(val phase: KnockoutPhase) : RoundStageDto
}

/**
 * Um encaixe do calendário semanal (issue #125). `key` é a identidade estável
 * (rota do WebSocket, prontidão); o resto é a mesma informação já decomposta
 * para a UI não precisar interpretar a string.
 */
@Serializable
data class MatchdayDto(
    val key: String,
    val year: Int,
    val week: Int,
    val slot: Slot,
    /** ISO-8601 (`2026-02-01`): a quarta ou o domingo do encaixe. */
    val date: String,
)

@Serializable
data class RoundMatchDto(
    val matchId: Long,
    val homeClubId: Long,
    val awayClubId: Long,
    val homeClubName: String,
    val awayClubName: String,
    val status: RoundStatus,
    val homeGoals: Int,
    val awayGoals: Int,
    val minute: Int,
    val division: Int,
    /** Só em jogo de copa encerrado empatado; ausente nos demais. */
    val penalties: PenaltyScoreDto? = null,
    /** O que a partida disputa — por partida desde o encaixe misto (issue #125). */
    val stage: RoundStageDto,
    val competitionId: String,
    val competitionName: String,
)

@Serializable
data class RoundDto(
    val matchday: MatchdayDto,
    val season: Int,
    val status: RoundStatus,
    val matches: List<RoundMatchDto>,
)

/**
 * `GET /api/league/calendar` (issue #125): a data corrente do jogo e o próximo
 * jogo do clube, com a competição dele. Os dois somem quando não há o que
 * mostrar (liga sem calendário, clube sem jogo marcado na temporada).
 */
@Serializable
data class CalendarDto(
    val current: MatchdayDto? = null,
    val season: Int? = null,
    val nextMatch: NextMatchDto? = null,
)

@Serializable
data class NextMatchDto(
    val matchday: MatchdayDto,
    val match: RoundMatchDto,
)

@Serializable
data class ReadinessStatusDto(
    /** Chave do encaixe (issue #125); ausente sem rodada no calendário. */
    val matchday: String? = null,
    val readyCount: Int,
    val totalCount: Int,
    val pendingUsernames: List<String>,
    /** Ausente quando o cronômetro do escape hatch não está armado. */
    val secondsRemaining: Int? = null,
    val timedOut: Boolean,
    /** Materializado de `ReadinessStatus.allReady` (issue #98). */
    val allReady: Boolean,
    /** Materializado de `ReadinessStatus.canStart` — o mesmo gate do play (issue #98). */
    val canStart: Boolean,
)

// ─── Mapeamento domínio → DTO ─────────────────────────────────────────────

fun StandingRow.toDto(): StandingRowDto = StandingRowDto(
    position = position,
    clubId = clubId.value,
    clubName = clubName,
    played = played,
    wins = wins,
    draws = draws,
    losses = losses,
    goalsFor = goalsFor,
    goalsAgainst = goalsAgainst,
    goalDifference = goalDifference,
    points = points,
    zone = zone,
)

fun Standings.toDto(): StandingsDto = StandingsDto(
    season = season,
    division = division.value,
    round = round,
    promotionSpots = promotionSpots,
    relegationSpots = relegationSpots,
    rows = rows.map { it.toDto() },
)

private fun SeasonStanding.toDto(): SeasonStandingDto =
    SeasonStandingDto(division = division.value, row = row.toDto(), pointsPercentage = pointsPercentage)

private fun SeasonTopScorer.toDto(): SeasonTopScorerDto = SeasonTopScorerDto(
    playerId = playerId.value,
    playerName = playerName,
    clubId = clubId.value,
    clubName = clubName,
    goals = goals,
)

fun SeasonRecord.toDto(): SeasonRecordDto = SeasonRecordDto(
    season = season,
    champion = champion.toDto(),
    finalStandings = finalStandings.map { it.toDto() },
    topScorer = topScorer?.toDto(),
    cupChampion = cupChampion?.toDto(),
)

private fun CareerCampaign.toDto(): CareerCampaignDto =
    CareerCampaignDto(season = season, standing = standing.toDto())

fun ClubCareer.toDto(): ClubCareerDto = ClubCareerDto(
    clubId = clubId.value,
    clubName = clubName,
    seasonsPlayed = seasonsPlayed,
    titles = titles,
    cupTitles = cupTitles,
    bestCampaign = bestCampaign?.toDto(),
)

/** `when` exaustivo, sem `else`: tipo de data novo no calendário quebra a compilação aqui. */
private fun RoundStage.toDto(): RoundStageDto = when (this) {
    is RoundStage.League -> RoundStageDto.League(matchday = matchday)
    is RoundStage.Cup -> RoundStageDto.Cup(phase = phase)
}

fun RoundMatch.toDto(): RoundMatchDto = RoundMatchDto(
    matchId = matchId,
    homeClubId = homeClubId.value,
    awayClubId = awayClubId.value,
    homeClubName = homeClubName,
    awayClubName = awayClubName,
    status = status,
    homeGoals = homeGoals,
    awayGoals = awayGoals,
    minute = minute,
    division = division.value,
    penalties = penalties?.toDto(),
    stage = stage.toDto(),
    competitionId = competition.value,
    competitionName = competitionName,
)

fun Matchday.toDto(): MatchdayDto = MatchdayDto(
    key = key,
    year = week.year,
    week = week.week,
    slot = slot,
    date = date.toString(),
)

fun Round.toDto(): RoundDto = RoundDto(
    matchday = matchday.toDto(),
    season = season,
    status = status,
    matches = matches.map { it.toDto() },
)

fun CalendarView.toDto(): CalendarDto = CalendarDto(
    current = current?.toDto(),
    season = season,
    nextMatch = nextMatch?.let { NextMatchDto(matchday = it.matchday.toDto(), match = it.match.toDto()) },
)

fun ReadinessStatus.toDto(): ReadinessStatusDto = ReadinessStatusDto(
    matchday = matchday?.key,
    readyCount = readyCount,
    totalCount = totalCount,
    pendingUsernames = pendingUsernames,
    secondsRemaining = secondsRemaining,
    timedOut = timedOut,
    // Lê o getter do domínio — a regra continua num lugar só (`RoundReadiness.kt`).
    allReady = allReady,
    canStart = canStart,
)
