package com.carlesso.goalfather.adapter.`in`.web.ws

import org.springframework.http.server.ServerHttpRequest
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.support.DefaultHandshakeHandler
import java.security.Principal

/**
 * Promove o `userId` validado pelo [JwtHandshakeInterceptor] a `principal` da
 * `WebSocketSession` (issue #27). O Spring chama [determineUser] durante o
 * handshake; lemos o atributo já populado pelo interceptor.
 *
 * O `Principal { ... }` é uma conversão SAM do Kotlin: `Principal` tem um único
 * método (`getName`), então uma lambda satisfaz a interface — mais enxuto que
 * uma classe anônima Java.
 */
class UserIdHandshakeHandler : DefaultHandshakeHandler() {

    override fun determineUser(
        request: ServerHttpRequest,
        wsHandler: WebSocketHandler,
        attributes: Map<String, Any>,
    ): Principal? {
        val userId = attributes[JwtHandshakeInterceptor.USER_ID_ATTR] as? Long ?: return null
        return Principal { userId.toString() }
    }
}
