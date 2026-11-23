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
 * `GET /api/market` como busca paginada no pool de livres (issue #139), contra
 * o seed do pack do E2E: o formato `MarketPage` do contrato, a paginação, um
 * filtro e o 400 de parâmetro inválido. A regra de filtro em si é testada sem
 * Spring em `MarketPoolRulesTest`.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MarketSearchMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: Json

    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response else response

    private val token: String by lazy {
        val auth = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"mvc-market-${System.nanoTime() % 1_000_000}","password":"secret123"}"""
        }.andReturn().await().contentAsString
        json.decodeFromString(AuthResponse.serializer(), auth).token
    }

    private fun search(query: String): MockHttpServletResponse =
        mockMvc.get("/api/market?$query") { header("Authorization", "Bearer $token") }.andReturn().await()

    private fun page(query: String): JsonObject {
        val response = search(query)
        assertEquals(200, response.status, response.contentAsString)
        return json.parseToJsonElement(response.contentAsString).jsonObject
    }

    private fun JsonObject.int(field: String): Int = getValue(field).jsonPrimitive.int

    @Test
    fun `devolve uma pagina do contrato e pagina sem repetir jogador`() {
        val all = page("size=100")
        assertEquals(setOf("items", "page", "size", "totalItems", "totalPages"), all.keys)
        val total = all.int("totalItems")
        assertTrue(total >= 2, "o seed tem livres: $total")

        val first = page("size=1&page=0&sort=overall&dir=desc")
        val second = page("size=1&page=1&sort=overall&dir=desc")
        assertEquals(total, first.int("totalPages"))
        val ids = listOf(first, second).map { it.getValue("items").jsonArray.single().jsonObject.getValue("player").jsonObject.int("id") }
        assertEquals(2, ids.toSet().size, "páginas repetiram jogador: $ids")
        // Livre do pool sai sem taxa (issue #139).
        assertTrue(all.getValue("items").jsonArray.all { it.jsonObject.int("price") == 0 })
    }

    @Test
    fun `filtra por posicao`() {
        val keepers = page("position=GK&size=100").getValue("items").jsonArray
        assertTrue(keepers.isNotEmpty())
        assertTrue(keepers.all { it.jsonObject.getValue("player").jsonObject.getValue("position").jsonPrimitive.content == "GK" })
    }

    @Test
    fun `parametro invalido e 400 INVALID_MARKET_QUERY`() {
        for (query in listOf("sort=salary", "dir=up", "page=-1", "size=0", "size=101", "nationality=br", "minValue=-1")) {
            val response = search(query)
            assertEquals(400, response.status, "$query → ${response.contentAsString}")
            assertTrue(response.contentAsString.contains("INVALID_MARKET_QUERY"), response.contentAsString)
        }
    }
}
