package com.carlesso.goalfather.config.security

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Fail-fast: aborta o boot se o `app.jwt.secret` ainda for o default de
 * desenvolvimento fora de um ambiente dev/test (issue #30).
 *
 * O default em `application.yml` está versionado no repositório — logo é
 * público. Subir um ambiente real com ele significa que qualquer pessoa pode
 * forjar tokens válidos. Em vez de confiar na disciplina de lembrar de definir
 * `JWT_SECRET`, transformamos o esquecimento em falha barulhenta no startup.
 *
 * "Dev/test" = profile `dev`/`test` ativo OU nenhum profile ativo (o `bootRun`
 * local roda sem profile). Qualquer profile de implantação (ex.: `prod`) com o
 * segredo default derruba a aplicação antes de aceitar tráfego.
 *
 * A regra mora numa função pura no `companion object`, testável sem subir o
 * contexto Spring.
 */
@Component
class JwtSecretGuard(
    @Value("\${app.jwt.secret}") private val secret: String,
    private val environment: Environment,
) {
    @PostConstruct
    fun verify() {
        check(isSecretAllowed(environment.activeProfiles.toSet(), secret)) {
            val active = environment.activeProfiles.joinToString().ifEmpty { "default" }
            "app.jwt.secret está com o valor default de desenvolvimento no profile " +
                "'$active'. Defina JWT_SECRET (ex.: `openssl rand -base64 48`) antes " +
                "de subir fora de dev/test."
        }
    }

    companion object {
        /** Mesmo valor do default em `application.yml`; público por estar versionado. */
        const val DEFAULT_DEV_SECRET = "dev-secret-change-me-please-0123456789-abcdefghijklmnop"

        private val SAFE_PROFILES = setOf("dev", "test")

        /**
         * `true` quando o segredo é seguro de usar nos profiles dados. Um segredo
         * customizado é sempre aceito; o default só passa em dev/test (ou sem
         * profile, que é o `bootRun` local).
         */
        fun isSecretAllowed(activeProfiles: Set<String>, secret: String): Boolean =
            secret != DEFAULT_DEV_SECRET ||
                activeProfiles.isEmpty() ||
                activeProfiles.any { it in SAFE_PROFILES }
    }
}
