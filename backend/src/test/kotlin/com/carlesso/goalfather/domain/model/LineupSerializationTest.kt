package com.carlesso.goalfather.domain.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A escalação é persistida como JSON na coluna `clubs.lineup_json`, então a
 * postura (issue #56) chega ao banco sem migração — mas isso só é verdade
 * enquanto o par (encode, decode) se comportar como abaixo. Este teste é a
 * guarda: se alguém quebrar a compatibilidade do JSON, quebra aqui e não em
 * produção com o histórico de escalações já gravado.
 *
 * Usa a MESMA config do bean `json` de `SerializationConfig`.
 */
class LineupSerializationTest {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    private val player = Player(
        id = PlayerId(1),
        name = "P1",
        position = Position.MF,
        overall = 70,
        pace = 70,
        shooting = 70,
        passing = 70,
        defending = 70,
        salary = 10_000,
        age = 25,
    )

    @Test
    fun `postura sobrevive ao round-trip de JSON`() {
        val lineup = Lineup(
            players = listOf(player),
            formation = Formation.F_5_3_2,
            tactics = Tactics(Posture.DEFENSIVE),
        )

        val decoded = json.decodeFromString(
            Lineup.serializer(),
            json.encodeToString(Lineup.serializer(), lineup),
        )

        assertEquals(lineup, decoded)
        assertEquals(Posture.DEFENSIVE, decoded.tactics.posture)
    }

    @Test
    fun `escalacao gravada antes da issue 56 desserializa como EQUILIBRADA`() {
        // Exatamente o formato que já está no banco: sem o campo `tactics`.
        val legacy = """{"players":[],"formation":"4-4-2"}"""

        val decoded = json.decodeFromString(Lineup.serializer(), legacy)

        assertEquals(Posture.BALANCED, decoded.tactics.posture)
        assertEquals(Formation.F_4_4_2, decoded.formation)
    }
}
