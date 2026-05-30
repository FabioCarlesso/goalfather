package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.PlayerEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PositionEnum
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position

fun PlayerEntity.toDomain(): Player = Player(
    id = PlayerId(id),
    name = name,
    position = position.toDomain(),
    overall = overall,
    pace = pace,
    shooting = shooting,
    passing = passing,
    defending = defending,
    stamina = stamina,
    salary = salary,
    age = age,
    goals = goals,
    yellowCards = yellowCards,
    redCards = redCards,
    injured = injured,
)

fun Player.toEntity(clubId: Long? = null): PlayerEntity = PlayerEntity(
    id = id.value,
    clubId = clubId,
    name = name,
    position = position.toEntity(),
    overall = overall,
    pace = pace,
    shooting = shooting,
    passing = passing,
    defending = defending,
    stamina = stamina,
    salary = salary,
    age = age,
    goals = goals,
    yellowCards = yellowCards,
    redCards = redCards,
    injured = injured,
)

private fun PositionEnum.toDomain(): Position = when (this) {
    PositionEnum.GK -> Position.GK
    PositionEnum.CB -> Position.CB
    PositionEnum.MF -> Position.MF
    PositionEnum.FW -> Position.FW
}

private fun Position.toEntity(): PositionEnum = when (this) {
    Position.GK -> PositionEnum.GK
    Position.CB -> PositionEnum.CB
    Position.MF -> PositionEnum.MF
    Position.FW -> PositionEnum.FW
}
