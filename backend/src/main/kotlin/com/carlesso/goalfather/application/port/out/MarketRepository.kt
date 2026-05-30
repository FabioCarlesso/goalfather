package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position

interface MarketRepository {
    suspend fun findAll(position: Position? = null, maxPrice: Long? = null): List<MarketEntry>
    suspend fun findEntry(playerId: PlayerId): MarketEntry?
    suspend fun add(entry: MarketEntry)
    suspend fun remove(playerId: PlayerId)
}
