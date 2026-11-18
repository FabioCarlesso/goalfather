package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
 * Seleções (issue #134) contra o mundo do pack de teste: trava a forma JSON
 * de `NationalTeamsOverview` e `ClubCallUps` do contrato.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NationalTeamMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: Json

    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response
        else response

    private fun token(): String {
        val body = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"nt-${System.nanoTime()}","password":"secret123"}"""
        }.andReturn().await().contentAsString
        return json.decodeFromString(AuthResponse.serializer(), body).token
    }

    private fun get(path: String, token: String): MockHttpServletResponse =
        mockMvc.get(path) { header("Authorization", "Bearer $token") }.andReturn().await()

    @Test
    fun `painel das selecoes com as datas FIFA e as Eliminatorias`() {
        val response = get("/api/national-teams", token())
        assertEquals(200, response.status, response.contentAsString)
        val body = json.parseToJsonElement(response.contentAsString).jsonObject

        val windows = body.getValue("windows").jsonArray
        assertEquals(8, windows.size)
        val first = windows.first().jsonObject
        // O ano depende do encaixe corrente do banco compartilhado (outros testes
        // gravam encaixes "lá na frente"); a primeira janela é sempre a de março.
        assertTrue(Regex("""\d{4}-03-\d{2}""").matches(first.getValue("start").jsonPrimitive.content))
        assertEquals(2, first.getValue("matchSlots").jsonPrimitive.int)
        val qualifiers = body.getValue("qualifiers").jsonObject
        assertEquals(18, qualifiers.getValue("totalRounds").jsonPrimitive.int)
        val rows = qualifiers.getValue("rows").jsonArray
        assertEquals(10, rows.size)
        assertTrue(rows.all { it.jsonObject.getValue("country").jsonObject.getValue("flag").jsonPrimitive.content.isNotEmpty() })
        assertEquals(1, (rows.first() as JsonObject).getValue("position").jsonPrimitive.int)
    }

    @Test
    fun `convocados do clube e 404 para clube inexistente`() {
        val token = token()
        val response = get("/api/clubs/1/call-ups", token)
        assertEquals(200, response.status, response.contentAsString)
        val body = json.parseToJsonElement(response.contentAsString).jsonObject
        assertTrue("window" in body && "players" in body)
        for (p in body.getValue("players").jsonArray) {
            assertEquals(1, p.jsonObject.getValue("clubId").jsonPrimitive.int)
            assertEquals(3, p.jsonObject.getValue("country").jsonObject.getValue("code").jsonPrimitive.content.length)
        }

        val missing = get("/api/clubs/999999/call-ups", token)
        assertEquals(404, missing.status)
        assertTrue("CLUB_NOT_FOUND" in missing.contentAsString)
    }
}
