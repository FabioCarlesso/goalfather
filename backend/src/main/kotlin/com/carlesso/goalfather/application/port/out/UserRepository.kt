package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId

/**
 * Port de saída para persistência de usuários. Implementado em
 * `adapter/out/persistence` sobre JPA. O `save` recebe o domínio [User]
 * (com hash) e devolve o usuário persistido (já com `id` gerado no insert).
 */
interface UserRepository {
    suspend fun findById(id: UserId): User?

    suspend fun findByUsername(username: String): User?

    suspend fun existsByUsername(username: String): Boolean

    /** Técnicos humanos — usuários que já reivindicaram um clube (issue #20). */
    suspend fun findManagers(): List<User>

    suspend fun save(user: User): User
}
