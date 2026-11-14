package com.carlesso.goalfather.application.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RoundFinalizationGateTest {

    private val gate = RoundFinalizationGate(permits = 4)

    @Test
    fun `exclusively espera a finalizacao em curso e barra a seguinte`() = runTest {
        val log = mutableListOf<String>()
        val releaseFirst = CompletableDeferred<Unit>()

        launch { gate.finalizing { log += "fim-1 começa"; releaseFirst.await(); log += "fim-1 termina" } }
        runCurrent()
        launch { gate.exclusively { log += "reset" } }
        runCurrent()
        // O reset ainda espera a finalização 1; uma finalização nova pedida
        // agora também espera — o reset já está na fila das licenças.
        launch { gate.finalizing { log += "fim-2" } }
        runCurrent()
        assertEquals(listOf("fim-1 começa"), log)

        releaseFirst.complete(Unit)
        runCurrent()
        assertEquals(listOf("fim-1 começa", "fim-1 termina", "reset", "fim-2"), log)
    }

    @Test
    fun `finalizacoes correm juntas quando ninguem pede exclusividade`() = runTest {
        val inside = mutableListOf<Int>()
        val release = CompletableDeferred<Unit>()
        repeat(3) { i -> launch { gate.finalizing { inside += i; release.await() } } }
        runCurrent()
        assertEquals(listOf(0, 1, 2), inside)
        release.complete(Unit)
    }
}
