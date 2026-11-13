package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Suspension
import com.carlesso.goalfather.test.makePlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Ida e volta da ficha disciplinar pela coluna `discipline_json` (issue #130). */
class PlayerDisciplineMapperTest {

    private val league = CompetitionId("liga-d1")
    private val cup = CompetitionId("copa")

    @Test
    fun `amarelos pendentes e suspensoes sobrevivem a ida e volta`() {
        val player = makePlayer(1).copy(
            pendingYellows = mapOf(league to 2),
            suspensions = listOf(Suspension(cup, 1)),
        )

        assertEquals(player, player.toEntity(clubId = 1).toDomain())
    }

    @Test
    fun `ficha limpa grava coluna nula e linha antiga sem coluna le ficha limpa`() {
        val entity = makePlayer(1).toEntity(clubId = 1)

        assertNull(entity.disciplineJson)
        val read = entity.toDomain()
        assertEquals(emptyMap(), read.pendingYellows)
        assertEquals(emptyList(), read.suspensions)
    }

    @Test
    fun `linha corrompida com gancho zerado ou repetido nao derruba a leitura`() {
        val entity = makePlayer(1).toEntity(clubId = 1).apply {
            disciplineJson = """{"pendingYellows":{"liga-d1":0},"suspensions":[""" +
                """{"competitionId":"copa","matchesLeft":1},{"competitionId":"copa","matchesLeft":2},""" +
                """{"competitionId":"liga-d1","matchesLeft":0}]}"""
        }

        val read = entity.toDomain()

        assertEquals(emptyMap(), read.pendingYellows)
        assertEquals(listOf(Suspension(cup, 1)), read.suspensions)
    }
}
