package com.carlesso.goalfather.application.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Portão entre a FINALIZAÇÃO de um encaixe (claim `finishRound` + efeitos) e
 * quem precisa do estado parado para recriá-lo — o reset do profile e2e.
 *
 * A coroutine da rodada sobrevive ao cliente (ver `RoundWebSocketHandler`),
 * então a finalização de um teste pode estar gravando clubes, tabela e o
 * próximo encaixe no instante em que o teste seguinte apaga e semeia a liga.
 * O guard do `finish` (só `InProgress` termina) cobre o claim que chega
 * DEPOIS do reset; este portão cobre o que já estava no meio.
 *
 * Funciona como uma trava de leitura/escrita, só que para coroutines:
 * - [finalizing] pega UMA licença: várias finalizações (encaixes diferentes)
 *   correm juntas;
 * - [exclusively] pega TODAS: espera as que estão em curso terminarem e
 *   barra as novas até o bloco acabar.
 *
 * Por que não `ReentrantReadWriteLock`: a trava do JDK pertence à THREAD, e
 * uma função `suspend` pode voltar em outra thread depois de suspender —
 * soltar a trava de lá lança `IllegalMonitorStateException`. O `Semaphore` do
 * kotlinx não tem dono, e esperar por ele suspende em vez de bloquear.
 *
 * Fora do profile e2e ninguém chama [exclusively]: cada finalização só pega e
 * devolve uma licença livre, sem disputa.
 */
class RoundFinalizationGate(private val permits: Int = DEFAULT_PERMITS) {
    private val semaphore = Semaphore(permits)

    // Serializa os `exclusively`: dois juntando licenças ao mesmo tempo
    // ficariam cada um com uma parte, esperando o outro para sempre.
    private val exclusive = Mutex()

    suspend fun <T> finalizing(block: suspend () -> T): T = semaphore.withPermit { block() }

    suspend fun <T> exclusively(block: suspend () -> T): T = exclusive.withLock {
        repeat(permits) { semaphore.acquire() }
        try {
            block()
        } finally {
            repeat(permits) { semaphore.release() }
        }
    }

    companion object {
        /** Bem acima dos encaixes que finalizam juntos (um por slot). */
        const val DEFAULT_PERMITS: Int = 64
    }
}
