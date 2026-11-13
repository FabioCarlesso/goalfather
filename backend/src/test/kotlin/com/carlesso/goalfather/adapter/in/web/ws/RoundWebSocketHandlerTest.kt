package com.carlesso.goalfather.adapter.`in`.web.ws

import com.carlesso.goalfather.application.port.`in`.PlayRoundUseCase
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Retirement
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.test.makePlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.net.URI
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ciclo de vida do WS de rodada (revisão do PR #92). Fechar a aba no meio do
 * stream cancelava o job: a rodada nunca chegava ao claim `finishRound` e
 * ficava `InProgress` até alguém reconectar — e o cancelamento ainda era logado
 * como ERROR. Agora a rodada vai até o fim no servidor, sem pacing.
 */
class RoundWebSocketHandlerTest {

    private val json = Json { classDiscriminator = "type" }

    /** 10 eventos com 10 minutos entre eles: ~7s de pacing se alguém assistisse. */
    private fun roundStream(completed: CountDownLatch): Flow<RoundEvent> = flow {
        for (minute in 0..90 step 10) {
            emit(RoundEvent.MatchUpdate(1, MatchEvent.KickOff(minute, "Casa", "Fora", 70.0, 70.0)))
        }
        emit(RoundEvent.RoundFinished(standings = emptyList()))
    }.onCompletion { if (it == null) completed.countDown() }

    private fun session(open: AtomicBoolean, query: String = ""): WebSocketSession = mockk(relaxed = true) {
        every { id } returns "s1"
        every { uri } returns URI("ws://localhost/ws/slot/2026-W05-WEEKEND$query")
        every { isOpen } answers { open.get() }
    }

    /** Resultado de uma conexão inteira: pausas pedidas e frames enviados. */
    private data class Transmission(val pauses: List<Long>, val frames: List<String>)

    /**
     * Roda a rodada de [roundStream] numa sessão com [query] até o handler
     * fechar a conexão. A pausa é só gravada (sem dormir): o teste verifica o
     * ritmo pedido em vez de cronometrar — determinístico mesmo com a máquina
     * carregada.
     */
    private fun transmit(query: String): Transmission {
        val pauses = Collections.synchronizedList(mutableListOf<Long>())
        val frames = Collections.synchronizedList(mutableListOf<String>())
        val closed = CountDownLatch(1)
        val session = session(AtomicBoolean(true), query)
        every { session.sendMessage(any()) } answers { frames += (firstArg<TextMessage>()).payload }
        every { session.close(any()) } answers { closed.countDown() }
        val useCase = object : PlayRoundUseCase {
            override fun stream(matchday: Matchday) = roundStream(CountDownLatch(1))
        }

        RoundWebSocketHandler(useCase, json, pause = { pauses += it }).afterConnectionEstablished(session)

        assertTrue(closed.await(5, TimeUnit.SECONDS), "o handler deveria encerrar a conexão")
        return Transmission(pauses.toList(), frames.toList())
    }

    // 9 saltos de 10 minutos entre os 10 MatchUpdate + o gap fixo do
    // RoundFinished + o gap antes do close (também pelo `pause`).
    private fun expectedPauses(msPerMinute: Long) = List(9) { 10 * msPerMinute }.filter { it > 0 } + 50L + 50L

    @Test
    fun `speed ausente - ritmo NORMAL de 80ms por minuto`() {
        assertEquals(expectedPauses(80), transmit("").pauses)
    }

    @Test
    fun `speed valido - cada velocidade aplica o proprio intervalo`() {
        assertEquals(expectedPauses(160), transmit("?speed=SLOW").pauses)
        assertEquals(expectedPauses(20), transmit("?speed=FAST").pauses)
        // INSTANT: só sobram os gaps de encerramento (RoundFinished e close).
        assertEquals(listOf(50L, 50L), transmit("?token=x&speed=INSTANT").pauses)
    }

    @Test
    fun `speed invalido ou vazio - cai no NORMAL em vez de recusar o handshake`() {
        assertEquals(expectedPauses(80), transmit("?speed=TURBO").pauses)
        assertEquals(expectedPauses(80), transmit("?speed=").pauses)
    }

    @Test
    fun `os frames sao identicos em qualquer velocidade`() {
        val normal = transmit("?speed=NORMAL").frames
        assertEquals(11, normal.size)
        for (speed in StreamSpeed.entries) {
            assertEquals(normal, transmit("?speed=$speed").frames, "frames divergentes em $speed")
        }
    }

    @Test
    fun `INSTANT com espera real - a rodada inteira chega em menos de 1s`() {
        val closed = CountDownLatch(1)
        val frames = AtomicInteger()
        val session = session(AtomicBoolean(true), "?speed=INSTANT")
        every { session.sendMessage(any()) } answers { frames.incrementAndGet() }
        every { session.close(any()) } answers { closed.countDown() }
        val useCase = object : PlayRoundUseCase {
            override fun stream(matchday: Matchday) = roundStream(CountDownLatch(1))
        }

        val started = System.nanoTime()
        RoundWebSocketHandler(useCase, json).afterConnectionEstablished(session)

        assertTrue(closed.await(1, TimeUnit.SECONDS), "INSTANT deveria entregar a rodada em < 1s")
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000)
        assertEquals(11, frames.get())
    }

    @Test
    fun `aba fechada no meio do stream - a rodada vai ate o fim no servidor, sem pacing`() {
        val completed = CountDownLatch(1)
        val useCase = object : PlayRoundUseCase {
            override fun stream(matchday: Matchday) = roundStream(completed)
        }
        val handler = RoundWebSocketHandler(useCase, json)
        val open = AtomicBoolean(true)
        val session = session(open)

        handler.afterConnectionEstablished(session)
        open.set(false)
        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY)

        // Bem abaixo dos ~7s de pacing: com ninguém assistindo, não há espera.
        assertTrue(completed.await(2, TimeUnit.SECONDS), "o stream deveria terminar (e encerrar a rodada)")
        // Nada é enviado para uma sessão fechada (no máximo o 1º evento, sem
        // delay, pode sair antes de o teste fechar a aba).
        verify(atLeast = 0, atMost = 1) { session.sendMessage(any()) }
    }

    @Test
    fun `payload enviado passa pelo DTO - aposentadorias carregam star`() {
        // Issue #72: serializar o `RoundEvent` de domínio perdia `Player.star`
        // (computed property). Aqui o handler inteiro, não só o mapeamento.
        val sent = CountDownLatch(1)
        val frame = slot<TextMessage>()
        val event = RoundEvent.SeasonFinished(
            season = 2026,
            champion = StandingRow(position = 1, clubId = ClubId(1), clubName = "Alfa"),
            standings = emptyList(),
            retirements = listOf(Retirement(ClubId(1), makePlayer(10, overall = 90), makePlayer(11, overall = 50))),
        )
        val useCase = object : PlayRoundUseCase {
            override fun stream(matchday: Matchday) = flowOf<RoundEvent>(event)
        }
        val session = session(AtomicBoolean(true))
        every { session.sendMessage(capture(frame)) } answers { sent.countDown() }

        RoundWebSocketHandler(useCase, json).afterConnectionEstablished(session)

        assertTrue(sent.await(2, TimeUnit.SECONDS), "o SeasonFinished deveria ser enviado")
        val payload = json.parseToJsonElement(frame.captured.payload).jsonObject
        assertEquals("SeasonFinished", payload.getValue("type").jsonPrimitive.content)
        val retirement = payload.getValue("retirements").jsonArray.single().jsonObject
        assertEquals("true", retirement.getValue("retired").jsonObject.getValue("star").jsonPrimitive.content)
        assertEquals("false", retirement.getValue("promoted").jsonObject.getValue("star").jsonPrimitive.content)
    }

    @Test
    fun `encaixe nao liberado - envia RoundNotStarted e fecha NORMAL com motivo proprio`() {
        // Issue #151: o use case barra o encaixe `Scheduled` com um único evento;
        // o handler o repassa como `{"type":"RoundNotStarted"}` e fecha em 1000
        // (não é erro de servidor), sem pacing — só o gap antes do close.
        val closed = CountDownLatch(1)
        val frames = Collections.synchronizedList(mutableListOf<String>())
        val pauses = Collections.synchronizedList(mutableListOf<Long>())
        val closeStatus = slot<CloseStatus>()
        val useCase = object : PlayRoundUseCase {
            override fun stream(matchday: Matchday) = flowOf<RoundEvent>(RoundEvent.RoundNotStarted)
        }
        val session = session(AtomicBoolean(true))
        every { session.sendMessage(any()) } answers { frames += firstArg<TextMessage>().payload }
        every { session.close(capture(closeStatus)) } answers { closed.countDown() }

        RoundWebSocketHandler(useCase, json, pause = { pauses += it }).afterConnectionEstablished(session)

        assertTrue(closed.await(2, TimeUnit.SECONDS), "o handler deveria encerrar a conexão")
        assertEquals(listOf("""{"type":"RoundNotStarted"}"""), frames.toList())
        assertEquals(CloseStatus.NORMAL.code, closeStatus.captured.code)
        assertEquals(RoundWebSocketHandler.NOT_STARTED_REASON, closeStatus.captured.reason)
        assertEquals(listOf(RoundWebSocketHandler.END_SIGNAL_GAP_MS), pauses.toList())
    }
}
