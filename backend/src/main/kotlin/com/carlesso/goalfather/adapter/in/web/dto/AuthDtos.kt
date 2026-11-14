package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.User
import kotlinx.serialization.Serializable

/**
 * DTOs de auth (issue #18). A seleção de clube (#19/#131) mora em `WorldDtos.kt`. Nunca expõem o
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
    /** Demissão mais recente (issue #62) — presente, a UI abre a tela de game over. */
    val dismissal: DismissalDto? = null,
)

@Serializable
data class AuthResponse(val token: String, val user: AuthUserDto)

fun User.toDto(): AuthUserDto = AuthUserDto(
    id = id.value,
    username = username,
    clubId = clubId?.value,
    dismissal = dismissal?.toDto(),
)
