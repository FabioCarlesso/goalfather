package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import com.carlesso.goalfather.application.port.out.ClubRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
 * Cobre a cadeia de segurança (issue #18) ponta a ponta via MockMvc: token
 * ausente → 401, e usuário A não altera o clube de B → 403. Roda com o contexto
 * Spring completo (`@SpringBootTest` + `@AutoConfigureMockMvc`), então exercita
 * o `SecurityFilterChain`, o `JwtAuthenticationFilter` e o `@AuthenticationPrincipal`
 * reais — o que os testes de unidade dos services não tocam.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: Json
    @Autowired private lateinit var clubRepo: ClubRepository

    /**
     * Os controllers agora são `suspend`: o Spring MVC os executa de forma
     * assíncrona (via `kotlinx-coroutines-reactor`), então o MockMvc precisa de
     * `asyncDispatch` para materializar a resposta final — sem isso o corpo vem
     * vazio. Requisições barradas pelo `SecurityFilterChain` (401) nem chegam ao
     * controller, logo permanecem síncronas e não passam por aqui.
     */
    private fun MvcResult.await(): MockHttpServletResponse =
        if (request.isAsyncStarted) mockMvc.perform(asyncDispatch(this)).andReturn().response
        else response

    /** Cadastra e devolve o JWT do novo usuário. */
    private fun register(username: String): String {
        val body = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$username","password":"secret123"}"""
        }.andReturn().await().contentAsString
        return json.decodeFromString(AuthResponse.serializer(), body).token
    }

    @Test
    fun `GET clube sem token retorna 401`() {
        mockMvc.get("/api/clubs/1").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `me sem token retorna 401`() {
        mockMvc.get("/api/auth/me").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `register retorna 201 com token e me devolve o usuario autenticado`() {
        val username = "mvc-reg-${System.nanoTime()}"
        val token = register(username)
        assertTrue(token.isNotBlank())

        val response = mockMvc.get("/api/auth/me") {
            header("Authorization", "Bearer $token")
        }.andReturn().await()
        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains(""""username":"$username""""))
    }

    @Test
    fun `username duplicado retorna 409`() {
        val username = "mvc-dup-${System.nanoTime()}"
        register(username)
        val response = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$username","password":"secret123"}"""
        }.andReturn().await()
        assertEquals(409, response.status)
    }

    @Test
    fun `usuario A nao altera o clube de B - 403`() {
        val tokenA = register("mvc-a-${System.nanoTime()}")
        val tokenB = register("mvc-b-${System.nanoTime()}")
        val clubId = runBlocking { clubRepo.findAvailable().first().id.value }

        // A reivindica o clube.
        val claim = mockMvc.post("/api/clubs/$clubId/claim") {
            header("Authorization", "Bearer $tokenA")
        }.andReturn().await()
        assertEquals(200, claim.status)

        // B tenta salvar escalação no clube de A → 403 (checagem de posse).
        val lineup = mockMvc.post("/api/clubs/$clubId/lineup") {
            header("Authorization", "Bearer $tokenB")
            contentType = MediaType.APPLICATION_JSON
            content = """{"formation":"4-4-2","playerIds":[1,2,3,4,5,6,7,8,9,10,11]}"""
        }.andReturn().await()
        assertEquals(403, lineup.status)
    }
}
