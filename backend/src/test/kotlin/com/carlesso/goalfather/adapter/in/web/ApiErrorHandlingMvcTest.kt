package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.errorprobe.ErrorProbeController
import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Formato padronizado dos erros de entrada (issue #81): tudo sai como
 * `ErrorResponse` (code/message/details), entrada malformada é 400 — nunca 500
 * nem o corpo padrão do Spring (`timestamp,status,error,path`) —, e os status
 * que o Spring já resolvia (404/405) continuam os mesmos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ErrorProbeController::class)
class ApiErrorHandlingMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: Json

    /** Ver nota em [AuthSecurityMvcTest]: controllers suspend exigem asyncDispatch. */
    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response
        else response

    /** Cadastra um técnico e reivindica um clube livre; devolve (token, clubId). */
    private fun managerWithClub(): Pair<String, Long> {
        val token = token()
        val available = mockMvc.get("/api/clubs/available") { header("Authorization", "Bearer $token") }
            .andReturn().await().contentAsString
        val clubId = json.parseToJsonElement(available).jsonArray.first().jsonObject.getValue("id").jsonPrimitive.long
        mockMvc.post("/api/clubs/$clubId/claim") { header("Authorization", "Bearer $token") }.andReturn().await()
        return token to clubId
    }

    private fun token(): String {
        val auth = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"mvc-errors-${System.nanoTime() % 1_000_000}","password":"secret123"}"""
        }.andReturn().await().contentAsString
        return json.decodeFromString(AuthResponse.serializer(), auth).token
    }

    /** Afirma status + corpo `ErrorResponse` com o `code` esperado e o devolve. */
    private fun MockHttpServletResponse.assertError(status: Int, code: String): ErrorResponse {
        assertEquals(status, this.status, contentAsString)
        assertTrue(contentType.orEmpty().startsWith("application/json"), "contentType=$contentType")
        // Decodificar com o serializer do DTO garante o formato do contrato; o
        // corpo padrão do Spring não tem `code` e falharia aqui.
        val error = json.decodeFromString(ErrorResponse.serializer(), contentAsString)
        assertEquals(code, error.code, contentAsString)
        assertFalse(contentAsString.contains("timestamp"), contentAsString)
        return error
    }

    @Test
    fun `escalacao com formacao desconhecida ou jogador repetido responde 400`() {
        val (token, clubId) = managerWithClub()
        val error = mockMvc.post("/api/clubs/$clubId/lineup") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"formation":"2-2-6","playerIds":[1,2,3,4,5,6,7,8,9,10,11]}"""
        }.andReturn().await().assertError(400, "MALFORMED_REQUEST")
        assertEquals("Formação desconhecida: 2-2-6", error.details["reason"])

        // Mesmo clube (cada `managerWithClub` consome um dos poucos clubes livres
        // do banco compartilhado pelos testes): jogador repetido é erro de
        // negócio, com os ids em `details` — a checagem vem antes de "fora do
        // elenco", então ids arbitrários bastam.
        val duplicated = mockMvc.post("/api/clubs/$clubId/lineup") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"formation":"4-4-2","playerIds":[7,7,2,3,4,5,6,8,9,9,10]}"""
        }.andReturn().await().assertError(400, "DUPLICATE_PLAYERS")
        assertEquals("7,9", duplicated.details["playerIds"])
    }

    @Test
    fun `foco de treino desconhecido responde 400 MALFORMED_REQUEST`() {
        val (token, clubId) = managerWithClub()
        val error = mockMvc.post("/api/clubs/$clubId/training") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"focus":"YOGA"}"""
        }.andReturn().await().assertError(400, "MALFORMED_REQUEST")

        // O kotlinx cita o enum pelo nome qualificado; o pacote interno não
        // deve vazar para o cliente — só o nome simples do tipo.
        val reason = error.details["reason"].orEmpty()
        assertTrue(reason.contains("TrainingFocus") && reason.contains("YOGA"), reason)
        assertFalse(reason.contains("com.carlesso"), reason)
    }

    @Test
    fun `json sintaticamente invalido responde 400 MALFORMED_REQUEST`() {
        val (token, clubId) = managerWithClub()
        mockMvc.post("/api/clubs/$clubId/training") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"focus":"""
        }.andReturn().await().assertError(400, "MALFORMED_REQUEST")
    }

    @Test
    fun `posicao invalida no filtro do mercado responde 400 INVALID_PARAMETER`() {
        val error = mockMvc.get("/api/market?position=XX") { header("Authorization", "Bearer ${token()}") }
            .andReturn().await().assertError(400, "INVALID_PARAMETER")
        assertEquals("position", error.details["parameter"])
        assertEquals("XX", error.details["value"])
    }

    @Test
    fun `id de path nao numerico responde 400 INVALID_PARAMETER`() {
        mockMvc.get("/api/clubs/abc") { header("Authorization", "Bearer ${token()}") }
            .andReturn().await().assertError(400, "INVALID_PARAMETER")
    }

    @Test
    fun `rota inexistente continua 404 agora como ErrorResponse`() {
        mockMvc.get("/api/rota-que-nao-existe") { header("Authorization", "Bearer ${token()}") }
            .andReturn().await().assertError(404, "NOT_FOUND")
    }

    @Test
    fun `metodo nao suportado continua 405`() {
        val response = mockMvc.delete("/api/market") { header("Authorization", "Bearer ${token()}") }
            .andReturn().await()
        response.assertError(405, "METHOD_NOT_ALLOWED")
        assertTrue(response.getHeader("Allow").orEmpty().contains("GET"), "Allow=${response.getHeader("Allow")}")
    }

    @Test
    fun `sem token continua 401 do entry point`() {
        mockMvc.get("/api/rota-que-nao-existe").andReturn().await().assertError(401, "UNAUTHORIZED")
    }

    @Test
    fun `require de dominio estourando vira 500 INTERNAL_ERROR sem vazar detalhes`() {
        val response = mockMvc.get("/api/test-probe/require") { header("Authorization", "Bearer ${token()}") }
            .andReturn().await()
        val error = response.assertError(500, "INTERNAL_ERROR")
        assertTrue(error.details.isEmpty())
        assertFalse(response.contentAsString.contains("segredo"), response.contentAsString)
        assertFalse(response.contentAsString.contains("Exception"), response.contentAsString)
    }

    @Test
    fun `exception em handler suspend tambem vira 500 INTERNAL_ERROR`() {
        val response = mockMvc.get("/api/test-probe/suspend") { header("Authorization", "Bearer ${token()}") }
            .andReturn().await()
        response.assertError(500, "INTERNAL_ERROR")
        assertFalse(response.contentAsString.contains("segredo"), response.contentAsString)
    }
}
