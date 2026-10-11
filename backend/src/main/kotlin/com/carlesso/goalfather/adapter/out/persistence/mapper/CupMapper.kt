package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.CupEntity
import com.carlesso.goalfather.domain.model.Cup
import kotlinx.serialization.json.Json

fun CupEntity.toDomain(json: Json): Cup = json.decodeFromString(Cup.serializer(), cupJson)

fun Cup.toEntity(json: Json): CupEntity = CupEntity(
    season = season,
    cupJson = json.encodeToString(Cup.serializer(), this),
)
