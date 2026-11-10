package com.carlesso.goalfather.adapter.`in`.web.ws

import io.mockk.every
import io.mockk.mockk
import org.springframework.web.socket.WebSocketSession
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

/** Leitura do `?speed=` do handshake (issue #115). */
class StreamSpeedTest {

    private fun sessionAt(uri: URI?): WebSocketSession = mockk { every { this@mockk.uri } returns uri }

    @Test
    fun `intervalos documentados no contrato`() {
        assertEquals(listOf(160L, 80L, 20L, 0L), StreamSpeed.entries.map { it.msPerMinute })
    }

    @Test
    fun `fromQuery - valores validos, qualquer caixa`() {
        assertEquals(StreamSpeed.SLOW, StreamSpeed.fromQuery("SLOW"))
        assertEquals(StreamSpeed.INSTANT, StreamSpeed.fromQuery("instant"))
        assertEquals(StreamSpeed.FAST, StreamSpeed.fromQuery("Fast"))
    }

    @Test
    fun `fromQuery - ausente, vazio ou invalido cai no NORMAL`() {
        listOf(null, "", "TURBO", "0", " NORMAL").forEach {
            assertEquals(StreamSpeed.NORMAL, StreamSpeed.fromQuery(it), "valor: '$it'")
        }
    }

    @Test
    fun `gapMs - proporcional ao salto de minutos e nunca negativo`() {
        assertEquals(800, StreamSpeed.NORMAL.gapMs(10, 20))
        assertEquals(0, StreamSpeed.INSTANT.gapMs(0, 90))
        assertEquals(0, StreamSpeed.SLOW.gapMs(45, 30))
    }

    @Test
    fun `streamSpeed le o query param da sessao`() {
        assertEquals(StreamSpeed.FAST, sessionAt(URI("ws://h/ws/round/3?token=abc&speed=FAST")).streamSpeed())
        assertEquals(StreamSpeed.NORMAL, sessionAt(URI("ws://h/ws/round/3?token=abc")).streamSpeed())
        assertEquals(StreamSpeed.NORMAL, sessionAt(null).streamSpeed())
    }
}
