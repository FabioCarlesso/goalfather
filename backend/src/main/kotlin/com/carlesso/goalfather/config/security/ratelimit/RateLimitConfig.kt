package com.carlesso.goalfather.config.security.ratelimit

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wiring dos limitadores de autenticação (issue #43).
 *
 * Dois beans do MESMO tipo [RateLimiter], resolvidos por nome no ponto de
 * injeção (via `@Qualifier` no AuthController). `@EnableConfigurationProperties`
 * ativa o binding tipado de [RateLimitProperties].
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties::class)
class RateLimitConfig {

    @Bean
    fun loginRateLimiter(props: RateLimitProperties): RateLimiter =
        RateLimiter(props.login.maxAttempts, props.login.window)

    @Bean
    fun registerRateLimiter(props: RateLimitProperties): RateLimiter =
        RateLimiter(props.register.maxAttempts, props.register.window)
}
