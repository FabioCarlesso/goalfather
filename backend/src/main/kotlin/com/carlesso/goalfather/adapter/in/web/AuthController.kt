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
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.LoginResult
import com.carlesso.goalfather.domain.result.RegisterResult
import kotlinx.coroutines.runBlocking
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
) {

    @PostMapping("/register")
    fun register(@RequestBody req: RegisterRequest): ResponseEntity<Any> = runBlocking {
        validate(req.username, req.password)?.let { return@runBlocking it }
        when (val result = register.execute(normalize(req.username), req.password)) {
            is RegisterResult.Success -> ResponseEntity.status(201).body(authResponse(result.user))
            is RegisterResult.UsernameTaken -> ResponseEntity.status(409).body(
                ErrorResponse(code = "USERNAME_TAKEN", message = "Username '${result.username}' já está em uso"),
            )
        }
    }

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest): ResponseEntity<Any> = runBlocking {
        when (val result = login.execute(normalize(req.username), req.password)) {
            is LoginResult.Success -> ResponseEntity.ok(authResponse(result.user))
            is LoginResult.InvalidCredentials -> ResponseEntity.status(401).body(
                ErrorResponse(code = "INVALID_CREDENTIALS", message = "Usuário ou senha inválidos"),
            )
        }
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal userId: Long?): ResponseEntity<Any> = runBlocking {
        if (userId == null) {
            return@runBlocking ResponseEntity.status(401).body(
                ErrorResponse(code = "UNAUTHORIZED", message = "Autenticação necessária"),
            )
        }
        val user = userRepo.findById(UserId(userId))
            ?: return@runBlocking ResponseEntity.status(401).body(
                ErrorResponse(code = "UNAUTHORIZED", message = "Usuário não encontrado"),
            )
        ResponseEntity.ok(user.toDto())
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

    private fun authResponse(user: User): AuthResponse =
        AuthResponse(token = jwt.generate(user.id.value, user.username), user = user.toDto())
}
