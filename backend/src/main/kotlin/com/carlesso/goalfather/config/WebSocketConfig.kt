package com.carlesso.goalfather.config

import com.carlesso.goalfather.adapter.`in`.web.ws.JwtHandshakeInterceptor
import com.carlesso.goalfather.adapter.`in`.web.ws.MatchWebSocketHandler
import com.carlesso.goalfather.adapter.`in`.web.ws.RoundWebSocketHandler
import com.carlesso.goalfather.adapter.`in`.web.ws.UserIdHandshakeHandler
import com.carlesso.goalfather.application.port.`in`.PlayRoundUseCase
import com.carlesso.goalfather.application.port.`in`.StreamMatchUseCase
import com.carlesso.goalfather.config.security.JwtService
import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * Registra o handler do WS de rodada em `/ws/round/{number}`.
 *
 * `setAllowedOrigins("*")` permite o frontend Vite dev conectar; em
 * produção, restringir aos hosts reais (paralelo ao CorsConfig REST).
 *
 * Autenticação (issue #27): o handshake passa pelo [JwtHandshakeInterceptor]
 * (valida o JWT do query param `?token=`) e pelo [UserIdHandshakeHandler]
 * (promove o `userId` a `principal`). Sem token válido, a conexão é rejeitada
 * com 401 antes de chegar ao handler — por isso o `permitAll` das rotas `/ws`
 * no `SecurityConfig` deixa de ser um buraco aberto.
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val playRound: PlayRoundUseCase,
    private val streamMatch: StreamMatchUseCase,
    private val json: Json,
    private val jwt: JwtService,
) : WebSocketConfigurer {

    @Bean
    fun roundHandler(): RoundWebSocketHandler = RoundWebSocketHandler(playRound, json)

    @Bean
    fun matchHandler(): MatchWebSocketHandler = MatchWebSocketHandler(streamMatch, json)

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(roundHandler(), "/ws/round/**")
            .addInterceptors(JwtHandshakeInterceptor(jwt))
            .setHandshakeHandler(UserIdHandshakeHandler())
            .setAllowedOriginPatterns("*")
        registry.addHandler(matchHandler(), "/ws/matches/**")
            .addInterceptors(JwtHandshakeInterceptor(jwt))
            .setHandshakeHandler(UserIdHandshakeHandler())
            .setAllowedOriginPatterns("*")
    }
}
