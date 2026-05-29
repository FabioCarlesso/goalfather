package com.carlesso.goalfather

import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

/**
 * Smoke test minimo: verifica que o contexto Spring Boot sobe sem erros.
 * Flyway aplica V1__init.sql; DataInitializer popula seed; todos os
 * beans (controllers, ports, adapters, WS) sao resolvidos.
 *
 * Se este teste passa, `./gradlew bootRun` tambem sobe.
 */
@SpringBootTest
class SmokeTest {
    @Test
    fun contextLoads() {
        // Sucesso = bean wiring inteiro funciona.
    }
}
