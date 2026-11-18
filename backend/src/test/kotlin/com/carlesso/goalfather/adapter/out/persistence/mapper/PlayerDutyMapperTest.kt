package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.serialization.LocalDateSerializer
import com.carlesso.goalfather.test.makePlayer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Convocação pelas colunas `duty_country`/`duty_until` (issue #134) e no JSON da API. */
class PlayerDutyMapperTest {

    private val duty = Availability.OnInternationalDuty(CountryCode("BRA"), LocalDate.of(2026, 3, 31))

    @Test
    fun `convocado sobrevive a ida e volta e apto grava colunas nulas`() {
        val called = makePlayer(1, availability = duty)
        val entity = called.toEntity(clubId = 1)
        assertEquals("BRA", entity.dutyCountry)
        assertEquals(0, entity.injuredForRounds)
        assertEquals(called, entity.toDomain())

        val fit = makePlayer(2).toEntity(clubId = 1)
        assertNull(fit.dutyCountry)
        assertNull(fit.dutyUntil)
    }

    @Test
    fun `codigo de pais corrompido le como apto em vez de derrubar o elenco`() {
        val entity = makePlayer(1, availability = duty).toEntity(clubId = 1).apply { dutyCountry = "br" }
        assertEquals(Availability.Available, entity.toDomain().availability)
    }

    @Test
    fun `no JSON a convocacao sai com o discriminador e a data ISO do contrato`() {
        val json = Json { classDiscriminator = "type" }
        assertEquals(
            """{"type":"OnInternationalDuty","country":"BRA","until":"2026-03-31"}""",
            json.encodeToString(Availability.serializer(), duty),
        )
        assertEquals("\"2026-03-31\"", json.encodeToString(LocalDateSerializer, LocalDate.of(2026, 3, 31)))
    }
}
