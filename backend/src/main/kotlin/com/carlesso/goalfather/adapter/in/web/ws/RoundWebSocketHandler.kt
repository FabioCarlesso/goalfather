package com.carlesso.goalfather.adapter.`in`.web.ws

import com.carlesso.goalfather.adapter.`in`.web.dto.RoundEventDto
import com.carlesso.goalfather.adapter.`in`.web.dto.toDto
import com.carlesso.goalfather.application.port.`in`.PlayRoundUseCase
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.model.Matchday
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

/**
 * Handler do WebSocket `/ws/slot/{key}` (issue #125; antes `/ws/round/{n}`),
 * em que `key` é o encaixe (`2026-W05-WEEKEND`). Sobe quando o cliente abre
 * a conexao, dispara o stream do `PlayRoundUseCase` e envia cada
 * `RoundEvent` como JSON — sempre via `RoundEventDto` (issue #72): o domínio
 * não cruza a fronteira web, nem por REST nem por WebSocket.
 *
 * Pequenos delays entre frames (80ms por minuto por padrão) sao
 * responsabilidade DESTE adapter — o use case retorna o Flow inteiro de uma
 * vez. Aqui intercalamos delay proporcional ao minuto entre mensagens,
 * mantendo a sensacao de partida ao vivo.
 *
 * Velocidade (issue #115): o cliente escolhe o ritmo no handshake
 * (`?speed=SLOW|NORMAL|FAST|INSTANT`, ver [StreamSpeed]); ausente ou inválido
 * → `NORMAL`. Como o stream é gerado POR CONEXÃO e é determinístico, a
 * velocidade de um técnico não afeta os outros, e os eventos são idênticos em
 * qualquer ritmo. "Pular para o fim" não tem mensagem própria: o cliente abre
 * outra conexão com `INSTANT` e só depois fecha a antiga (ver o contrato).
 *
 * Ciclo de vida: a coroutine da sessão NÃO é cancelada quando o cliente sai.
 * O stream é o que encerra a rodada (claim `finishRound` + efeitos, depois do
 * último FullTime); cancelá-lo no meio deixava a rodada `InProgress` até
 * alguém reconectar. Sem ninguém assistindo, o pacing perde o sentido: os
 * delays e os envios são pulados e a rodada termina em milissegundos.
 * Reconexões concorrentes continuam seguras — só um stream vence o claim.
 *
 * Prontidão (issue #151): encaixe ainda não liberado pelo `POST /round/play`
 * não é simulado. O use case devolve só `RoundNotStarted`, que vai ao cliente
 * como evento, seguido de close NORMAL com motivo [NOT_STARTED_REASON].
 */
class RoundWebSocketHandler(
    private val playRound: PlayRoundUseCase,
    private val json: Json,
    // Ponto de injeção da espera entre frames: os testes trocam por um
    // gravador e verificam o ritmo sem dormir de verdade. Parâmetro com tipo
    // função `suspend` e valor default — o Spring segue chamando o construtor
    // de 2 argumentos, sem precisar de interface/bean só para isso.
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(RoundWebSocketHandler::class.java)

    companion object {
        /** Pausa antes dos sinais de encerramento, em qualquer velocidade. */
        const val END_SIGNAL_GAP_MS: Long = 50

        /** Motivo do close quando o encaixe ainda não foi liberado (issue #151). */
        const val NOT_STARTED_REASON = "Encaixe nao liberado"
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val matchday = extractMatchday(session)
        if (matchday == null) {
            session.close(CloseStatus.BAD_DATA.withReason("Encaixe invalido na URL"))
            return
        }

        val speed = session.streamSpeed()
        log.info("WS slot connected: session={} matchday={} speed={}", session.id, matchday.key, speed)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                var lastMinute = 0
                // `onEach` faz o envio de cada evento; `lastOrNull` é o operador
                // terminal que coleta o Flow e devolve o último — é dele que sai
                // o motivo do close, sem estado mutável compartilhado com o
                // cálculo do pacing.
                val last = playRound.stream(matchday).onEach { event ->
                    val delayMs = when (event) {
                        // Encaixe ainda `Scheduled` (issue #151): o gate de
                        // prontidão barrou. Não é erro do servidor — o cliente
                        // recebe o evento e um close NORMAL (1000), e volta a
                        // aguardar a prontidão. WARN porque o frontend oficial só
                        // abre o WS depois de um `play` com sucesso: chegar aqui
                        // indica cliente fora do fluxo (ou com bug).
                        RoundEvent.RoundNotStarted -> {
                            log.warn(
                                "WS slot rejeitado, encaixe nao liberado: session={} matchday={}",
                                session.id,
                                matchday.key,
                            )
                            0L
                        }
                        is RoundEvent.MatchUpdate -> {
                            val gap = speed.gapMs(lastMinute, event.event.minute)
                            lastMinute = event.event.minute
                            gap
                        }
                        // Sinais de encerramento (rodada/temporada): pequeno gap
                        // para o cliente processar os últimos FullTime antes
                        // deles — mantido mesmo em INSTANT (custa 100ms, não 1s).
                        is RoundEvent.RoundFinished, is RoundEvent.SeasonFinished -> END_SIGNAL_GAP_MS
                    }
                    // Pacing só para quem está assistindo: com a aba fechada, a
                    // rodada corre até o fim sem esperar (ver KDoc da classe).
                    if (delayMs > 0 && session.isOpen) pause(delayMs)
                    if (session.isOpen) {
                        val payload = json.encodeToString(RoundEventDto.serializer(), event.toDto())
                        session.sendMessage(TextMessage(payload))
                    }
                }.lastOrNull()

                if (session.isOpen) {
                    val reason = if (last == RoundEvent.RoundNotStarted) NOT_STARTED_REASON else "Rodada encerrada"
                    // Pelo `pause` injetado, como o resto do pacing: os testes
                    // enxergam essa espera em vez de dormir de verdade.
                    pause(END_SIGNAL_GAP_MS)
                    session.close(CloseStatus.NORMAL.withReason(reason))
                }
            } catch (e: CancellationException) {
                // Cancelamento é fluxo normal de coroutine (ex.: shutdown), não
                // erro: relança sem logar ERROR. Precisa vir antes do `Exception`
                // genérico, que o engoliria.
                throw e
            } catch (e: IllegalArgumentException) {
                log.warn("Round invalida: {}", e.message)
                session.close(CloseStatus.SERVER_ERROR.withReason(e.message ?: "Erro"))
            } catch (e: Exception) {
                log.error("Erro streaming round", e)
                if (session.isOpen) session.close(CloseStatus.SERVER_ERROR)
            }
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        // Sem cancel de propósito: a rodada termina no servidor (KDoc da classe).
        log.info("WS round closed: session={} status={}", session.id, status)
    }

    /**
     * URI no formato `/ws/slot/{key}`. O `parse` do domínio devolve `null`
     * para chave malformada ou semana que o ano não tem — URL é entrada
     * externa, e a ausência vira `BAD_DATA` no chamador.
     */
    private fun extractMatchday(session: WebSocketSession): Matchday? {
        val path = session.uri?.path ?: return null
        val key = path.substringAfter("/ws/slot/", missingDelimiterValue = "")
        return Matchday.parse(key)
    }
}
