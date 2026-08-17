package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.entity.SeasonHistoryEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.SeasonHistoryJpaRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.SeasonRecord
import com.carlesso.goalfather.domain.model.SeasonStanding
import com.carlesso.goalfather.domain.model.StandingRow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cobre — sem Spring — a tradução que sustenta o append-only do histórico
 * (issue #60, achado da review do PR #76): a violação de PK que chega quando
 * duas réplicas passam juntas pelo `existsById` vira `false`, não exception.
 *
 * Mesmo padrão do [LeaguePersistenceAdapterTest]: a corrida real é
 * inerentemente não-determinística, então aqui a exceção é forçada por mock
 * para exercitar o `catch` de forma estável. Que o banco de fato LANÇA essa
 * exceção (em vez de sobrescrever a linha via `merge`) é o que o
 * [SeasonHistoryIntegrationTest] prova contra o H2.
 */
class SeasonHistoryPersistenceAdapterTest {

    private val historyRepo: SeasonHistoryJpaRepository = mockk()
    private val adapter = SeasonHistoryPersistenceAdapter(historyRepo, Json)

    private val record = SeasonRecord(
        season = 2026,
        champion = standing(),
        finalStandings = listOf(standing()),
    )

    private fun standing() = SeasonStanding(
        division = Division.FIRST,
        row = StandingRow(position = 1, clubId = ClubId(1), clubName = "C1", played = 10, points = 30),
        pointsPercentage = 100,
    )

    @Test
    fun `perdedor da corrida na PK recebe false em vez de exception`() = runBlocking {
        // Cenário de duas réplicas: o `existsById` não viu nada (a outra ainda
        // não commitou), então a gravação segue e é a PK que barra.
        every { historyRepo.existsById(2026) } returns false
        every { historyRepo.saveAndFlush(any()) } throws DataIntegrityViolationException("PK duplicada")

        assertFalse(adapter.append(record), "temporada já registrada por outra réplica")
    }

    @Test
    fun `temporada ja registrada nao chega a gravar`() = runBlocking {
        every { historyRepo.existsById(2026) } returns true

        assertFalse(adapter.append(record))
        verify(exactly = 0) { historyRepo.saveAndFlush(any()) }
    }

    @Test
    fun `temporada inedita grava e devolve true`() = runBlocking {
        every { historyRepo.existsById(2026) } returns false
        every { historyRepo.saveAndFlush(any()) } answers { firstArg<SeasonHistoryEntity>() }

        assertTrue(adapter.append(record))
        verify { historyRepo.saveAndFlush(any()) }
    }
}
