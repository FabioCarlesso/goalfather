package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.cents
import com.carlesso.goalfather.test.makePlayer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Formato HTTP da renovação (issue #138): o `type` do `RenewalResult`, o 400
 * de termos malformados e o 403 de posse, além da persistência do contrato e
 * do esfriamento (V26) num banco de verdade. As regras estão cobertas sem
 * Spring (`ContractNegotiationRulesTest`, `RenewContractServiceTest`).
 * Clubes na faixa 972_xxx.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ContractMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: Json
    @Autowired private lateinit var clubRepo: ClubRepository

    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response
        else response

    private fun manager(clubId: Long): Pair<String, Long> {
        val body = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"renew-${System.nanoTime()}","password":"secret123"}"""
        }.andReturn().await().contentAsString
        val auth = json.decodeFromString(AuthResponse.serializer(), body)
        runBlocking {
            val squad = (1L..12L).map { n -> makePlayer(clubId * 100 + n, position = if (n == 1L) Position.GK else Position.MF) }
            val club = Club(ClubId(clubId), "Renova FC $clubId", 1_000_000_00.cents, 10_000, squad, ownerId = auth.user.id)
            assertIs<ClubSaveResult.Saved>(clubRepo.save(club))
        }
        return auth.token to clubId
    }

    private fun renew(token: String, clubId: Long, playerId: Long, body: String): MockHttpServletResponse =
        mockMvc.post("/api/clubs/$clubId/players/$playerId/renewal") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andReturn().await()

    private fun MockHttpServletResponse.jsonBody(): JsonObject = json.parseToJsonElement(contentAsString).jsonObject

    private fun JsonObject.type(): String = getValue("type").jsonPrimitive.content

    @Test
    fun `renovacao aceita grava o contrato novo e a ofensiva esfria a conversa`() {
        val (token, clubId) = manager(972_001)
        val playerId = clubId * 100 + 5

        val renewed = renew(token, clubId, playerId, """{"salary":100000000,"years":2,"releaseClause":500000000}""")
        assertEquals(200, renewed.status, renewed.contentAsString)
        val body = renewed.jsonBody()
        assertEquals("Renewed", body.type())
        val contract = body.getValue("player").jsonObject.getValue("contract").jsonObject
        assertEquals(500_000_000, contract.getValue("releaseClause").jsonPrimitive.long)
        // Gravado: o clube relido já tem o salário novo.
        val saved = runBlocking { assertNotNull(clubRepo.findById(ClubId(clubId))) }
        assertEquals(100_000_000.cents, saved.squad.first { it.id.value == playerId }.salary)

        // Abaixo do salário atual (piso da renovação) e ofensiva: esfria.
        val other = clubId * 100 + 6
        val refused = renew(token, clubId, other, """{"salary":1,"years":2}""").jsonBody()
        assertEquals("PlayerRefused", refused.type())
        assertEquals("LOW_SALARY", refused.getValue("reason").jsonPrimitive.content)
        val until = refused.getValue("cooldownUntil").jsonPrimitive.content

        val cooling = renew(token, clubId, other, """{"salary":100000000,"years":2}""").jsonBody()
        assertEquals("TalksCoolingDown", cooling.type())
        assertEquals(until, cooling.getValue("until").jsonPrimitive.content)
    }

    @Test
    fun `termos malformados sao 400, clube alheio 403 e jogador de fora vem no 200`() {
        val (token, clubId) = manager(972_011)
        val (otherToken, _) = manager(972_012)

        val invalid = renew(token, clubId, clubId * 100 + 5, """{"salary":100,"years":2,"releaseClause":0}""")
        assertEquals(400, invalid.status)
        assertEquals("INVALID_CONTRACT_OFFER", invalid.jsonBody().getValue("code").jsonPrimitive.content)

        // Salário acima do teto (US$ 100 milhões): 400, sem chegar à folha.
        assertEquals(400, renew(token, clubId, clubId * 100 + 5, """{"salary":${Long.MAX_VALUE},"years":2}""").status)

        assertEquals(403, renew(otherToken, clubId, clubId * 100 + 5, """{"salary":100,"years":2}""").status)

        val missing = renew(token, clubId, 1, """{"salary":100,"years":2}""")
        assertEquals(200, missing.status)
        assertEquals("PlayerNotAvailable", missing.jsonBody().type())
    }
}
