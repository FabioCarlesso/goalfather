package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Navegação pelo mundo (issue #133) contra o mundo do pack reduzido do E2E
 * (`data/world/e2e/v1`): Série A e B com 6 clubes, Copa do Brasil e Premier
 * League com 4. Banco próprio: os outros testes jogam encaixes no banco
 * compartilhado, e aqui o mundo precisa estar no começo. Trava o formato do contrato (`CompetitionSummary`,
 * `CompetitionDetail`, `CompetitionMatchday`, `ClubSchedule`) e os 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:goalfather-navigation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"])
class CompetitionMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: Json

    /** Ver nota em [AuthSecurityMvcTest]: controllers suspend exigem asyncDispatch. */
    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response
        else response

    private val token: String by lazy {
        val body = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"nav-${System.nanoTime()}","password":"secret123"}"""
        }.andReturn().await().contentAsString
        json.decodeFromString(AuthResponse.serializer(), body).token
    }

    private fun call(path: String): MockHttpServletResponse =
        mockMvc.get(path) { header("Authorization", "Bearer $token") }.andReturn().await()

    private fun ok(path: String): JsonElement {
        val response = call(path)
        assertEquals(200, response.status, response.contentAsString)
        return json.parseToJsonElement(response.contentAsString)
    }

    private fun errorCode(path: String, status: Int = 404): String {
        val response = call(path)
        assertEquals(status, response.status, response.contentAsString)
        return json.parseToJsonElement(response.contentAsString).jsonObject.getValue("code").jsonPrimitive.content
    }

    private fun JsonElement.str(key: String) = jsonObject.getValue(key).jsonPrimitive.content

    @Test
    fun `lista as competicoes do mundo com formato, papel e temporada`() {
        val list = ok("/api/competitions").jsonArray

        assertEquals(
            listOf("bra-serie-a", "bra-serie-b", "bra-copa-do-brasil", "eng-premier-league"),
            list.map { it.str("id") },
        )
        val cup = list[2]
        assertEquals("KNOCKOUT", cup.str("format"))
        assertEquals("NATIONAL_CUP", cup.str("kind"))
        assertEquals("BRA", cup.jsonObject.getValue("country").str("code"))
        assertEquals(12, cup.jsonObject.getValue("clubCount").jsonPrimitive.int)
        assertEquals("ROUND_ROBIN", list[0].str("format"))
        assertEquals(1, list[0].jsonObject.getValue("tier").jsonPrimitive.int)
        assertEquals("2026/27", list[3].str("seasonLabel"))
    }

    @Test
    fun `detalhe traz os desempates e a tabela vem da liga`() {
        val detail = ok("/api/competitions/bra-serie-a")
        assertEquals("POINTS", detail.jsonObject.getValue("tiebreakers").jsonArray.first().jsonPrimitive.content)
        assertEquals(9, detail.jsonObject.getValue("foreignPlayerLimit").jsonPrimitive.int)

        val table = ok("/api/competitions/bra-serie-a/standings")
        assertEquals("bra-serie-a", table.str("competitionId"))
        assertEquals(6, table.jsonObject.getValue("rows").jsonArray.size)
    }

    @Test
    fun `copa tem chave e nao tem tabela, liga o contrario`() {
        val bracket = ok("/api/competitions/bra-copa-do-brasil/bracket")
        assertEquals("bra-copa-do-brasil", bracket.str("competitionId"))
        assertEquals("STANDINGS_NOT_FOUND", errorCode("/api/competitions/bra-copa-do-brasil/standings"))
        assertEquals("BRACKET_NOT_FOUND", errorCode("/api/competitions/bra-serie-a/bracket"))
        assertEquals("COMPETITION_NOT_FOUND", errorCode("/api/competitions/xyz-liga"))
        assertEquals("COMPETITION_NOT_FOUND", errorCode("/api/competitions/xyz-liga/matches"))
    }

    @Test
    fun `jogos por encaixe so da competicao, com o stage sealed discriminado`() {
        val days = ok("/api/competitions/bra-serie-a/matches").jsonArray
        // Turno e returno com 6 clubes: 10 rodadas de 3 jogos.
        assertEquals(10, days.size)
        days.forEach { day ->
            val matches = day.jsonObject.getValue("matches").jsonArray
            assertEquals(3, matches.size)
            matches.forEach {
                assertEquals("bra-serie-a", it.str("competitionId"))
                assertEquals("League", it.jsonObject.getValue("stage").str("type"))
            }
        }
        val keys = days.map { it.jsonObject.getValue("matchday").str("key") }
        assertEquals(keys.sorted(), keys)

        // Mundo recém-criado: ninguém marcou, a artilharia vem vazia (e não 404).
        assertTrue(ok("/api/competitions/bra-serie-a/scorers").jsonArray.isEmpty())
    }

    @Test
    fun `calendario do clube por semanas, com data FIFA e validacao`() {
        val clubId = ok("/api/competitions/bra-serie-a/standings").jsonObject.getValue("rows").jsonArray
            .first().jsonObject.getValue("clubId").jsonPrimitive.content

        val schedule = ok("/api/clubs/$clubId/schedule?weeks=26")
        val weeks = schedule.jsonObject.getValue("weeks").jsonArray
        assertEquals(26, weeks.size)
        assertTrue(weeks.any { it.jsonObject.getValue("fifaWindow").jsonPrimitive.boolean }, "26 semanas desde janeiro passam por março")
        val matches = weeks.flatMap { it.jsonObject.getValue("matches").jsonArray }
        assertTrue(matches.isNotEmpty())
        matches.forEach { entry ->
            val match = entry.jsonObject.getValue("match")
            assertTrue(clubId in listOf(match.str("homeClubId"), match.str("awayClubId")))
        }
        assertEquals(8, ok("/api/clubs/$clubId/schedule").jsonObject.getValue("weeks").jsonArray.size)
        assertEquals("INVALID_PARAMETER", errorCode("/api/clubs/$clubId/schedule?weeks=0", 400))
        assertEquals("CLUB_NOT_FOUND", errorCode("/api/clubs/999999/schedule"))
    }
}
