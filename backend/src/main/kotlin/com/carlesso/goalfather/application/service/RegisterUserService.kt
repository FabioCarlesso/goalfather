package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.RegisterUserUseCase
import com.carlesso.goalfather.application.port.out.PasswordHasher
import com.carlesso.goalfather.application.port.out.UserRepository
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.RegisterResult

/**
 * Cadastro de usuário. Username único; senha guardada como hash BCrypt
 * (via [PasswordHasher]). O usuário nasce sem clube — `clubId = null`.
 */
class RegisterUserService(
    private val userRepo: UserRepository,
    private val passwordHasher: PasswordHasher,
) : RegisterUserUseCase {

    override suspend fun execute(username: String, rawPassword: String): RegisterResult {
        if (userRepo.existsByUsername(username)) {
            return RegisterResult.UsernameTaken(username)
        }
        // id = 0 é placeholder: o insert gera o id real (IDENTITY), devolvido no save.
        val toCreate = User(
            id = UserId(0),
            username = username,
            passwordHash = passwordHasher.hash(rawPassword),
            clubId = null,
        )
        return RegisterResult.Success(userRepo.save(toCreate))
    }
}
