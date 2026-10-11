package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.Cup

/**
 * Port de saída da copa mata-mata (issue #61). Uma copa por temporada.
 *
 * Só quem venceu o claim da rodada (issue #46) grava — o sorteio na virada
 * de temporada e o avanço da chave a cada fase disputada —, então não há
 * corrida a arbitrar aqui: o `save` é uma substituição simples, sem lock
 * próprio. É a mesma garantia que já protege tabela e caixa.
 */
interface CupRepository {
    /** Copa de uma temporada, ou `null` se ela não teve copa. */
    suspend fun findBySeason(season: Int): Cup?

    /** Copa da temporada mais recente — a que está em disputa (ou acabou de acabar). */
    suspend fun findLatest(): Cup?

    /** Grava a copa da temporada (cria ou substitui a da mesma `season`). */
    suspend fun save(cup: Cup)
}
