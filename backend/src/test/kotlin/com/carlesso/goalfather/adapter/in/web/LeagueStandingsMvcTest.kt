package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertNotNull

/**
 * Trava o FORMATO do payload HTTP dos endpoints de liga (issue #47, achado 1
 * da review do PR #64). Os endpoints devolvem `ResponseEntity<Any>` — a
 * escolha do conversor (kotlinx vs. Jackson) acontece em runtime, e uma
 * regressão aí não seria pega nem pelos testes de unidade (que ficam aquém
 * do HTTP) nem pelo E2E de CI (que roda contra mocks MSW).
 *
 * Por isso os asserts inspecionam o JSON CRU (`parseToJsonElement`), não um
 * decode para as data classes: desserializar preencheria campos ausentes com
 * defaults (`division = 1`, spots `0`) e mascararia exatamente a regressão
 * que queremos detectar — campo requerido pelo contrato sumindo do payload.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeagueStandingsMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: Json
    @Autowired private lateinit var leagueRepo: LeagueRepository

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

    private fun getJson(path: String, token: String): String {
        val response = mockMvc.get(path) {
            header("Authorization", "Bearer $token")
        }.andReturn().await()
        assertEquals(200, response.status, "GET $path deveria responder 200")
        return response.contentAsString
    }

    @Test
    fun `standings responde um array com uma tabela por divisao e as vagas de zona`() {
        val token = register("mvc-standings-${System.nanoTime()}")

        val tables = json.parseToJsonElement(getJson("/api/league/standings", token)).jsonArray
        assertEquals(2, tables.size, "Seed tem 2 divisões → 2 tabelas")

        val byDivision = tables.map { it.jsonObject }.associateBy {
            assertNotNull(it["division"], "Campo requerido `division` ausente do payload")
                .jsonPrimitive.int
        }
        assertEquals(setOf(1, 2), byDivision.keys)

        val elite = byDivision.getValue(1)
        assertEquals(0, elite.getValue("promotionSpots").jsonPrimitive.int)
        assertEquals(2, elite.getValue("relegationSpots").jsonPrimitive.int)
        assertEquals(6, elite.getValue("rows").jsonArray.size)

        val second = byDivision.getValue(2)
        assertEquals(2, second.getValue("promotionSpots").jsonPrimitive.int)
        assertEquals(0, second.getValue("relegationSpots").jsonPrimitive.int)
        assertEquals(6, second.getValue("rows").jsonArray.size)

        // value classes serializam como número puro (ClubId → long), não objeto.
        val firstRow = elite.getValue("rows").jsonArray.first().jsonObject
        assertNotNull(firstRow["clubId"]).jsonPrimitive.int
    }

    @Test
    fun `round current expoe a divisao de cada partida`() {
        // Outras suítes de integração também gravam rodadas "latest" no mesmo
        // contexto/H2 — salvar a própria rodada torna o teste imune à ordem.
        val roundNumber = 9401
        runBlocking {
            leagueRepo.saveRound(
                Round(
                    number = roundNumber,
                    season = 2026,
                    matches = listOf(
                        RoundMatch(9401101, ClubId(1), ClubId(2), "A", "B"),
                        RoundMatch(9401201, ClubId(7), ClubId(8), "C", "D", division = Division(2)),
                    ),
                ),
            )
        }
        val token = register("mvc-round-${System.nanoTime()}")

        val round = json.parseToJsonElement(getJson("/api/league/round/current", token)).jsonObject
        assertEquals(roundNumber, round.getValue("number").jsonPrimitive.int)

        val matches = round.getValue("matches").jsonArray.map { it.jsonObject }
        val divisions = matches.map {
            assertNotNull(it["division"], "Campo requerido `division` ausente da partida")
                .jsonPrimitive.int
        }
        // Inclui a divisão default (1): `encodeDefaults` precisa mantê-la no payload.
        assertEquals(listOf(1, 2), divisions, "Rodada global agrega as duas divisões")
    }
}
