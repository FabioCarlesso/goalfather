package com.carlesso.goalfather.adapter.`in`.web.ws

import com.carlesso.goalfather.config.security.JwtService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.util.UriComponentsBuilder

/**
 * Autentica o handshake do WebSocket (issue #27).
 *
 * O browser não envia o header `Authorization` ao abrir um WebSocket, então o
 * JWT viaja em query param `?token=<jwt>`. Validamos antes de a conexão subir:
 * token ausente, expirado ou com assinatura inválida → HTTP 401 e handshake
 * rejeitado (`return false`), nunca chegando ao handler.
 *
 * O `userId` extraído é guardado em [attributes], que o Spring copia para
 * `WebSocketSession.attributes`. Combinado ao [UserIdHandshakeHandler], também
 * vira o `principal` da sessão — disponível para autorização por recurso.
 */
class JwtHandshakeInterceptor(
    private val jwt: JwtService,
) : HandshakeInterceptor {

    private val log = LoggerFactory.getLogger(JwtHandshakeInterceptor::class.java)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val token = UriComponentsBuilder.fromUri(request.uri).build()
            .queryParams.getFirst("token")
        val userId = token?.let { jwt.parseUserId(it) }
        if (userId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            log.warn("WS handshake rejeitado: token ausente ou inválido ({})", request.uri.path)
            return false
        }
        attributes[USER_ID_ATTR] = userId
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) {
        // Nada a fazer pós-handshake.
    }

    companion object {
        /** Chave em `WebSocketSession.attributes` que carrega o `userId` (Long). */
        const val USER_ID_ATTR = "userId"
    }
}
