package com.carlesso.goalfather.config.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Lê `Authorization: Bearer <jwt>`, valida o token e popula o
 * `SecurityContext`. O *principal* é o `userId` (Long) — os controllers o
 * recuperam com `@AuthenticationPrincipal userId: Long`.
 *
 * Não rejeita requisições sem token: apenas não autentica. Quem decide
 * 401/403 é a cadeia de segurança (`authorizeHttpRequests`).
 */
@Component
class JwtAuthenticationFilter(
    private val jwt: JwtService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            val userId = jwt.parseUserId(header.removePrefix(BEARER_PREFIX))
            if (userId != null && SecurityContextHolder.getContext().authentication == null) {
                val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())
                auth.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
