package com.carlesso.goalfather.config.security

import com.carlesso.goalfather.config.security.JwtSecretGuard.Companion.DEFAULT_DEV_SECRET
import com.carlesso.goalfather.config.security.JwtSecretGuard.Companion.isSecretAllowed
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regra de fail-fast do segredo JWT (issue #30). Testa a função pura — sem
 * subir contexto Spring.
 */
class JwtSecretGuardTest {

    private val customSecret = "uma-chave-bem-aleatoria-com-mais-de-32-bytes-xyz"

    @Test
    fun `default e permitido sem profile (bootRun local)`() {
        assertTrue(isSecretAllowed(emptySet(), DEFAULT_DEV_SECRET))
    }

    @Test
    fun `default e permitido em dev ou test`() {
        assertTrue(isSecretAllowed(setOf("dev"), DEFAULT_DEV_SECRET))
        assertTrue(isSecretAllowed(setOf("test"), DEFAULT_DEV_SECRET))
        assertTrue(isSecretAllowed(setOf("prod", "dev"), DEFAULT_DEV_SECRET))
    }

    @Test
    fun `default e barrado em profile de implantacao`() {
        assertFalse(isSecretAllowed(setOf("prod"), DEFAULT_DEV_SECRET))
        assertFalse(isSecretAllowed(setOf("staging"), DEFAULT_DEV_SECRET))
    }

    @Test
    fun `segredo customizado e sempre permitido`() {
        assertTrue(isSecretAllowed(emptySet(), customSecret))
        assertTrue(isSecretAllowed(setOf("prod"), customSecret))
        assertTrue(isSecretAllowed(setOf("dev"), customSecret))
    }

    /**
     * O profile `postgres` (issue #67) só escolhe o datasource — não diz nada
     * sobre o ambiente ser seguro. É o que o stack do docker-compose usa junto
     * de `dev`: o `dev` é que autoriza o segredo default, não o `postgres`.
     */
    @Test
    fun `postgres sozinho nao autoriza o segredo default`() {
        assertFalse(isSecretAllowed(setOf("postgres"), DEFAULT_DEV_SECRET))
        assertFalse(isSecretAllowed(setOf("prod", "postgres"), DEFAULT_DEV_SECRET))
    }

    @Test
    fun `stack local dev+postgres aceita o segredo default`() {
        assertTrue(isSecretAllowed(setOf("dev", "postgres"), DEFAULT_DEV_SECRET))
    }
}
