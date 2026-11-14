package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.SeasonHistoryEntity
import com.carlesso.goalfather.domain.model.SeasonRecord
import kotlinx.serialization.json.Json

fun SeasonHistoryEntity.toDomain(json: Json): SeasonRecord =
    json.decodeFromString(SeasonRecord.serializer(), recordJson)

fun SeasonRecord.toEntity(json: Json): SeasonHistoryEntity = SeasonHistoryEntity(
    season = season,
    // Record sem país é do mundo antigo, que era só o Brasil.
    countryCode = country?.code?.value ?: "BRA",
    recordJson = json.encodeToString(SeasonRecord.serializer(), this),
)
