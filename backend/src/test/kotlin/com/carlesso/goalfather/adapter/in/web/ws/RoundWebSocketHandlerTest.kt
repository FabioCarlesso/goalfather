package com.carlesso.goalfather.adapter.`in`.web.ws

import com.carlesso.goalfather.application.port.`in`.PlayRoundUseCase
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.RoundEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.json.Json
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
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

    private fun session(open: AtomicBoolean): WebSocketSession = mockk(relaxed = true) {
        every { id } returns "s1"
        every { uri } returns URI("ws://localhost/ws/round/1")
        every { isOpen } answers { open.get() }
    }

    @Test
    fun `aba fechada no meio do stream - a rodada vai ate o fim no servidor, sem pacing`() {
        val completed = CountDownLatch(1)
        val useCase = object : PlayRoundUseCase {
            override fun stream(roundNumber: Int) = roundStream(completed)
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
}
