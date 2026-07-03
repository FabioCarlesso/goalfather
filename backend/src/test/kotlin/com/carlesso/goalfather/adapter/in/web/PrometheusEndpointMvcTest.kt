package com.carlesso.goalfather.adapter.`in`.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Critério de aceite da issue #44: `GET /actuator/prometheus` responde 200 no
 * formato de scrape e já expõe as métricas customizadas `goalfather_*`. Os
 * timers de simulação são criados na construção dos beans (não dependem de uma
 * rodada ter sido jogada), então aparecem mesmo com contagem zero. O endpoint é
 * liberado sem token (ver SecurityConfig).
 *
 * `@AutoConfigureObservability` é necessário porque o Spring Boot DESLIGA os
 * registries de export (Prometheus incluso) em `@SpringBootTest` por padrão —
 * fora dele só o `SimpleMeterRegistry` sobe. A anotação reativa o registry
 * Prometheus e o endpoint de scrape apenas neste teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class PrometheusEndpointMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `prometheus expoe metricas customizadas sem token`() {
        val response = mockMvc.get("/actuator/prometheus").andReturn().response
        val body = response.contentAsString

        assertEquals(200, response.status)
        assertTrue(
            body.contains("goalfather_round_simulation_seconds"),
            "Timer da simulação de rodada deve aparecer no scrape",
        )
        assertTrue(
            body.contains("goalfather_match_simulation_seconds"),
            "Timer da simulação de partida deve aparecer no scrape",
        )
    }
}
