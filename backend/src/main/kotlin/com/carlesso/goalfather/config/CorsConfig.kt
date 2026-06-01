package com.carlesso.goalfather.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * CORS para o frontend consumir o backend.
 *
 * Origens externalizadas em `app.cors.allowed-origins` (lista separada por
 * vírgula) em vez de host fixo no código. O default cobre:
 *  - dev Vite:     http://localhost:5173
 *  - stack Docker: http://localhost:8080 — a UI é servida pelo nginx, que faz
 *    proxy de /api same-origin, mas o browser ainda manda `Origin` nos POST,
 *    então esse host precisa estar liberado senão o Spring devolve
 *    "Invalid CORS request".
 * Em produção, sobrescreva via env `APP_CORS_ALLOWED_ORIGINS`.
 */
@Configuration
class CorsConfig(
    @Value("\${app.cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
    private val allowedOrigins: List<String>,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(*allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}
