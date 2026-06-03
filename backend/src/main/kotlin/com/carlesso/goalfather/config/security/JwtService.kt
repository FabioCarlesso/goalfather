package com.carlesso.goalfather.config.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date

/**
 * Emite e valida JWTs (HMAC-SHA256, issue #18).
 *
 * O segredo vem de `app.jwt.secret` (env `JWT_SECRET` em produção). Precisa
 * ter ≥ 32 bytes para HS256 — o default de dev cumpre isso. O `subject` do
 * token carrega o `userId`; um claim `username` é incluído por conveniência.
 */
@Component
class JwtService(
    @Value("\${app.jwt.secret}") secret: String,
    @Value("\${app.jwt.expiration-ms:86400000}") private val expirationMs: Long,
) {
    private val key = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generate(userId: Long, username: String): String =
        Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(key)
            .compact()

    /** `userId` do token, ou `null` se ausente, expirado ou com assinatura inválida. */
    fun parseUserId(token: String): Long? = runCatching {
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
            .subject
            .toLong()
    }.getOrNull()
}
