package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Employment
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.cents
import com.carlesso.goalfather.test.makePlayer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Contrato pelas colunas `contract_*` (issue #136) e a escalação antiga do `lineup_json`. */
class PlayerContractMapperTest {

    private val contract = Employment.UnderContract(
        clubId = ClubId(4),
        salary = 7_500_00.cents,
        start = LocalDate.of(2026, 1, 1),
        end = LocalDate.of(2028, 12, 31),
        releaseClause = 9_000_000_00.cents,
    )

    @Test
    fun `contrato sobrevive a ida e volta pelas colunas`() {
        val player = makePlayer(1).copy(employment = contract)
        val entity = player.toEntity(clubId = 4)

        assertEquals(LocalDate.of(2028, 12, 31), entity.contractEnd)
        assertEquals(7_500_00, entity.salary)
        assertEquals(9_000_000_00L, entity.releaseClause)
        assertEquals(player, entity.toDomain())
    }

    @Test
    fun `sem clube e livre - colunas do contrato nulas e salario zero`() {
        val entity = makePlayer(1).copy(employment = contract).toEntity(clubId = null)

        assertNull(entity.contractStart)
        assertNull(entity.contractEnd)
        assertEquals(0, entity.salary)
        assertEquals(Employment.FreeAgent, entity.toDomain().employment)
    }

    @Test
    fun `releaseFromClub zera o vinculo da linha`() {
        val entity = makePlayer(1).copy(employment = contract).toEntity(clubId = 4).apply { releaseFromClub() }

        assertNull(entity.clubId)
        assertNull(entity.releaseClause)
        assertEquals(Employment.FreeAgent, entity.toDomain().employment)
    }

    @Test
    fun `linha com clube e sem datas le com contrato padrao em vez de derrubar o elenco`() {
        val entity = makePlayer(1).toEntity(clubId = 4).apply {
            contractStart = null
            contractEnd = null
        }

        val read = entity.toDomain().contract
        assertEquals(ClubId(4), read?.clubId)
        assertEquals(LocalDate.of(2028, 12, 31), read?.end)
    }

    @Test
    fun `escalacao gravada antes dos contratos ainda desserializa`() {
        // Formato do `lineup_json` antes da #136: jogador com `salary`, sem `employment`.
        val legacy = """
            {"players":[{"id":7,"name":"Velho","position":"MF","overall":70,"pace":70,"shooting":70,
            "passing":70,"defending":70,"stamina":100,"salary":1000000,"age":25}],
            "formation":"4-4-2"}
        """.trimIndent()
        val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

        val lineup = decodeLineup(legacy, json)

        assertEquals(PlayerId(7), lineup.players.single().id)
        assertEquals(Employment.FreeAgent, lineup.players.single().employment)
        assertEquals(Money.ZERO, lineup.players.single().salary)
    }

    @Test
    fun `escalacao no formato novo faz ida e volta sem perder o contrato`() {
        val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
        val lineup = Lineup(players = listOf(makePlayer(1).copy(employment = contract)), formation = Formation.F_4_4_2)

        assertEquals(lineup, decodeLineup(json.encodeToString(Lineup.serializer(), lineup), json))
    }
}
