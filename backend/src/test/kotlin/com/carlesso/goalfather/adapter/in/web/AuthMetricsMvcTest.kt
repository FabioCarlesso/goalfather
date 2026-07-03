package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.application.metrics.GoalfatherMetrics
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifica que o counter de login por desfecho (issue #44) incrementa no
 * `MeterRegistry` ao exercitar o `AuthController`. Um login com credenciais
 * inválidas deve bater `goalfather.auth.logins{result="failure"}`.
 *
 * Não precisa de `@AutoConfigureObservability`: o counter é publicado no
 * `MeterRegistry` injetado (o `SimpleMeterRegistry` presente no teste), sem
 * depender do endpoint Prometheus.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthMetricsMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var registry: MeterRegistry

    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response
        else response

    @Test
    fun `login invalido incrementa o counter de falhas`() {
        val before = failureCount()

        val status = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            // Username único (usuário inexistente) evita colisão de rate-limit com outros testes.
            content = """{"username":"metrics-${System.nanoTime()}","password":"nope123"}"""
        }.andReturn().await().status

        assertEquals(401, status, "credenciais inválidas devem responder 401")
        assertEquals(before + 1.0, failureCount(), "counter de login com falha deve incrementar em 1")
    }

    private fun failureCount(): Double =
        registry.find(GoalfatherMetrics.AUTH_LOGINS)
            .tag(GoalfatherMetrics.TAG_RESULT, "failure")
            .counter()
            ?.count()
            ?: 0.0
}
