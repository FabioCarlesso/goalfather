package com.carlesso.goalfather.adapter.`in`.web.ws

import com.carlesso.goalfather.application.port.`in`.PlayRoundUseCase
import com.carlesso.goalfather.domain.event.RoundEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 * Handler do WebSocket `/ws/round/{number}`. Sobe quando o cliente abre
 * a conexao, dispara o stream do `PlayRoundUseCase` e envia cada
 * `RoundEvent` como JSON.
 *
 * Pequenos delays entre frames (80ms por minuto) sao responsabilidade
 * DESTE adapter — o use case retorna o Flow inteiro de uma vez. Aqui
 * intercalamos delay proporcional ao minuto entre mensagens, mantendo
 * a sensacao de partida ao vivo.
 *
 * Ciclo de vida: SupervisorJob por sessao; cancelado em afterConnectionClosed.
 */
class RoundWebSocketHandler(
    private val playRound: PlayRoundUseCase,
    private val json: Json,
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(RoundWebSocketHandler::class.java)
    private val jobs = ConcurrentHashMap<String, Job>()

    companion object {
        const val MS_PER_MINUTE: Long = 80
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val number = extractRoundNumber(session)
        if (number == null) {
            session.close(CloseStatus.BAD_DATA.withReason("Rodada invalida na URL"))
            return
        }

        log.info("WS round connected: session={} round={}", session.id, number)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = scope.launch {
            try {
                var lastMinute = 0
                playRound.stream(number).collect { event ->
                    val delayMs = when (event) {
                        is RoundEvent.MatchUpdate -> {
                            val gap = (event.event.minute - lastMinute) * MS_PER_MINUTE
                            lastMinute = event.event.minute
                            gap.coerceAtLeast(0)
                        }
                        // Sinais de encerramento (rodada/temporada): pequeno gap
                        // para o cliente processar os últimos FullTime antes deles.
                        is RoundEvent.RoundFinished -> 50L
                        is RoundEvent.SeasonFinished -> 50L
                    }
                    if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
                    if (session.isOpen) {
                        val payload = json.encodeToString(RoundEvent.serializer(), event)
                        session.sendMessage(TextMessage(payload))
                    }
                }
                if (session.isOpen) {
                    kotlinx.coroutines.delay(50)
                    session.close(CloseStatus.NORMAL.withReason("Rodada encerrada"))
                }
            } catch (e: IllegalArgumentException) {
                log.warn("Round invalida: {}", e.message)
                session.close(CloseStatus.SERVER_ERROR.withReason(e.message ?: "Erro"))
            } catch (e: Exception) {
                log.error("Erro streaming round", e)
                if (session.isOpen) session.close(CloseStatus.SERVER_ERROR)
            }
        }
        jobs[session.id] = job
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        jobs.remove(session.id)?.cancel()
        log.info("WS round closed: session={} status={}", session.id, status)
    }

    private fun extractRoundNumber(session: WebSocketSession): Int? {
        // URI no formato /ws/round/{n}
        val path = session.uri?.path ?: return null
        val regex = Regex("/ws/round/(\\d+)")
        val match = regex.find(path) ?: return null
        return match.groupValues[1].toIntOrNull()
    }
}
