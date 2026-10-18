package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId

/**
 * Port de saída — define como o domínio acessa clubes. A implementação
 * vive em `adapter/out/persistence` (JPA/Postgres) ou em testes
 * (in-memory/MockK). O domínio não sabe nem se importa.
 *
 * Métodos `suspend` — abrem espaço para I/O assíncrono sem bloqueio
 * (coroutines), e tornam mocking idêntico ao real (MockK `coEvery`).
 */
interface ClubRepository {
    suspend fun findById(id: ClubId): Club?

    /** Todos os clubes da liga — usado para montar o calendário (round-robin). */
    suspend fun findAll(): List<Club>

    /** Clubes ainda sem dono (`ownerId == null`) — fluxo de seleção (issue #19). */
    suspend fun findAvailable(): List<Club>

    /**
     * Grava o clube sob lock otimista (issue #89): só aplica se o banco ainda
     * estiver na `club.version` que o chamador leu. Se outra escrita chegou
     * antes, NADA é gravado (nem o clube, nem o elenco) e o desfecho é
     * [ClubSaveResult.Conflict] — valor, não exception, para o use case
     * decidir entre devolver conflito ou reler e reaplicar.
     */
    suspend fun save(club: Club): ClubSaveResult
}

/**
 * Desfecho de [ClubRepository.save]. `sealed` deixa o `when` do chamador
 * exaustivo: esquecer o conflito vira erro de compilação, não lost update.
 */
sealed interface ClubSaveResult {
    /** Gravado; [club] já traz a versão nova — a próxima gravação parte dela. */
    data class Saved(val club: Club) : ClubSaveResult

    /** Outra escrita alterou o clube depois da leitura; nada foi gravado. */
    data class Conflict(val clubId: ClubId) : ClubSaveResult
}
