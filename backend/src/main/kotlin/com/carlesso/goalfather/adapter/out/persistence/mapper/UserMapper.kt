package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.UserEntity
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId

fun UserEntity.toDomain(): User = User(
    id = UserId(id),
    username = username,
    passwordHash = passwordHash,
    clubId = clubId?.let { ClubId(it) },
)

fun User.toEntity(): UserEntity = UserEntity(
    // id = 0 sinaliza insert (IDENTITY gera o real); !=0 atualiza a linha.
    id = id.value,
    username = username,
    passwordHash = passwordHash,
    clubId = clubId?.value,
)
