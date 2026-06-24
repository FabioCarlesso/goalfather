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
}
