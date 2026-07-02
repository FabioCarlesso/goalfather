package com.carlesso.goalfather.adapter.`in`.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cobre o rate limiting do register (issue #43). Usa um contexto dedicado com
 * limite baixo (`@TestPropertySource`) — o `application.yml` de teste afrouxa o
 * register para não interferir nos demais testes, então aqui apertamos de novo
 * num contexto isolado. A chave é por IP (127.0.0.1) e toda tentativa conta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "app.rate-limit.register.max-attempts=3",
        "app.rate-limit.register.window=1m",
    ],
)
class RegisterRateLimitMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc

    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response
        else response

    private fun register(username: String): MockHttpServletResponse =
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$username","password":"secret123"}"""
        }.andReturn().await()

    @Test
    fun `apos exceder o limite de cadastros o register retorna 429`() {
        val prefix = "reg-limit-${System.nanoTime()}"

        // 3 cadastros (usernames distintos) dentro do limite → 201.
        repeat(3) { assertEquals(201, register("$prefix-$it").status) }

        // 4º ultrapassa a janela → 429 com Retry-After, antes mesmo de validar.
        val blocked = register("$prefix-over")
        assertEquals(429, blocked.status)
        assertTrue(blocked.contentAsString.contains(""""code":"TOO_MANY_REQUESTS""""))
        assertTrue(blocked.getHeader(HttpHeaders.RETRY_AFTER) != null, "deve enviar Retry-After")
    }
}
