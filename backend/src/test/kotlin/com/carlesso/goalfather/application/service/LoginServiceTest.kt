package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.PasswordHasher
import com.carlesso.goalfather.application.port.out.UserRepository
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.LoginResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class LoginServiceTest {

    private val userRepo: UserRepository = mockk()
    private val hasher = object : PasswordHasher {
        override fun hash(raw: String) = "hash:$raw"
        override fun matches(raw: String, hash: String) = hash == "hash:$raw"
    }
    private val service = LoginService(userRepo, hasher)

    private val stored = User(UserId(1), "fabio", "hash:secret123", clubId = null)

    @Test
    fun `senha correta retorna Success`() = runTest {
        coEvery { userRepo.findByUsername("fabio") } returns stored

        val result = service.execute("fabio", "secret123")

        assertIs<LoginResult.Success>(result)
    }

    @Test
    fun `senha errada retorna InvalidCredentials`() = runTest {
        coEvery { userRepo.findByUsername("fabio") } returns stored

        val result = service.execute("fabio", "errada")

        assertIs<LoginResult.InvalidCredentials>(result)
    }

    @Test
    fun `usuario inexistente retorna InvalidCredentials (sem vazar qual falhou)`() = runTest {
        coEvery { userRepo.findByUsername("ninguem") } returns null

        val result = service.execute("ninguem", "qualquer")

        assertIs<LoginResult.InvalidCredentials>(result)
    }
}
