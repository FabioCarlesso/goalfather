package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Trava o FORMATO de `GET /api/league/cup` (issue #61) contra o contrato,
 * como [LeagueHistoryMvcTest] faz com o histórico: o endpoint devolve
 * `ResponseEntity<Any>`, então só em runtime se sabe o que sai no JSON.
 * Usa a copa de 2026 que o `DataInitializer` sorteia.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeagueCupMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: Json

    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response
        else response

    private fun token(): String {
        val body = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"mvc-cup-${System.nanoTime() % 1_000_000}","password":"secret123"}"""
        }.andReturn().await().contentAsString
        return json.decodeFromString(AuthResponse.serializer(), body).token
    }

    private fun get(path: String, token: String): MockHttpServletResponse =
        mockMvc.get(path) { header("Authorization", "Bearer $token") }.andReturn().await()

    @Test
    fun `cup devolve a chave com fases, premios e confrontos discriminados`() {
        val response = get("/api/league/cup?season=2026", token())
        assertEquals(200, response.status)
        val cup = json.parseToJsonElement(response.contentAsString).jsonObject

        assertEquals(2026, cup.getValue("season").jsonPrimitive.int)
        assertEquals("ROUND_OF_16", cup.getValue("currentPhase").jsonPrimitive.content)
        assertTrue("champion" !in cup, "copa em andamento omite o campeão")

        val phases = cup.getValue("phases").jsonArray.map { it.jsonObject }
        assertEquals(
            listOf("ROUND_OF_16", "QUARTER_FINALS", "SEMI_FINALS", "FINAL"),
            phases.map { it.getValue("phase").jsonPrimitive.content },
        )
        assertTrue(phases.all { it.getValue("prizeCents").jsonPrimitive.content.toLong() > 0 })

        val ties = cup.getValue("stages").jsonArray.single().jsonObject.getValue("ties").jsonArray.map { it.jsonObject }
        assertEquals(8, ties.size)
        val types = ties.map { it.getValue("type").jsonPrimitive.content }
        assertEquals(4, types.count { it == "Bye" })
        assertEquals(4, types.count { it == "Fixture" })
        // ClubId sai como número puro, e confronto em aberto não tem resultado.
        val fixture = ties.first { it.getValue("type").jsonPrimitive.content == "Fixture" }
        fixture.getValue("home").jsonObject.getValue("id").jsonPrimitive.int
        assertTrue("result" !in fixture && "winnerId" !in fixture)
    }

    @Test
    fun `temporada sem copa responde 404 CUP_NOT_FOUND`() {
        val response = get("/api/league/cup?season=9599", token())
        assertEquals(404, response.status)
        assertEquals(
            "CUP_NOT_FOUND",
            json.parseToJsonElement(response.contentAsString).jsonObject.getValue("code").jsonPrimitive.content,
        )
    }

    @Test
    fun `rodada corrente traz o stage com o discriminador do contrato`() {
        val response = get("/api/league/round/current", token())
        assertEquals(200, response.status)
        val stage = json.parseToJsonElement(response.contentAsString).jsonObject.getValue("stage").jsonObject
        // Sem o `type` o cliente não distingue rodada de liga de fase de copa —
        // é o que o Jackson entregaria para um `ResponseEntity<Any>`.
        assertTrue(stage.getValue("type").jsonPrimitive.content in setOf("League", "Cup"))
    }

    @Test
    fun `sem season devolve a copa mais recente`() {
        val response = get("/api/league/cup", token())
        assertEquals(200, response.status)
        assertTrue(json.parseToJsonElement(response.contentAsString).jsonObject.getValue("season").jsonPrimitive.int >= 2026)
    }
}
