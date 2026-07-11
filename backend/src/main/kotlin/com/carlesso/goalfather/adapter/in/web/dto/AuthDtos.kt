package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.User
import kotlinx.serialization.Serializable

/**
 * DTOs de auth (issue #18) e seleção de clube (issue #19). Nunca expõem o
 * `passwordHash` — `AuthUserDto` carrega só o que o frontend precisa.
 * `explicitNulls = false` (SerializationConfig) omite `clubId` quando nulo.
 */

@Serializable
data class RegisterRequest(val username: String, val password: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class AuthUserDto(
    val id: Long,
    val username: String,
    val clubId: Long? = null,
)

@Serializable
data class AuthResponse(val token: String, val user: AuthUserDto)

@Serializable
data class AvailableClubDto(
    val id: Long,
    val name: String,
    val cash: Long,
    val stadiumCapacity: Int,
    val averageOverall: Int,
    /** Divisão em que o clube disputa a temporada (issue #47) — orienta a escolha. */
    val division: Int,
)

fun User.toDto(): AuthUserDto = AuthUserDto(
    id = id.value,
    username = username,
    clubId = clubId?.value,
)

/** Resumo de um clube disponível — média do `overall` do elenco (0 se vazio). */
fun Club.toAvailableDto(): AvailableClubDto = AvailableClubDto(
    id = id.value,
    name = name,
    cash = cash,
    stadiumCapacity = stadiumCapacity,
    averageOverall = if (squad.isEmpty()) 0 else squad.sumOf { it.overall } / squad.size,
    division = division.value,
)
