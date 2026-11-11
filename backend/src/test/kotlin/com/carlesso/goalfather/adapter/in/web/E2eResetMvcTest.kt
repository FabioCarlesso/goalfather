package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import com.carlesso.goalfather.adapter.out.persistence.repository.UserJpaRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.test.md
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reset do profile `e2e` (issue #101): depois de `POST /test-support/reset` o
 * estado tem de ser o de um boot limpo, que é o que cada spec do
 * `npm run e2e:real` assume.
 *
 * Banco PRÓPRIO (`goalfather-e2e-reset`): o H2 em memória com
 * `DB_CLOSE_DELAY=-1` é compartilhado por nome entre os contextos Spring do
 * mesmo JVM. Resetar o banco `goalfather-test` apagaria o que as outras suítes
 * em cache gravaram.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:goalfather-e2e-reset;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"])
class E2eResetMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: Json
    @Autowired private lateinit var leagueRepo: LeagueRepository
    @Autowired private lateinit var users: UserJpaRepository
    @Autowired private lateinit var caches: CacheManager

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

    private fun get(path: String, token: String) =
        json.parseToJsonElement(
            mockMvc.get(path) { header("Authorization", "Bearer $token") }.andReturn().await().contentAsString,
        )

    @Test
    fun `reset volta clubes, rodada, tabela e usuarios ao estado de boot limpo`() {
        // Suja tudo o que os specs sujavam: técnico com clube, calendário
        // adiantado e tabela com pontos.
        val token = register("sujo")
        val freeBefore = get("/api/clubs/available", token).jsonArray.size
        mockMvc.post("/api/clubs/1/claim") { header("Authorization", "Bearer $token") }.andReturn().await()
        runBlocking {
            leagueRepo.saveRound(Round(matchday = md(7), season = 2026, matches = emptyList()))
            val elite = leagueRepo.currentStandings().first()
            leagueRepo.saveStandings(elite.copy(rows = elite.rows.map { it.copy(points = 9) }))
        }
        val dirty = get("/api/league/standings", token).jsonArray
            .flatMap { it.jsonObject.getValue("rows").jsonArray }
            .map { it.jsonObject.getValue("points").jsonPrimitive.int }
        assertTrue(9 in dirty, "pré-condição: a tabela suja é a que a API serve")

        val reset = mockMvc.post("/test-support/reset").andReturn()
        assertEquals(204, reset.response.status)

        assertEquals(0, users.count(), "reset deveria apagar os usuários")
        val fresh = register("limpo")
        assertEquals(freeBefore, get("/api/clubs/available", fresh).jsonArray.size, "todos os clubes livres de novo")
        assertEquals("2026-W01-WEEKEND", get("/api/league/round/current", fresh).jsonObject.getValue("matchday").jsonObject.getValue("key").jsonPrimitive.content)

        val points = get("/api/league/standings", fresh).jsonArray
            .flatMap { it.jsonObject.getValue("rows").jsonArray }
            .map { it.jsonObject.getValue("points").jsonPrimitive.int }
        assertEquals(setOf(0), points.toSet(), "tabela zerada")

        assertNotNull(get("/api/league/cup", fresh).jsonObject["stages"], "copa sorteada de novo")
    }

    @Test
    fun `reset esvazia todo cache, inclusive o de tabela que a seed nao regrava`() {
        // `market`/`standings` já seriam despejados pelos `@CacheEvict` dos
        // `save` da seed. Um cache sobre outra tabela, não: o TRUNCATE passa
        // por fora do JPA e não dispara evict nenhum. É esse caso que o
        // esvaziamento explícito do reset cobre.
        val cache = assertNotNull(caches.getCache("fora-da-seed"))
        cache.put("chave", "liga anterior")

        mockMvc.post("/test-support/reset").andReturn()

        assertNull(cache.get("chave"))
    }

    @Test
    fun `reset seguido de reset nao viola as PKs da seed`() {
        repeat(2) { assertEquals(204, mockMvc.post("/test-support/reset").andReturn().response.status) }
    }
}

/**
 * O outro lado da trava: no contexto padrão (sem `e2e`, como em produção e no
 * Docker) a rota de reset não existe. Se alguém tirar o `@Profile`, é aqui que
 * quebra, não no banco de alguém.
 */
@SpringBootTest
@AutoConfigureMockMvc
class E2eResetAbsentMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `sem o profile e2e a rota de reset responde 404`() {
        assertEquals(404, mockMvc.post("/test-support/reset").andReturn().response.status)
    }
}
