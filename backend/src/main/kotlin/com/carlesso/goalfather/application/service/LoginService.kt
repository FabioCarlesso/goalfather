package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.LoginUseCase
import com.carlesso.goalfather.application.port.out.PasswordHasher
import com.carlesso.goalfather.application.port.out.UserRepository
import com.carlesso.goalfather.domain.result.LoginResult

/**
 * Login: verifica senha contra o hash. Username inexistente e senha errada
 * devolvem a MESMA variante [LoginResult.InvalidCredentials] — não vazamos
 * qual dos dois falhou (evita enumeração de usuários).
 */
class LoginService(
    private val userRepo: UserRepository,
    private val passwordHasher: PasswordHasher,
) : LoginUseCase {

    override suspend fun execute(username: String, rawPassword: String): LoginResult {
        val user = userRepo.findByUsername(username)
            ?: return LoginResult.InvalidCredentials
        return if (passwordHasher.matches(rawPassword, user.passwordHash)) {
            LoginResult.Success(user)
        } else {
            LoginResult.InvalidCredentials
        }
    }
}
