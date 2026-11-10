package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.PlayerEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PositionEnum
import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.Money
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
    salary = Money(salary.toLong()),
    age = age,
    goals = goals,
    yellowCards = yellowCards,
    redCards = redCards,
    availability = if (injuredForRounds >= 1) Availability.Injured(injuredForRounds)
    else Availability.Available,
    nationality = nationality.toCountry(),
    // `takeIf` descarta a segunda nacionalidade se ela coincidir com a primeira
    // depois de resolvidas (ex.: dois códigos desconhecidos caindo no default),
    // em vez de deixar o `require` do `Player` derrubar a leitura.
    secondNationality = secondNationality.toCountryOrNull()?.takeIf { it != nationality.toCountry() },
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
    salary = Math.toIntExact(salary.cents),
    age = age,
    goals = goals,
    yellowCards = yellowCards,
    redCards = redCards,
    // `when` exaustivo sobre o sealed: a soma de tipos vira o Int da coluna.
    injuredForRounds = when (val a = availability) {
        is Availability.Available -> 0
        is Availability.Injured -> a.roundsOut
    },
    nationality = nationality.code.value,
    secondNationality = secondNationality?.code?.value,
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
