package com.carlesso.goalfather.config.security

import com.carlesso.goalfather.application.port.out.PasswordHasher
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Cadeia de segurança (issue #18).
 *
 * - Stateless (JWT), CSRF desabilitado (sem cookies de sessão).
 * - Os endpoints de register/login ficam liberados; os demais sob `/api`
 *   exigem token. O `permitAll` das rotas `/ws` aqui não é um buraco aberto: o
 *   browser não manda `Authorization` no handshake, então a autenticação do
 *   WebSocket roda no `JwtHandshakeInterceptor` (token em `?token=`, issue #27),
 *   que rejeita o handshake com 401 antes de a conexão subir.
 * - 401 com corpo JSON via [RestAuthenticationEntryPoint].
 * - `BCryptPasswordEncoder` por trás do port [PasswordHasher], mantendo o
 *   Spring Security fora da camada `application`.
 *
 * Usa o DSL Kotlin de segurança (`invoke`/`authorizeHttpRequests { }`),
 * idiomático e mais legível que o builder encadeado do Java.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthenticationFilter,
    private val entryPoint: RestAuthenticationEntryPoint,
    @Value("\${app.cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
    private val allowedOrigins: List<String>,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            cors { }
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize(HttpMethod.POST, "/api/auth/register", permitAll)
                authorize(HttpMethod.POST, "/api/auth/login", permitAll)
                authorize("/ws/**", permitAll)
                authorize("/actuator/health", permitAll)
                authorize("/actuator/info", permitAll)
                // Scrape do Prometheus (issue #44): sem token, como health/info. O
                // scraper não manda Authorization; numa instância única atrás da
                // rede interna é o padrão. Se um dia expor publicamente, proteger
                // com basic-auth/rede é responsabilidade do deploy.
                authorize("/actuator/prometheus", permitAll)
                authorize("/api/**", authenticated)
                authorize(anyRequest, permitAll)
            }
            exceptionHandling { authenticationEntryPoint = entryPoint }
            httpBasic { disable() }
            formLogin { disable() }
            addFilterBefore<UsernamePasswordAuthenticationFilter>(jwtAuthFilter)
        }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun passwordHasher(encoder: PasswordEncoder): PasswordHasher = object : PasswordHasher {
        override fun hash(raw: String): String = encoder.encode(raw)
        override fun matches(raw: String, hash: String): Boolean = encoder.matches(raw, hash)
    }

    /**
     * CORS único usado pela cadeia de segurança (substitui o antigo
     * `WebMvcConfigurer`, evitando headers duplicados). Origens externalizadas
     * em `app.cors.allowed-origins`.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = this@SecurityConfig.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", config)
        }
    }
}
