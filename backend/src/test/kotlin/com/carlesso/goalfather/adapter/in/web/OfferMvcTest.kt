package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.test.makePlayer
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertIs

/**
 * Trava o FORMATO HTTP das propostas entre técnicos (issue #63): o `type` do
 * `TransferOfferResult` e do `OfferStatus` (sealed → kotlinx, não Jackson),
 * e os 403/404 de posse e existência. As regras em si estão cobertas sem
 * Spring (`TransferOfferRulesTest`, `TransferOfferServiceTest`).
 *
 * Os clubes são criados aqui (faixa 971_xxx) com dono = usuário recém-criado:
 * os clubes do seed têm 11 jogadores (não podem vender) e podem já ter sido
 * reivindicados por outros testes do mesmo contexto.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OfferMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: Json
    @Autowired private lateinit var clubRepo: ClubRepository

    /** Ver nota em [AuthSecurityMvcTest]: controllers suspend exigem asyncDispatch. */
    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response
        else response

    private data class Manager(val token: String, val clubId: Long)

    /** Usuário novo + clube humano dele, com [squadSize] jogadores (o 1º é goleiro). */
    private fun manager(clubId: Long, squadSize: Int = 12): Manager {
        val body = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"offer-${System.nanoTime()}","password":"secret123"}"""
        }.andReturn().await().contentAsString
        val auth = json.decodeFromString(AuthResponse.serializer(), body)
        runBlocking {
            val squad = (1L..squadSize.toLong()).map { n ->
                makePlayer(clubId * 100 + n, position = if (n == 1L) Position.GK else Position.MF)
            }
            val club = Club(ClubId(clubId), "Mvc FC $clubId", 10_000_000_00, 10_000, squad, ownerId = auth.user.id)
            assertIs<ClubSaveResult.Saved>(clubRepo.save(club))
        }
        return Manager(auth.token, clubId)
    }

    private fun post(path: String, token: String, body: String? = null): MockHttpServletResponse =
        mockMvc.post(path) {
            header("Authorization", "Bearer $token")
            if (body != null) {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        }.andReturn().await()

    private fun get(path: String, token: String): MockHttpServletResponse =
        mockMvc.get(path) { header("Authorization", "Bearer $token") }.andReturn().await()

    private fun createBody(buyer: Manager, seller: Manager, playerN: Long = 5, amount: Long = 300_000_00) =
        """{"buyerClubId":${buyer.clubId},"sellerClubId":${seller.clubId},""" +
            """"playerId":${seller.clubId * 100 + playerN},"amount":$amount}"""

    private fun MockHttpServletResponse.jsonBody(): JsonObject =
        json.parseToJsonElement(contentAsString).jsonObject

    private fun JsonObject.type(): String = getValue("type").jsonPrimitive.content

    @Test
    fun `ciclo proposta - recusa sai com o type do contrato`() {
        val seller = manager(971_001)
        val buyer = manager(971_002)

        val created = post("/api/offers", buyer.token, createBody(buyer, seller))
        assertEquals(200, created.status)
        val body = created.jsonBody()
        assertEquals("Success", body.type())
        val offer = body.getValue("offer").jsonObject
        assertEquals("Pending", offer.getValue("status").jsonObject.type())
        assertEquals(3, offer.getValue("roundsLeft").jsonPrimitive.int)
        assertEquals("Mvc FC 971001", offer.getValue("sellerClubName").jsonPrimitive.content)
        val offerId = offer.getValue("id").jsonPrimitive.long

        // O vendedor enxerga a proposta recebida na lista do clube.
        val listed = get("/api/clubs/${seller.clubId}/offers", seller.token)
        assertEquals(200, listed.status)
        val ids = json.parseToJsonElement(listed.contentAsString).jsonArray
            .map { it.jsonObject.getValue("id").jsonPrimitive.long }
        assertEquals(listOf(offerId), ids)

        val rejected = post("/api/offers/$offerId/reject", seller.token).jsonBody()
        assertEquals("Success", rejected.type())
        assertEquals("Rejected", rejected.getValue("offer").jsonObject.getValue("status").jsonObject.type())

        // Estado terminal: aceitar depois da recusa é erro de negócio no 200.
        val late = post("/api/offers/$offerId/accept", seller.token)
        assertEquals(200, late.status)
        val lateBody = late.jsonBody()
        assertEquals("OfferNotPending", lateBody.type())
        assertEquals("Rejected", lateBody.getValue("status").jsonObject.type())
    }

    @Test
    fun `vendedor com elenco minimo volta SellerSquadInvalid no 200`() {
        val seller = manager(971_011, squadSize = 11)
        val buyer = manager(971_012)

        val response = post("/api/offers", buyer.token, createBody(buyer, seller))

        assertEquals(200, response.status)
        val body = response.jsonBody()
        assertEquals("SellerSquadInvalid", body.type())
        assertEquals("TOO_FEW_PLAYERS", body.getValue("violation").jsonPrimitive.content)
        assertEquals(11, body.getValue("minSquadSize").jsonPrimitive.int)
    }

    @Test
    fun `posse e existencia viram 403 e 404`() {
        val seller = manager(971_021)
        val buyer = manager(971_022)

        // Propor em nome do clube de outro técnico.
        assertEquals(403, post("/api/offers", seller.token, createBody(buyer, seller)).status)
        // Ler as propostas de outro clube.
        assertEquals(403, get("/api/clubs/${seller.clubId}/offers", buyer.token).status)

        val offerId = post("/api/offers", buyer.token, createBody(buyer, seller)).jsonBody()
            .getValue("offer").jsonObject.getValue("id").jsonPrimitive.long
        // Comprador não aceita a própria proposta; vendedor não a retira.
        assertEquals(403, post("/api/offers/$offerId/accept", buyer.token).status)
        assertEquals(403, post("/api/offers/$offerId/withdraw", seller.token).status)

        val missing = post("/api/offers/999999999/accept", seller.token)
        assertEquals(404, missing.status)
        assertEquals("OFFER_NOT_FOUND", missing.jsonBody().getValue("code").jsonPrimitive.content)
    }
}
