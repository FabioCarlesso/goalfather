package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.ClubEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PlayerEntity
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Lineup
import kotlinx.serialization.json.Json

fun ClubEntity.toDomain(squadEntities: List<PlayerEntity>, json: Json): Club = Club(
    id = ClubId(id),
    name = name,
    cash = cash,
    stadiumCapacity = stadiumCapacity,
    squad = squadEntities.map { it.toDomain() },
    lineup = lineupJson?.let { json.decodeFromString(Lineup.serializer(), it) },
    ownerId = ownerId,
)

fun Club.toEntity(json: Json): ClubEntity = ClubEntity(
    id = id.value,
    name = name,
    cash = cash,
    stadiumCapacity = stadiumCapacity,
    lineupJson = lineup?.let { json.encodeToString(Lineup.serializer(), it) },
    ownerId = ownerId,
)
