package com.carlesso.goalfather.config.security

import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.json.Json
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

/**
 * Devolve `401` com corpo `ErrorResponse` JSON (em vez do redirect padrão do
 * Spring Security para uma página de login que não existe nesta API).
 *
 * `config` depender de `adapter.in.web.dto` é aceitável: `config` é a
 * composition root (monta os beans de todas as camadas); a regra de direção
 * protege `domain`/`application`, não ela.
 */
@Component
class RestAuthenticationEntryPoint(private val json: Json) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        // Sem isto o Servlet usa ISO-8859-1 por padrão e "ç"/"á" saem corrompidos.
        // Precisa vir ANTES de `response.writer`: o charset do writer é fixado
        // na primeira chamada.
        response.characterEncoding = Charsets.UTF_8.name()
        // Serializa o mesmo DTO dos demais erros (com o bean `Json` do app), então
        // `details: {}` sai automaticamente — `encodeDefaults = true` no bean.
        response.writer.write(json.encodeToString(ErrorResponse.serializer(), UNAUTHORIZED_BODY))
    }

    // `companion object` = membro "estático" do Kotlin; o corpo é imutável e
    // pode ser reaproveitado entre requisições.
    private companion object {
        val UNAUTHORIZED_BODY = ErrorResponse(code = "UNAUTHORIZED", message = "Autenticação necessária")
    }
}
