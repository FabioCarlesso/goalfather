package com.carlesso.goalfather.config

import com.carlesso.goalfather.adapter.`in`.web.ws.RoundWebSocketHandler
import com.carlesso.goalfather.application.port.`in`.PlayRoundUseCase
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
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val playRound: PlayRoundUseCase,
    private val json: Json,
) : WebSocketConfigurer {

    @Bean
    fun roundHandler(): RoundWebSocketHandler = RoundWebSocketHandler(playRound, json)

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(roundHandler(), "/ws/round/**")
            .setAllowedOriginPatterns("*")
    }
}
