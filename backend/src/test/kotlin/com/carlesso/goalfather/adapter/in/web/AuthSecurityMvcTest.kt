package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import com.carlesso.goalfather.application.port.out.ClubRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.Test
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

    /** Cadastra e devolve o JWT do novo usuário. */
    private fun register(username: String): String {
        val body = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$username","password":"secret123"}"""
        }.andReturn().response.contentAsString
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

        mockMvc.get("/api/auth/me") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.username") { value(username) }
        }
    }

    @Test
    fun `username duplicado retorna 409`() {
        val username = "mvc-dup-${System.nanoTime()}"
        register(username)
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$username","password":"secret123"}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `usuario A nao altera o clube de B - 403`() {
        val tokenA = register("mvc-a-${System.nanoTime()}")
        val tokenB = register("mvc-b-${System.nanoTime()}")
        val clubId = runBlocking { clubRepo.findAvailable().first().id.value }

        // A reivindica o clube.
        mockMvc.post("/api/clubs/$clubId/claim") {
            header("Authorization", "Bearer $tokenA")
        }.andExpect { status { isOk() } }

        // B tenta salvar escalação no clube de A → 403 (checagem de posse).
        mockMvc.post("/api/clubs/$clubId/lineup") {
            header("Authorization", "Bearer $tokenB")
            contentType = MediaType.APPLICATION_JSON
            content = """{"formation":"4-4-2","playerIds":[1,2,3,4,5,6,7,8,9,10,11]}"""
        }.andExpect { status { isForbidden() } }
    }
}
