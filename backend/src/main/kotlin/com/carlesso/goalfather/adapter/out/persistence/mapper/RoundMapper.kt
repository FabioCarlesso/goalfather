package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.MatchdayKey
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundStatusEnum
import com.carlesso.goalfather.adapter.out.persistence.entity.StandingsEntity
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.GameWeek
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.PlayedMatch
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.Slot
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

fun RoundEntity.toDomain(json: Json): Round = Round(
    matchday = id.toDomain(),
    season = season,
    status = status.toDomain(),
    matches = json.decodeFromString(ListSerializer(RoundMatch.serializer()), matchesJson),
)

fun Round.toEntity(json: Json): RoundEntity = RoundEntity(
    id = matchday.toKey(),
    season = season,
    status = status.toEntity(),
    matchesJson = json.encodeToString(ListSerializer(RoundMatch.serializer()), matches),
)

/**
 * Copia os campos do domínio numa entidade JÁ carregada. Necessário desde o
 * `@Version` da rodada (issue #46): mapear para uma instância nova (version=0)
 * faria o Hibernate ver um update obsoleto contra a versão do banco e estourar
 * `StaleObjectStateException` em todo `saveRound`. Mesmo cuidado do clube (#19).
 */
fun RoundEntity.updateFrom(round: Round, json: Json): RoundEntity = apply {
    season = round.season
    status = round.status.toEntity()
    matchesJson = json.encodeToString(ListSerializer(RoundMatch.serializer()), round.matches)
}

/** Encaixe → chave composta da tabela (issue #125). */
fun Matchday.toKey(): MatchdayKey = MatchdayKey(isoYear = week.year, isoWeek = week.week, slot = slot.name)

fun MatchdayKey.toDomain(): Matchday = Matchday(GameWeek(isoYear, isoWeek), Slot.valueOf(slot))

fun StandingsEntity.toDomain(json: Json): Standings = Standings(
    competitionId = CompetitionId(competitionId),
    competitionName = competitionName,
    country = countryCode.toCountry(),
    season = season,
    seasonLabel = seasonLabel,
    round = round,
    rows = json.decodeFromString(ListSerializer(StandingRow.serializer()), rowsJson),
    division = Division(division),
    promotionSpots = promotionSpots,
    relegationSpots = relegationSpots,
    results = json.decodeFromString(ListSerializer(PlayedMatchJson.serializer()), resultsJson).map { it.toDomain() },
)

fun Standings.toEntity(json: Json): StandingsEntity = StandingsEntity(
    competitionId = competitionId.value,
    season = season,
    competitionName = competitionName,
    countryCode = country.code.value,
    seasonLabel = seasonLabel,
    division = division.value,
    round = round,
    promotionSpots = promotionSpots,
    relegationSpots = relegationSpots,
    rowsJson = json.encodeToString(ListSerializer(StandingRow.serializer()), rows),
    resultsJson = json.encodeToString(ListSerializer(PlayedMatchJson.serializer()), results.map { it.toJson() }),
)

/**
 * Forma persistida de um placar da tabela (`results_json`). `PlayedMatch` é
 * do domínio genérico de formatos e não é `@Serializable`; um DTO de
 * persistência compacto (ids crus, nomes curtos) segura ~380 jogos por
 * temporada sem inflar a linha.
 */
@Serializable
private data class PlayedMatchJson(val h: Long, val a: Long, val hg: Int, val ag: Int) {
    fun toDomain(): PlayedMatch = PlayedMatch(ClubId(h), ClubId(a), hg, ag)
}

private fun PlayedMatch.toJson(): PlayedMatchJson = PlayedMatchJson(home.value, away.value, homeGoals, awayGoals)

private fun RoundStatusEnum.toDomain(): RoundStatus = when (this) {
    RoundStatusEnum.Scheduled -> RoundStatus.Scheduled
    RoundStatusEnum.InProgress -> RoundStatus.InProgress
    RoundStatusEnum.Finished -> RoundStatus.Finished
}

private fun RoundStatus.toEntity(): RoundStatusEnum = when (this) {
    RoundStatus.Scheduled -> RoundStatusEnum.Scheduled
    RoundStatus.InProgress -> RoundStatusEnum.InProgress
    RoundStatus.Finished -> RoundStatusEnum.Finished
}
