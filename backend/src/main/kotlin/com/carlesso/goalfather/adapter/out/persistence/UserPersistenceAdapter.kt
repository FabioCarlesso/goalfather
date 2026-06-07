package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.mapper.toDomain
import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.UserJpaRepository
import com.carlesso.goalfather.application.port.out.UserRepository
import com.carlesso.goalfather.domain.model.User
import com.carlesso.goalfather.domain.model.UserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Repository

/**
 * Implementação JPA do port [UserRepository]. Mesma ponte coroutine →
 * Spring Data dos demais adapters: `withContext(Dispatchers.IO)`.
 */
@Repository
class UserPersistenceAdapter(
    private val userRepo: UserJpaRepository,
) : UserRepository {

    override suspend fun findById(id: UserId): User? = withContext(Dispatchers.IO) {
        userRepo.findById(id.value).orElse(null)?.toDomain()
    }

    override suspend fun findByUsername(username: String): User? = withContext(Dispatchers.IO) {
        userRepo.findByUsername(username)?.toDomain()
    }

    override suspend fun existsByUsername(username: String): Boolean = withContext(Dispatchers.IO) {
        userRepo.existsByUsername(username)
    }

    override suspend fun findManagers(): List<User> = withContext(Dispatchers.IO) {
        userRepo.findAllByClubIdIsNotNullOrderById().map { it.toDomain() }
    }

    override suspend fun save(user: User): User = withContext(Dispatchers.IO) {
        userRepo.save(user.toEntity()).toDomain()
    }
}
