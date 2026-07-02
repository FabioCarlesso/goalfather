package com.carlesso.goalfather.adapter.`in`.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cobre o rate limiting do login (issue #43) ponta a ponta via MockMvc: após
 * `max-attempts` logins inválidos na janela, o próximo devolve `429` com
 * `Retry-After`. Roda com contexto Spring completo, exercitando o
 * `AuthController` e os beans `RateLimiter` reais. O limite de teste é 5
 * (`src/test/resources/application.yml`).
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginRateLimitMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc

    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response
        else response

    private fun login(username: String): MockHttpServletResponse =
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$username","password":"wrong-password"}"""
        }.andReturn().await()

    @Test
    fun `apos exceder o limite de tentativas invalidas o login retorna 429`() {
        // Username único garante uma chave (IP+username) isolada dos outros testes.
        val username = "brute-${System.nanoTime()}"

        // As primeiras 5 tentativas inválidas respondem 401 normalmente.
        repeat(5) {
            assertEquals(401, login(username).status, "tentativa dentro do limite deve ser 401")
        }

        // A 6ª ultrapassa a janela → 429 com Retry-After e ErrorResponse.
        val blocked = login(username)
        assertEquals(429, blocked.status)
        assertTrue(blocked.contentAsString.contains(""""code":"TOO_MANY_REQUESTS""""))
        assertTrue(blocked.getHeader(HttpHeaders.RETRY_AFTER) != null, "deve enviar Retry-After")
    }
}
