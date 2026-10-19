package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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
 * Trava o FORMATO do painel da diretoria (issue #62) — `GET
 * /api/clubs/{id}/board` — e a ausência de `dismissal` no `AuthUser` de quem
 * nunca foi demitido. Contra o seed real: o clube 1 nasce com meta.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BoardMvcTest {

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

    private fun get(path: String, token: String): MockHttpServletResponse =
        mockMvc.get(path) { header("Authorization", "Bearer $token") }.andReturn().await()

    @Test
    fun `painel traz meta, posicao, confianca e os derivados do dominio`() {
        val token = register("board-${System.nanoTime()}")

        val response = get("/api/clubs/1/board", token)

        assertEquals(200, response.status)
        val body = json.parseToJsonElement(response.contentAsString).jsonObject
        assertEquals(1L, body.getValue("clubId").jsonPrimitive.long)
        assertTrue(body.getValue("objective").jsonPrimitive.content in setOf("TITLE", "TOP_HALF", "MID_TABLE"))
        assertTrue(body.getValue("mood").jsonPrimitive.content in setOf("CONFIDENT", "WARY", "AT_RISK"))
        val size = body.getValue("divisionSize").jsonPrimitive.int
        assertTrue(body.getValue("currentPosition").jsonPrimitive.int in 1..size)
        assertTrue(body.getValue("targetPosition").jsonPrimitive.int in 1..size)
        // `onTarget` vem pronto do backend — a UI não recompara posições.
        assertEquals(
            body.getValue("currentPosition").jsonPrimitive.int <= body.getValue("targetPosition").jsonPrimitive.int,
            body.getValue("onTarget").jsonPrimitive.boolean,
        )
        assertTrue(body.getValue("confidence").jsonPrimitive.int in 0..100)
        assertTrue(body.getValue("bonusCents").jsonPrimitive.long > 0)
        assertEquals(25, body.getValue("firingConfidence").jsonPrimitive.int)
        for (field in listOf("season", "matchday")) assertTrue(field in body, "faltou $field")
    }

    @Test
    fun `clube inexistente responde 404`() {
        val token = register("board404-${System.nanoTime()}")

        val response = get("/api/clubs/999999/board", token)

        assertEquals(404, response.status)
        assertTrue("BOARD_NOT_FOUND" in response.contentAsString)
    }

    @Test
    fun `usuario nunca demitido nao carrega dismissal`() {
        val token = register("nodismissal-${System.nanoTime()}")

        val body = json.parseToJsonElement(get("/api/auth/me", token).contentAsString).jsonObject

        // `AuthUser` sai pelo Jackson (ver `okJson`): o nulo pode vir explícito.
        assertTrue(body["dismissal"] == null || body["dismissal"] is JsonNull)
    }
}
