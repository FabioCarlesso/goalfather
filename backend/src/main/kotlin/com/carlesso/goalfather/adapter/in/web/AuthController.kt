package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AuthResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.LoginRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.RegisterRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.toDto
import com.carlesso.goalfather.application.port.`in`.LoginUseCase
import com.carlesso.goalfather.application.port.`in`.RegisterUserUseCase
import com.carlesso.goalfather.application.port.out.UserRepository
import com.carlesso.goalfather.config.security.JwtService
import com.carlesso.goalfather.config.security.ratelimit.RateLimiter
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.LoginResult
import com.carlesso.goalfather.domain.result.RegisterResult
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val register: RegisterUserUseCase,
    private val login: LoginUseCase,
    private val userRepo: UserRepository,
    private val jwt: JwtService,
    @Qualifier("loginRateLimiter") private val loginRateLimiter: RateLimiter,
    @Qualifier("registerRateLimiter") private val registerRateLimiter: RateLimiter,
) {

    @PostMapping("/register")
    suspend fun register(@RequestBody req: RegisterRequest, request: HttpServletRequest): ResponseEntity<Any> {
        // Register é limitado por IP (throttle de criação em massa): conta toda
        // tentativa que chega ao endpoint, independente do resultado.
        val key = "register:${clientIp(request)}"
        rateLimited(registerRateLimiter, key)?.let { return it }
        registerRateLimiter.record(key)

        validate(req.username, req.password)?.let { return it }
        return when (val result = register.execute(normalize(req.username), req.password)) {
            is RegisterResult.Success -> ResponseEntity.status(201).body(authResponse(result.user))
            is RegisterResult.UsernameTaken -> ResponseEntity.status(409).body(
                ErrorResponse(code = "USERNAME_TAKEN", message = "Username '${result.username}' já está em uso"),
            )
        }
    }

    @PostMapping("/login")
    suspend fun login(@RequestBody req: LoginRequest, request: HttpServletRequest): ResponseEntity<Any> {
        // Login é limitado por IP + username: só tentativas INVÁLIDAS contam, e
        // um login bem-sucedido zera o contador (não pune quem só errou a senha).
        val username = normalize(req.username)
        val key = "login:${clientIp(request)}:$username"
        rateLimited(loginRateLimiter, key)?.let { return it }

        return when (val result = login.execute(username, req.password)) {
            is LoginResult.Success -> {
                loginRateLimiter.reset(key)
                ResponseEntity.ok(authResponse(result.user))
            }
            is LoginResult.InvalidCredentials -> {
                loginRateLimiter.record(key)
                ResponseEntity.status(401).body(
                    ErrorResponse(code = "INVALID_CREDENTIALS", message = "Usuário ou senha inválidos"),
                )
            }
        }
    }

    @GetMapping("/me")
    suspend fun me(@AuthenticationPrincipal userId: Long?): ResponseEntity<Any> {
        if (userId == null) {
            return ResponseEntity.status(401).body(
                ErrorResponse(code = "UNAUTHORIZED", message = "Autenticação necessária"),
            )
        }
        val user = userRepo.findById(UserId(userId))
            ?: return ResponseEntity.status(401).body(
                ErrorResponse(code = "UNAUTHORIZED", message = "Usuário não encontrado"),
            )
        return ResponseEntity.ok(user.toDto())
    }

    /**
     * Normaliza o username: `trim` + `lowercase` para que `Fabio`, `fabio ` e
     * `FABIO` sejam a MESMA conta — register e login usam a mesma forma, então
     * a unicidade e o lookup batem (issue: L2 da review da PR #26).
     */
    private fun normalize(username: String): String = username.trim().lowercase()

    /** Validação mínima espelhando o contrato (username >= 3, senha >= 6). */
    private fun validate(username: String, password: String): ResponseEntity<Any>? {
        val u = username.trim()
        return when {
            u.length < 3 -> badRequest("Username precisa de ao menos 3 caracteres")
            password.length < 6 -> badRequest("Senha precisa de ao menos 6 caracteres")
            else -> null
        }
    }

    private fun badRequest(message: String): ResponseEntity<Any> =
        ResponseEntity.badRequest().body(ErrorResponse(code = "VALIDATION_ERROR", message = message))

    /**
     * Se a chave estourou o limite, devolve `429` com `Retry-After` no mesmo
     * formato [ErrorResponse] dos demais erros; caso contrário `null` (segue).
     */
    private fun rateLimited(limiter: RateLimiter, key: String): ResponseEntity<Any>? =
        if (limiter.isBlocked(key)) {
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, limiter.retryAfterSeconds.toString())
                .body(
                    ErrorResponse(
                        code = "TOO_MANY_REQUESTS",
                        message = "Muitas tentativas. Tente novamente mais tarde.",
                    ),
                )
        } else {
            null
        }

    /**
     * IP de origem para a chave de rate limit. Atrás do nginx (docker-compose),
     * o IP real vem no `X-Forwarded-For` — pega-se o PRIMEIRO da lista (o
     * cliente; os demais são proxies). Sem proxy, usa `remoteAddr`.
     *
     * Ressalva: `X-Forwarded-For` é forjável se a app ficar exposta sem um proxy
     * confiável na frente. Na topologia atual (nginx único) o header é setado
     * pelo proxy, então é confiável o suficiente para throttling.
     */
    private fun clientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: request.remoteAddr

    private fun authResponse(user: User): AuthResponse =
        AuthResponse(token = jwt.generate(user.id.value, user.username), user = user.toDto())
}
