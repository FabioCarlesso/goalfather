package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.UserId
import java.time.Instant

/**
 * Port de saída para a prontidão de rodada (issue #20). A implementação JPA
 * vive em `adapter/out/persistence` sobre a tabela `round_readiness`; em
 * testes de unidade, um mock MockK.
 */
interface RoundReadinessRepository {
    /** Sinaliza o usuário como pronto para a rodada. Idempotente (PK composta). */
    suspend fun markReady(roundNumber: Int, userId: UserId)

    /** Ids dos usuários que já sinalizaram prontos para a rodada. */
    suspend fun readyUserIds(roundNumber: Int): Set<UserId>

    /**
     * Instante do PRIMEIRO "pronto" da rodada (menor `ready_at`), ou `null` se
     * ninguém sinalizou. Ancora o timeout do escape hatch (issue #45): o
     * cronômetro conta a partir deste instante.
     */
    suspend fun firstReadyAt(roundNumber: Int): Instant?

    /** Limpa as marcações da rodada — chamado após a simulação (reset). */
    suspend fun reset(roundNumber: Int)
}
