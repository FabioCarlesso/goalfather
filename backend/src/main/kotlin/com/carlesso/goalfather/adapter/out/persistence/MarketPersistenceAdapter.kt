package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.entity.MarketEntryEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PositionEnum
import com.carlesso.goalfather.adapter.out.persistence.mapper.toDomain
import com.carlesso.goalfather.adapter.out.persistence.repository.MarketEntryJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.PlayerJpaRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class MarketPersistenceAdapter(
    private val marketRepo: MarketEntryJpaRepository,
    private val playerRepo: PlayerJpaRepository,
) : MarketRepository {

    override suspend fun findAll(position: Position?, maxPrice: Long?): List<MarketEntry> =
        withContext(Dispatchers.IO) {
            val entries = marketRepo.findAll()
            val withPlayer = entries.mapNotNull { entry ->
                val p = playerRepo.findById(entry.playerId).orElse(null) ?: return@mapNotNull null
                if (position != null && p.position != position.toEntity()) return@mapNotNull null
                if (maxPrice != null && entry.price > maxPrice) return@mapNotNull null
                MarketEntry(player = p.toDomain(), price = entry.price)
            }
            withPlayer
        }

    override suspend fun findEntry(playerId: PlayerId): MarketEntry? = withContext(Dispatchers.IO) {
        val entry = marketRepo.findById(playerId.value).orElse(null) ?: return@withContext null
        val player = playerRepo.findById(playerId.value).orElse(null) ?: return@withContext null
        MarketEntry(player = player.toDomain(), price = entry.price)
    }

    @Transactional
    override suspend fun add(entry: MarketEntry) {
        withContext(Dispatchers.IO) {
            // Player ja deve existir; só libera de clube (clubId = null) e
            // cria a entry de mercado.
            val player = playerRepo.findById(entry.player.id.value).orElse(null)
            if (player != null) {
                player.clubId = null
                playerRepo.save(player)
            }
            marketRepo.save(MarketEntryEntity(playerId = entry.player.id.value, price = entry.price))
        }
    }

    @Transactional
    override suspend fun remove(playerId: PlayerId) {
        withContext(Dispatchers.IO) {
            marketRepo.deleteById(playerId.value)
        }
    }
}

private fun Position.toEntity(): PositionEnum = when (this) {
    Position.GK -> PositionEnum.GK
    Position.CB -> PositionEnum.CB
    Position.MF -> PositionEnum.MF
    Position.FW -> PositionEnum.FW
}
