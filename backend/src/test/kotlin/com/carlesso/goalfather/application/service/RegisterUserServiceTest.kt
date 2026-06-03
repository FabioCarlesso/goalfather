package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.PasswordHasher
import com.carlesso.goalfather.application.port.out.UserRepository
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.RegisterResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RegisterUserServiceTest {

    private val userRepo: UserRepository = mockk()

    /** Hasher fake determinístico — prefixa "hash:" em vez de rodar BCrypt. */
    private val hasher = object : PasswordHasher {
        override fun hash(raw: String) = "hash:$raw"
        override fun matches(raw: String, hash: String) = hash == "hash:$raw"
    }

    private val service = RegisterUserService(userRepo, hasher)

    @Test
    fun `cadastro novo grava hash e devolve usuario com id gerado`() = runTest {
        coEvery { userRepo.existsByUsername("fabio") } returns false
        val saved = slot<User>()
        coEvery { userRepo.save(capture(saved)) } answers { saved.captured.copy(id = UserId(42)) }

        val result = service.execute("fabio", "secret123")

        assertIs<RegisterResult.Success>(result)
        assertEquals(42, result.user.id.value)
        assertEquals("fabio", result.user.username)
        assertEquals("hash:secret123", saved.captured.passwordHash)
        assertEquals(null, result.user.clubId)
    }

    @Test
    fun `username em uso retorna UsernameTaken e nao persiste`() = runTest {
        coEvery { userRepo.existsByUsername("fabio") } returns true

        val result = service.execute("fabio", "secret123")

        assertIs<RegisterResult.UsernameTaken>(result)
        coVerify(exactly = 0) { userRepo.save(any()) }
    }
}
