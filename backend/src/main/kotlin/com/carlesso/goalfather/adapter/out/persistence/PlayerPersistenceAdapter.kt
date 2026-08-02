package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.PlayerJpaRepository
import com.carlesso.goalfather.application.port.out.PlayerRepository
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Implementação do port `PlayerRepository` — jogadores fora de qualquer elenco
 * (issue #55). Mesma ponte `withContext(Dispatchers.IO)` dos demais adapters.
 */
@Repository
class PlayerPersistenceAdapter(
    private val playerRepo: PlayerJpaRepository,
) : PlayerRepository {

    @Transactional
    override suspend fun saveFreeAgents(players: List<Player>) {
        if (players.isEmpty()) return
        withContext(Dispatchers.IO) {
            playerRepo.saveAll(players.map { it.toEntity(clubId = null) })
        }
    }

    @Transactional
    override suspend fun deleteAll(ids: List<PlayerId>) {
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            playerRepo.deleteAllById(ids.map { it.value })
        }
    }
}
