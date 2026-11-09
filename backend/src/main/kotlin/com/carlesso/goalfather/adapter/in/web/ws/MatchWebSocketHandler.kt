package com.carlesso.goalfather.adapter.`in`.web.ws

import com.carlesso.goalfather.application.port.`in`.StreamMatchUseCase
import com.carlesso.goalfather.domain.event.MatchEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler do WebSocket `/ws/matches/{id}` — drill-down de uma única partida.
 * Espelha o `RoundWebSocketHandler`, mas emite `MatchEvent` cru (sem o
 * envelope `RoundEvent`), igual ao handler MSW `matchStream` do frontend.
 *
 * O delay por minuto (sensação de "ao vivo") é responsabilidade DESTE
 * adapter; o use case devolve o Flow sem timing. O ritmo vem do handshake
 * (`?speed=`, issue #115 — ver [StreamSpeed]); ausente ou inválido → `NORMAL`
 * (80ms/minuto). Aqui "pular para o fim" é só reconectar com `INSTANT`: o
 * drill-down não grava nada, e fechar a conexão cancela o job desta sessão.
 */
class MatchWebSocketHandler(
    private val streamMatch: StreamMatchUseCase,
    private val json: Json,
    // Espera entre frames injetável, como no RoundWebSocketHandler.
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(MatchWebSocketHandler::class.java)
    private val jobs = ConcurrentHashMap<String, Job>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val matchId = extractMatchId(session)
        if (matchId == null) {
            session.close(CloseStatus.BAD_DATA.withReason("Partida invalida na URL"))
            return
        }

        val speed = session.streamSpeed()
        log.info("WS match connected: session={} match={} speed={}", session.id, matchId, speed)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = scope.launch {
            try {
                var lastMinute = 0
                streamMatch.stream(matchId).collect { event ->
                    val gap = speed.gapMs(lastMinute, event.minute)
                    lastMinute = event.minute
                    if (gap > 0) pause(gap)
                    if (session.isOpen) {
                        val payload = json.encodeToString(MatchEvent.serializer(), event)
                        session.sendMessage(TextMessage(payload))
                    }
                }
                if (session.isOpen) {
                    delay(50)
                    session.close(CloseStatus.NORMAL.withReason("Partida encerrada"))
                }
            } catch (e: CancellationException) {
                // Aba fechada → `afterConnectionClosed` cancela o job. É o fim
                // normal do drill-down (que não grava nada), não um erro: relança
                // sem logar ERROR. Antes do `Exception` genérico, que o engoliria.
                throw e
            } catch (e: IllegalArgumentException) {
                log.warn("Partida invalida: {}", e.message)
                session.close(CloseStatus.SERVER_ERROR.withReason(e.message ?: "Erro"))
            } catch (e: Exception) {
                log.error("Erro streaming match", e)
                if (session.isOpen) session.close(CloseStatus.SERVER_ERROR)
            }
        }
        jobs[session.id] = job
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        jobs.remove(session.id)?.cancel()
        log.info("WS match closed: session={} status={}", session.id, status)
    }

    private fun extractMatchId(session: WebSocketSession): Long? {
        val path = session.uri?.path ?: return null
        val match = Regex("/ws/matches/(\\d+)").find(path) ?: return null
        return match.groupValues[1].toLongOrNull()
    }
}
