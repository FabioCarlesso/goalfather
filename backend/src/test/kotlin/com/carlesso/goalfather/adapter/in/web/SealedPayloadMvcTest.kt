package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Trava o discriminador `type` dos `sealed` que saem por handlers
 * `ResponseEntity<Any>` (follow-up da review do PR #78).
 *
 * Com o corpo declarado como `Any`, o Spring não acha serializer do kotlinx
 * para `Object` e cai no Jackson, que ignora `@JsonClassDiscriminator`: o
 * `availability` de cada jogador do `ClubDto` e o `TransferResultDto` do
 * mercado saíam sem o `type` que o `oneOf` do contrato exige — e o frontend
 * decide "lesionado ou apto" e "compra ok ou não" exatamente por ele.
 * Asserts sobre o JSON CRU, como [LeagueStandingsMvcTest].
 */
@SpringBootTest
@AutoConfigureMockMvc
class SealedPayloadMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: Json

    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response
        else response

    private fun parse(response: MockHttpServletResponse): JsonObject {
        assertEquals(200, response.status, response.contentAsString)
        return json.parseToJsonElement(response.contentAsString).jsonObject
    }

    /** Cadastra um técnico e reivindica um clube livre; devolve (token, clubId). */
    private fun managerWithClub(): Pair<String, Long> {
        val auth = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"mvc-sealed-${System.nanoTime() % 1_000_000}","password":"secret123"}"""
        }.andReturn().await().contentAsString
        val token = json.decodeFromString(AuthResponse.serializer(), auth).token

        val available = mockMvc.get("/api/clubs/available") { header("Authorization", "Bearer $token") }
            .andReturn().await().contentAsString
        val clubId = json.parseToJsonElement(available).jsonArray.first().jsonObject.getValue("id").jsonPrimitive.long
        mockMvc.post("/api/clubs/$clubId/claim") { header("Authorization", "Bearer $token") }.andReturn().await()
        return token to clubId
    }

    private fun assertSquadDiscriminated(club: JsonObject) {
        val types = club.getValue("squad").jsonArray.map {
            it.jsonObject.getValue("availability").jsonObject["type"]?.jsonPrimitive?.content
        }
        assertTrue(types.isNotEmpty() && types.all { it == "Available" || it == "Injured" }, "availability sem type: $types")
    }

    @Test
    fun `clube expoe availability com o discriminador do contrato`() {
        val (token, clubId) = managerWithClub()
        assertSquadDiscriminated(parse(mockMvc.get("/api/clubs/$clubId") { header("Authorization", "Bearer $token") }.andReturn().await()))
    }

    @Test
    fun `comandos que devolvem o clube tambem mantem o discriminador`() {
        val (token, clubId) = managerWithClub()
        val updated = mockMvc.put("/api/clubs/$clubId/ticket-price") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"ticketPriceCents":6000}"""
        }.andReturn().await()
        assertSquadDiscriminated(parse(updated))
    }

    @Test
    fun `venda devolve TransferResult com type e o clube discriminado`() {
        val (token, clubId) = managerWithClub()
        val club = parse(mockMvc.get("/api/clubs/$clubId") { header("Authorization", "Bearer $token") }.andReturn().await())
        val playerId = club.getValue("squad").jsonArray.first().jsonObject.getValue("id").jsonPrimitive.long

        val result = parse(
            mockMvc.post("/api/market/sell") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"clubId":$clubId,"playerId":$playerId}"""
            }.andReturn().await(),
        )
        assertEquals("Success", result["type"]?.jsonPrimitive?.content, "TransferResult sem type: $result")
        assertSquadDiscriminated(result.getValue("club").jsonObject)
    }
}
