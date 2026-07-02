package com.carlesso.goalfather.config.security.ratelimit

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuração do rate limiting de autenticação (issue #43).
 *
 * Usei `@ConfigurationProperties` como `data class` (em vez dos `@Value`
 * espalhados do resto do projeto) porque aqui há um GRUPO de propriedades
 * aninhadas: o binding tipado agrupa tudo num objeto imutável, valida na
 * subida e fica trivial de instanciar em teste — mais idiomático que quatro
 * `@Value` soltos. `Duration` é convertido pelo Spring a partir de valores
 * como `1m`, `60s` ou `PT1M`.
 *
 * - `login`: janela curta e apertada — mitiga brute force/credential stuffing
 *   por chave `IP + username`.
 * - `register`: janela mais longa e branda — throttle de criação de contas em
 *   massa por `IP` (ver [RateLimiter] e AuthController). Premissa: `1 IP ≈ 1
 *   cliente`. Atrás de NAT/CGNAT vários clientes legítimos compartilham o IP,
 *   por isso o default é folgado; ajuste via `AUTH_REGISTER_MAX_ATTEMPTS`.
 */
@ConfigurationProperties("app.rate-limit")
data class RateLimitProperties(
    val login: Rule = Rule(maxAttempts = 5, window = Duration.ofMinutes(1)),
    val register: Rule = Rule(maxAttempts = 20, window = Duration.ofHours(1)),
) {
    data class Rule(
        val maxAttempts: Int,
        val window: Duration,
    )
}
