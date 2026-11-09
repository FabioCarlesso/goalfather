package com.carlesso.goalfather.adapter.`in`.web.ws

import com.carlesso.goalfather.application.port.`in`.StreamMatchUseCase
import com.carlesso.goalfather.domain.event.MatchEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.net.URI
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ritmo do WS de drill-down de partida (issue #115). */
class MatchWebSocketHandlerTest {

    private val json = Json { classDiscriminator = "type" }

    /** KickOff no 0' e mais 3 eventos a 30' de distância. */
    private val match: Flow<MatchEvent> = flow {
        for (minute in 0..90 step 30) {
            emit(MatchEvent.KickOff(minute, "Casa", "Fora", 70.0, 70.0))
        }
    }

    private data class Transmission(val pauses: List<Long>, val frames: List<String>)

    private fun transmit(query: String): Transmission {
        val pauses = Collections.synchronizedList(mutableListOf<Long>())
        val frames = Collections.synchronizedList(mutableListOf<String>())
        val closed = CountDownLatch(1)
        val session = mockk<WebSocketSession>(relaxed = true) {
            every { id } returns "m1"
            every { uri } returns URI("ws://localhost/ws/matches/7$query")
            every { isOpen } returns true
            every { sendMessage(any()) } answers { frames += firstArg<TextMessage>().payload }
            every { close(any()) } answers { closed.countDown() }
        }
        val useCase = object : StreamMatchUseCase {
            override fun stream(matchId: Long) = match
        }

        MatchWebSocketHandler(useCase, json, pause = { pauses += it }).afterConnectionEstablished(session)

        assertTrue(closed.await(5, TimeUnit.SECONDS), "o handler deveria encerrar a conexão")
        return Transmission(pauses.toList(), frames.toList())
    }

    @Test
    fun `speed ausente ou invalido - NORMAL`() {
        assertEquals(List(3) { 30 * 80L }, transmit("").pauses)
        assertEquals(List(3) { 30 * 80L }, transmit("?speed=lento").pauses)
    }

    @Test
    fun `speed valido - aplica o intervalo e INSTANT nao pausa`() {
        assertEquals(List(3) { 30 * 160L }, transmit("?speed=SLOW").pauses)
        assertEquals(List(3) { 30 * 20L }, transmit("?speed=FAST").pauses)
        assertEquals(emptyList(), transmit("?speed=INSTANT").pauses)
    }

    @Test
    fun `os frames sao identicos em qualquer velocidade`() {
        val normal = transmit("").frames
        assertEquals(4, normal.size)
        StreamSpeed.entries.forEach { assertEquals(normal, transmit("?speed=$it").frames, "em $it") }
    }
}
