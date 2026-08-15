package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.SeasonRecord

/**
 * Port de saída do histórico de temporadas (issue #60). **Append-only**: só
 * existe escrita de criação — nem `save`, nem `delete`. História não é
 * corrigida; se um snapshot sair errado, o bug está em quem o monta.
 */
interface SeasonHistoryRepository {
    /** Temporadas encerradas, da mais recente para a mais antiga. */
    suspend fun findAll(): List<SeasonRecord>

    /** Snapshot de uma temporada, ou `null` se ela nunca foi encerrada. */
    suspend fun findBySeason(season: Int): SeasonRecord?

    /**
     * Grava o snapshot da temporada. `true` = gravou; `false` = a temporada já
     * tinha record e NADA foi alterado.
     *
     * O `false` não é erro: a virada de temporada já roda uma vez só (quem
     * vence o claim da rodada, issue #46), então o segundo chamador é sempre
     * um retardatário. A unicidade por `season` no banco é a rede de segurança
     * — com duas réplicas, quem perder a corrida colide na PK em vez de gravar
     * um segundo campeão.
     */
    suspend fun append(record: SeasonRecord): Boolean
}
