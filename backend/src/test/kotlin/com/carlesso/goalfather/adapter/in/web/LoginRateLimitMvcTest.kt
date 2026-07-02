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
 * Cobre o rate limiting do login (issue #43) ponta a ponta via MockMvc. O
 * limite de teste é 5 (`src/test/resources/application.yml`). Roda com contexto
 * Spring completo, exercitando o `AuthController` e os beans `RateLimiter`
 * reais.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginRateLimitMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc

    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response
        else response

    /** Login com senha errada; `headers` permite forjar IP (X-Real-IP / XFF). */
    private fun login(
        username: String,
        password: String = "wrong-password",
        headers: Map<String, String> = emptyMap(),
    ): MockHttpServletResponse =
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$username","password":"$password"}"""
            headers.forEach { (k, v) -> header(k, v) }
        }.andReturn().await()

    private fun register(username: String, password: String = "secret123"): Int =
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$username","password":"$password"}"""
        }.andReturn().await().status

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
        val retryAfter = blocked.getHeader(HttpHeaders.RETRY_AFTER)
        assertTrue(retryAfter != null && retryAfter.toInt() > 0, "deve enviar Retry-After > 0")
    }

    @Test
    fun `login bem-sucedido zera o contador de tentativas`() {
        val username = "reset-${System.nanoTime()}"
        assertEquals(201, register(username), "usuário deve ser criado")

        // 4 falhas (abaixo do limite de 5), depois um login válido que zera.
        repeat(4) { assertEquals(401, login(username).status) }
        assertEquals(200, login(username, password = "secret123").status, "login correto deve passar")

        // Sem o reset, a 1ª falha aqui já seria a 5ª acumulada e a 2ª viraria
        // 429. Como zerou, duas novas falhas voltam a ser 401.
        assertEquals(401, login(username).status)
        assertEquals(401, login(username).status)
    }

    @Test
    fun `chave usa X-Real-IP e ignora X-Forwarded-For forjado`() {
        val username = "xff-${System.nanoTime()}"
        val ipA = mapOf("X-Real-IP" to "203.0.113.1")

        // Esgota o limite para o IP A.
        repeat(5) { assertEquals(401, login(username, headers = ipA).status) }
        assertEquals(429, login(username, headers = ipA).status)

        // Trocar o X-Forwarded-For (forjável) NÃO cria bucket novo: segue 429.
        val spoofed = ipA + ("X-Forwarded-For" to "1.2.3.4, 5.6.7.8")
        assertEquals(429, login(username, headers = spoofed).status, "XFF não deve contornar o limite")

        // Já um X-Real-IP diferente (setado pelo proxy) é outro bucket → 401.
        val ipB = mapOf("X-Real-IP" to "203.0.113.2")
        assertEquals(401, login(username, headers = ipB).status, "IP real diferente = janela independente")
    }
}
