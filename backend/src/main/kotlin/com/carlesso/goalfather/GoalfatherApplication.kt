package com.carlesso.goalfather

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Bootstrap do Spring Boot.
 *
 * Pacotes scaneados automaticamente: tudo abaixo de `com.carlesso.goalfather`.
 * Convencoes de camada (clean architecture):
 * - domain           — puro, sem Spring
 * - application      — ports + services; bean wiring em config/BeanConfig
 * - adapter/in/web   — controllers REST + WS
 * - adapter/out/persistence — JPA entities + adapters dos ports
 * - config           — beans, CORS, WebSocket registry
 */
@SpringBootApplication
class GoalfatherApplication

fun main(args: Array<String>) {
    runApplication<GoalfatherApplication>(*args)
}
