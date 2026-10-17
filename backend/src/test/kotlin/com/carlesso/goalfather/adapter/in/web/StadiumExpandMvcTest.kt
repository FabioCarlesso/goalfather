package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ampliação do estádio pela borda HTTP (issue #79): pedido abaixo do mínimo
 * (incluindo negativo) responde 400 `INVALID_STADIUM_EXPANSION` e não mexe em
 * caixa nem capacidade — antes virava caixa grátis ou 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StadiumExpandMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: Json

    /** Ver nota em [AuthSecurityMvcTest]: controllers suspend exigem asyncDispatch. */
    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response
        else response

    /** Cadastra um técnico e reivindica um clube livre; devolve (token, clubId). */
    private fun managerWithClub(): Pair<String, Long> {
        val auth = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"mvc-stadium-${System.nanoTime() % 1_000_000}","password":"secret123"}"""
        }.andReturn().await().contentAsString
        val token = json.decodeFromString(AuthResponse.serializer(), auth).token

        val available = mockMvc.get("/api/clubs/available") { header("Authorization", "Bearer $token") }
            .andReturn().await().contentAsString
        val clubId = json.parseToJsonElement(available).jsonArray.first().jsonObject.getValue("id").jsonPrimitive.long
        mockMvc.post("/api/clubs/$clubId/claim") { header("Authorization", "Bearer $token") }.andReturn().await()
        return token to clubId
    }

    private fun club(token: String, clubId: Long): JsonObject {
        val response = mockMvc.get("/api/clubs/$clubId") { header("Authorization", "Bearer $token") }
            .andReturn().await()
        assertEquals(200, response.status, response.contentAsString)
        return json.parseToJsonElement(response.contentAsString).jsonObject
    }

    private fun expand(token: String, clubId: Long, seats: Int): MockHttpServletResponse =
        mockMvc.post("/api/clubs/$clubId/stadium/expand") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"additionalSeats":$seats}"""
        }.andReturn().await()

    @Test
    fun `ampliacao negativa responde 400 e nao altera caixa nem capacidade`() {
        val (token, clubId) = managerWithClub()
        val before = club(token, clubId)

        for (seats in listOf(-10_000, -4_000, 0, 999)) {
            val response = expand(token, clubId, seats)
            assertEquals(400, response.status, "seats=$seats → ${response.contentAsString}")
            val error = json.parseToJsonElement(response.contentAsString).jsonObject
            assertEquals("INVALID_STADIUM_EXPANSION", error.getValue("code").jsonPrimitive.content)
        }

        val after = club(token, clubId)
        assertEquals(before.getValue("cash").jsonPrimitive.long, after.getValue("cash").jsonPrimitive.long)
        assertEquals(
            before.getValue("stadiumCapacity").jsonPrimitive.int,
            after.getValue("stadiumCapacity").jsonPrimitive.int,
        )
    }
}
