package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.User

/**
 * Resultados de autenticação como valores (CLAUDE.md: erros de negócio são
 * `sealed`, não exceptions). O controller mapeia cada variante para o status
 * HTTP apropriado — `when` exaustivo, sem `else`.
 */
sealed interface RegisterResult {
    data class Success(val user: User) : RegisterResult
    data class UsernameTaken(val username: String) : RegisterResult
}

sealed interface LoginResult {
    data class Success(val user: User) : LoginResult
    data object InvalidCredentials : LoginResult
}
