package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import com.carlesso.goalfather.application.port.out.SeasonHistoryRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.SeasonRecord
import com.carlesso.goalfather.domain.model.SeasonStanding
import com.carlesso.goalfather.domain.model.SeasonTopScorer
import com.carlesso.goalfather.domain.model.StandingRow
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
 * Trava o FORMATO do payload do histórico (issue #60), na mesma linha do
 * [LeagueStandingsMvcTest]: os endpoints devolvem `ResponseEntity<Any>`, então
 * a serialização só é decidida em runtime.
 *
 * Cobre também o ROTEAMENTO, que é o risco próprio destas rotas:
 * `/history/club/{clubId}` convive com `/history/{season}` e seria engolido
 * por ele se a especificidade do literal falhasse — o sintoma seria um 400
 * tentando ler "club" como inteiro.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeagueHistoryMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: Json
    @Autowired private lateinit var historyRepo: SeasonHistoryRepository

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

    private fun getResponse(path: String, token: String): MockHttpServletResponse =
        mockMvc.get(path) { header("Authorization", "Bearer $token") }.andReturn().await()

    private fun getJson(path: String, token: String): String {
        val response = getResponse(path, token)
        assertEquals(200, response.status, "GET $path deveria responder 200")
        return response.contentAsString
    }

    /** Temporada própria (faixa 93xx) para não colidir com outras suítes no mesmo H2. */
    private fun seedSeason(season: Int) = runBlocking {
        historyRepo.append(
            SeasonRecord(
                season = season,
                champion = standing(1, 1),
                finalStandings = listOf(standing(1, 1), standing(2, 2)),
                topScorer = SeasonTopScorer(PlayerId(10), "Renato Silva", ClubId(1), "C1", 23),
            ),
        )
    }

    private fun standing(position: Int, clubId: Long) = SeasonStanding(
        division = Division.FIRST,
        row = StandingRow(
            position = position,
            clubId = ClubId(clubId),
            clubName = "C$clubId",
            played = 10,
            points = 24,
        ),
        pointsPercentage = 80,
    )

    @Test
    fun `history devolve o snapshot com campeao, artilheiro e classificacao`() {
        val season = 9301
        seedSeason(season)
        val token = register("mvc-hist-${System.nanoTime() % 1_000_000}")

        val records = json.parseToJsonElement(getJson("/api/league/history", token)).jsonArray
        val record = assertNotNull(
            records.map { it.jsonObject }.find { it.getValue("season").jsonPrimitive.int == season },
            "temporada $season deveria estar no histórico",
        )

        val champion = record.getValue("champion").jsonObject
        assertEquals(1, champion.getValue("division").jsonPrimitive.int)
        assertEquals(80, champion.getValue("pointsPercentage").jsonPrimitive.int)
        // value class serializa como número puro (ClubId → long), não objeto.
        assertEquals(1, champion.getValue("row").jsonObject.getValue("clubId").jsonPrimitive.int)

        assertEquals(2, record.getValue("finalStandings").jsonArray.size)
        assertEquals(23, record.getValue("topScorer").jsonObject.getValue("goals").jsonPrimitive.int)
    }

    @Test
    fun `history por temporada devolve a temporada e 404 para uma nunca encerrada`() {
        val season = 9302
        seedSeason(season)
        val token = register("mvc-season-${System.nanoTime() % 1_000_000}")

        val record = json.parseToJsonElement(getJson("/api/league/history/$season", token)).jsonObject
        assertEquals(season, record.getValue("season").jsonPrimitive.int)

        val missing = getResponse("/api/league/history/9399", token)
        assertEquals(404, missing.status)
        assertEquals(
            "SEASON_RECORD_NOT_FOUND",
            json.parseToJsonElement(missing.contentAsString).jsonObject
                .getValue("code").jsonPrimitive.content,
        )
    }

    @Test
    fun `carreira do clube tem rota propria e nao e confundida com uma temporada`() {
        seedSeason(9303)
        val token = register("mvc-career-${System.nanoTime() % 1_000_000}")

        val career = json.parseToJsonElement(getJson("/api/league/history/club/1", token)).jsonObject
        assertEquals(1, career.getValue("clubId").jsonPrimitive.int)
        // O clube 1 é campeão em toda temporada semeada aqui — títulos ≥ 1.
        assertEquals(
            career.getValue("titles").jsonArray.size,
            career.getValue("seasonsPlayed").jsonPrimitive.int,
        )
        assertNotNull(career["bestCampaign"], "melhor campanha deveria vir preenchida")

        // Clube que nunca fechou temporada: 404, não carreira zerada.
        assertEquals(404, getResponse("/api/league/history/club/99999", token).status)
    }
}
