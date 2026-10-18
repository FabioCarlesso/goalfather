package com.carlesso.goalfather.adapter.`in`.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Swagger UI do backend (revisão do PR #92). A doc citava springdoc, mas nada
 * era servido. Agora `/swagger-ui.html` mostra o PRÓPRIO contrato — sem token,
 * como health/info — e este teste garante que é byte a byte o arquivo de
 * `contract/`, não uma cópia que envelhece.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerUiMvcTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `a pagina do Swagger UI abre sem token e aponta para o contrato`() {
        val page = mockMvc.get("/swagger-ui.html").andReturn().response
        assertEquals(200, page.status)
        assertTrue("url: '/openapi.yaml'" in page.contentAsString)
    }

    @Test
    fun `o openapi servido e o proprio contract-openapi-yaml`() {
        val served = mockMvc.get("/openapi.yaml").andReturn().response
        assertEquals(200, served.status)
        assertEquals(File("../contract/openapi.yaml").readText(), served.getContentAsString(Charsets.UTF_8))
    }

    @Test
    fun `os assets do webjar resolvem sem a versao na URL`() {
        for (asset in listOf("swagger-ui-bundle.js", "swagger-ui.css")) {
            assertEquals(200, mockMvc.get("/webjars/swagger-ui/$asset").andReturn().response.status, asset)
        }
    }
}
