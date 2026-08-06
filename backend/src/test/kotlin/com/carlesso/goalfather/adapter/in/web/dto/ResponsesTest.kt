package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.Posture
import com.carlesso.goalfather.domain.model.Tactics
import com.carlesso.goalfather.test.makeClub
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O JSON que sai de `/api/clubs/{id}` tem de ser o que `contract/openapi.yaml`
 * descreve — não o que o domínio Kotlin acha conveniente.
 *
 * Estes testes existem porque a divergência anterior era INVISÍVEL: o E2E roda
 * contra o MSW, que implementa o contrato, então nada no CI comparava a
 * resposta real com o spec. Aqui a comparação é sobre o JSON literal, campo a
 * campo — se alguém devolver o domínio direto de novo, quebra aqui.
 *
 * Usa a MESMA config do bean `json` de `SerializationConfig`.
 */
class ResponsesTest {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    private fun encode(dto: ClubDto): JsonObject =
        json.parseToJsonElement(json.encodeToString(ClubDto.serializer(), dto)).jsonObject

    private val club = makeClub(squadSize = 12, ownerId = 7L)

    @Test
    fun `lineup sai como playerIds e posture achatada, nao como o domínio`() {
        // O domínio guarda `players` (jogadores inteiros) e `tactics.posture`;
        // o contrato fala `playerIds` e `posture`. Este teste é a fronteira.
        val withLineup = club.copy(
            lineup = Lineup(
                players = club.squad.take(11),
                formation = Formation.F_5_3_2,
                tactics = Tactics(Posture.DEFENSIVE),
            ),
        )

        val lineup = encode(withLineup.toDto()).getValue("lineup").jsonObject

        assertEquals("5-3-2", lineup.getValue("formation").jsonPrimitive.content)
        assertEquals("DEFENSIVE", lineup.getValue("posture").jsonPrimitive.content)
        assertEquals(
            club.squad.take(11).map { it.id.value },
            lineup.getValue("playerIds").jsonArray.map { it.jsonPrimitive.content.toLong() },
        )
        // Os nomes do domínio NÃO podem vazar.
        assertNull(lineup["players"], "`players` é do domínio; o contrato fala `playerIds`")
        assertNull(lineup["tactics"], "`tactics` é do domínio; o contrato acha a postura na raiz")
    }

    @Test
    fun `clube sem escalacao salva omite lineup`() {
        assertNull(encode(club.copy(lineup = null).toDto())["lineup"])
    }

    @Test
    fun `star viaja no JSON mesmo sendo derivado no dominio`() {
        // `Player.star` é computed property e kotlinx NÃO serializa getters —
        // por isso o campo existia no contrato, a UI o lia, e nunca chegava.
        val squad = club.squad.mapIndexed { i, p -> p.copy(overall = if (i == 0) 90 else 60) }

        val players = encode(club.copy(squad = squad).toDto()).getValue("squad").jsonArray

        assertTrue(players.first().jsonObject.getValue("star").jsonPrimitive.content.toBoolean())
        assertTrue(players.all { "star" in it.jsonObject }, "todo jogador precisa carregar `star`")
    }

    @Test
    fun `player carrega os campos que o contrato exige`() {
        val player = encode(club.toDto()).getValue("squad").jsonArray.first().jsonObject

        val required = listOf(
            "id", "name", "position", "overall", "pace", "shooting", "passing",
            "defending", "stamina", "salary", "age", "goals", "yellowCards",
            "redCards", "availability",
        )
        for (field in required) {
            assertTrue(field in player, "campo obrigatório do contrato ausente: $field")
        }
    }

    @Test
    fun `availability preserva o discriminador do contrato`() {
        val injured = club.squad.first().copy(availability = Availability.Injured(3))
        val squad = listOf(injured) + club.squad.drop(1)

        val availability = encode(club.copy(squad = squad).toDto())
            .getValue("squad").jsonArray.first().jsonObject
            .getValue("availability").jsonObject

        assertEquals("Injured", availability.getValue("type").jsonPrimitive.content)
        assertEquals(3, availability.getValue("roundsOut").jsonPrimitive.content.toInt())
    }

    @Test
    fun `club nao vaza campos fora do contrato`() {
        // `division` é estado de domínio que o schema `Club` não declara; sai
        // pelo endpoint próprio (`/available`, via AvailableClubDto).
        val encoded = encode(
            club.copy(
                lineup = Lineup(players = club.squad.take(11), formation = Formation.F_4_4_2),
            ).toDto(),
        )

        assertEquals(
            setOf("id", "name", "cash", "stadiumCapacity", "squad", "lineup", "ownerId"),
            encoded.keys,
        )
    }
}
