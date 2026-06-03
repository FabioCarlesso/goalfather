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

    suspend fun save(club: Club): Club
}
