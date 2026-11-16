package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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
 * Onboarding país → liga → clube (issue #131) contra o mundo REAL montado do
 * pack reduzido (`data/world/e2e/v1`, o do `application.yml` de teste): 6+6
 * clubes do Brasil e 4 da Premier League. Trava o formato do contrato
 * (`WorldCountry`, `WorldLeague`, `AvailableClub`).
 *
 * Banco PRÓPRIO: os outros testes reivindicam clubes no `goalfather-test`
 * compartilhado, e as contagens de clubes livres daqui seriam instáveis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:goalfather-onboarding;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"])
class WorldOnboardingMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: Json

    /** Ver nota em [AuthSecurityMvcTest]: controllers suspend exigem asyncDispatch. */
    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response
        else response

    private fun register(username: String): String {
        val body = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$username","password":"secret123"}"""
        }.andReturn().await().contentAsString
        return json.decodeFromString(AuthResponse.serializer(), body).token
    }

    private fun get(path: String, token: String): JsonArray {
        val response = mockMvc.get(path) { header("Authorization", "Bearer $token") }.andReturn().await()
        assertEquals(200, response.status, response.contentAsString)
        return json.parseToJsonElement(response.contentAsString).jsonArray
    }

    @Test
    fun `paises do onboarding com as ligas de cada um`() {
        val token = register("onb-${System.nanoTime()}")

        val countries = get("/api/world/countries", token)

        assertEquals(listOf("Brasil", "Inglaterra"), countries.map { it.jsonObject.getValue("country").jsonObject.getValue("name").jsonPrimitive.content })
        val brazil = countries.first().jsonObject
        assertEquals("BRA", brazil.getValue("country").jsonObject.getValue("code").jsonPrimitive.content)
        val leagues = brazil.getValue("leagues").jsonArray.map { it.jsonObject }
        assertEquals(listOf("bra-serie-a", "bra-serie-b"), leagues.map { it.getValue("id").jsonPrimitive.content })
        assertEquals(listOf(1, 2), leagues.map { it.getValue("tier").jsonPrimitive.int })
        assertEquals(listOf(6, 6), leagues.map { it.getValue("clubCount").jsonPrimitive.int })
        assertEquals("Campeonato Brasileiro Série A", leagues.first().getValue("name").jsonPrimitive.content)
        assertEquals("2026", leagues.first().getValue("seasonLabel").jsonPrimitive.content)

        val premier = countries[1].jsonObject.getValue("leagues").jsonArray.single().jsonObject
        assertEquals("eng-premier-league", premier.getValue("id").jsonPrimitive.content)
        assertEquals(4, premier.getValue("clubCount").jsonPrimitive.int)
        assertEquals("2026/27", premier.getValue("seasonLabel").jsonPrimitive.content)
    }

    @Test
    fun `clubes livres de uma liga com pais e liga, por forca media desc`() {
        val token = register("onb-clubs-${System.nanoTime()}")

        val clubs = get("/api/clubs/available?competitionId=eng-premier-league", token).map { it.jsonObject }

        assertEquals(4, clubs.size)
        clubs.forEach {
            assertEquals("eng-premier-league", it.getValue("competitionId").jsonPrimitive.content)
            assertEquals("Premier League", it.getValue("competitionName").jsonPrimitive.content)
            assertEquals("ENG", it.getValue("country").jsonObject.getValue("code").jsonPrimitive.content)
            assertEquals(1, it.getValue("division").jsonPrimitive.int)
            assertTrue(it.getValue("cash").jsonPrimitive.long > 0)
        }
        val strengths = clubs.map { it.getValue("averageOverall").jsonPrimitive.int }
        assertEquals(strengths.sortedDescending(), strengths)

        // Sem filtro: os livres de todas as ligas (o teste do claim pode ter levado um).
        val freeByLeague = get("/api/world/countries", token)
            .flatMap { it.jsonObject.getValue("leagues").jsonArray }
            .sumOf { it.jsonObject.getValue("availableClubCount").jsonPrimitive.int }
        assertEquals(freeByLeague, get("/api/clubs/available", token).size)
        assertTrue(freeByLeague >= 15)
        assertEquals(0, get("/api/clubs/available?competitionId=xyz-liga", token).size)
    }

    @Test
    fun `clube reivindicado sai da lista e da contagem de livres`() {
        val token = register("onb-claim-${System.nanoTime()}")
        val before = get("/api/clubs/available?competitionId=bra-serie-b", token)
        val chosen = before.first().jsonObject.getValue("id").jsonPrimitive.long

        val claim = mockMvc.post("/api/clubs/$chosen/claim") { header("Authorization", "Bearer $token") }.andReturn().await()
        assertEquals(200, claim.status)

        val after = get("/api/clubs/available?competitionId=bra-serie-b", token)
        assertEquals(before.size - 1, after.size)
        val serieB = get("/api/world/countries", token).first().jsonObject.getValue("leagues").jsonArray[1].jsonObject
        assertEquals(after.size, serieB.getValue("availableClubCount").jsonPrimitive.int)

        // Outro técnico não leva o mesmo clube (multiplayer: cada um escolhe um livre).
        val rival = register("onb-rival-${System.nanoTime()}")
        val stolen = mockMvc.post("/api/clubs/$chosen/claim") { header("Authorization", "Bearer $rival") }.andReturn().await()
        assertEquals(409, stolen.status)
    }
}
