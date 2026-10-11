package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.RoundEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundStatusEnum
import com.carlesso.goalfather.adapter.out.persistence.entity.StandingsEntity
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

fun RoundEntity.toDomain(json: Json): Round = Round(
    number = number,
    season = season,
    status = status.toDomain(),
    matches = json.decodeFromString(ListSerializer(RoundMatch.serializer()), matchesJson),
    // Linha anterior à copa (issue #61) não tem `stage_json`: era rodada de
    // liga, e sem copa a rodada de liga coincide com a data.
    stage = stageJson?.let { json.decodeFromString(RoundStage.serializer(), it) } ?: RoundStage.League(number),
)

fun Round.toEntity(json: Json): RoundEntity = RoundEntity(
    number = number,
    season = season,
    status = status.toEntity(),
    matchesJson = json.encodeToString(ListSerializer(RoundMatch.serializer()), matches),
    stageJson = json.encodeToString(RoundStage.serializer(), stage),
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
    stageJson = json.encodeToString(RoundStage.serializer(), round.stage)
}

fun StandingsEntity.toDomain(json: Json): Standings = Standings(
    season = season,
    round = round,
    rows = json.decodeFromString(ListSerializer(StandingRow.serializer()), rowsJson),
    division = Division(division),
    promotionSpots = promotionSpots,
    relegationSpots = relegationSpots,
)

fun Standings.toEntity(json: Json): StandingsEntity = StandingsEntity(
    season = season,
    division = division.value,
    round = round,
    promotionSpots = promotionSpots,
    relegationSpots = relegationSpots,
    rowsJson = json.encodeToString(ListSerializer(StandingRow.serializer()), rows),
)

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
