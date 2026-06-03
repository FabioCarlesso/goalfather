package com.carlesso.goalfather.application.port.out

/**
 * Port para hashing de senha. Existe para manter o Spring Security FORA da
 * camada `application`: o serviço de auth depende desta interface, não do
 * `PasswordEncoder` do framework. A implementação (BCrypt) é registrada em
 * `config/SecurityConfig`. Inversão de dependência clássica — facilita
 * também o teste, que injeta um hasher fake determinístico.
 */
interface PasswordHasher {
    fun hash(raw: String): String

    fun matches(raw: String, hash: String): Boolean
}
