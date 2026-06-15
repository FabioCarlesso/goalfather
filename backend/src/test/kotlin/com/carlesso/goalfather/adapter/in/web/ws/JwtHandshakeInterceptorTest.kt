package com.carlesso.goalfather.adapter.`in`.web.ws

import com.carlesso.goalfather.config.security.JwtService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.socket.WebSocketHandler
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testa o handshake de WS sem subir Spring: um [JwtService] real assina o token
 * e mocks de `ServerHttpRequest`/`ServerHttpResponse` simulam o handshake. Cobre
 * token válido (aceita + popula `userId`), ausente e inválido (rejeita com 401).
 */
class JwtHandshakeInterceptorTest {

    // Segredo ≥ 32 bytes exigido por HS256; expiração curta basta para o teste.
    private val jwt = JwtService(secret = "test-secret-com-pelo-menos-32-bytes!!", expirationMs = 60_000)
    private val interceptor = JwtHandshakeInterceptor(jwt)
    private val handler: WebSocketHandler = mockk()

    private fun handshake(uri: String, attributes: MutableMap<String, Any> = mutableMapOf()): Pair<Boolean, MutableMap<String, Any>> {
        val request: ServerHttpRequest = mockk { every { this@mockk.uri } returns URI.create(uri) }
        val response: ServerHttpResponse = mockk(relaxed = true)
        val ok = interceptor.beforeHandshake(request, response, handler, attributes)
        return ok to attributes
    }

    @Test
    fun `token valido aceita o handshake e popula o userId`() {
        val token = jwt.generate(userId = 42, username = "fabio")
        val (ok, attributes) = handshake("ws://localhost:8080/ws/round/1?token=$token")

        assertTrue(ok)
        assertEquals(42L, attributes[JwtHandshakeInterceptor.USER_ID_ATTR])
    }

    @Test
    fun `token ausente rejeita o handshake com 401`() {
        val request: ServerHttpRequest = mockk { every { uri } returns URI.create("ws://localhost:8080/ws/round/1") }
        val response: ServerHttpResponse = mockk(relaxed = true)

        val ok = interceptor.beforeHandshake(request, response, handler, mutableMapOf())

        assertFalse(ok)
        verify { response.setStatusCode(HttpStatus.UNAUTHORIZED) }
    }

    @Test
    fun `token invalido rejeita o handshake com 401`() {
        val request: ServerHttpRequest = mockk {
            every { uri } returns URI.create("ws://localhost:8080/ws/matches/1?token=nao-e-um-jwt")
        }
        val response: ServerHttpResponse = mockk(relaxed = true)

        val ok = interceptor.beforeHandshake(request, response, handler, mutableMapOf())

        assertFalse(ok)
        verify { response.setStatusCode(HttpStatus.UNAUTHORIZED) }
    }
}
