package com.carlesso.goalfather.test

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position

/**
 * Construtores compactos de domínio para testes — evita boilerplate
 * repetido em cada classe de teste sem inflar o domínio com helpers
 * que não são usados em produção.
 */
internal fun makePlayer(
    id: Long,
    position: Position = Position.MF,
    overall: Int = 75,
): Player = Player(
    id = PlayerId(id),
    name = "Player$id",
    position = position,
    overall = overall,
    pace = overall,
    shooting = overall,
    passing = overall,
    defending = overall,
    salary = 10_000,
    age = 25,
)

internal fun makeClub(
    id: Long = 1,
    name: String = "Test FC",
    cash: Long = 1_000_000_00,
    squadSize: Int = 11,
    overall: Int = 75,
    lineup: Lineup? = null,
    ownerId: Long? = null,
): Club = Club(
    id = ClubId(id),
    name = name,
    cash = cash,
    stadiumCapacity = 15_000,
    squad = (1L..squadSize.toLong()).map { makePlayer(it, overall = overall) },
    lineup = lineup,
    ownerId = ownerId,
)
