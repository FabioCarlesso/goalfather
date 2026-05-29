package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MarketEntry(
    val player: Player,
    val price: Long,
) {
    init {
        require(price >= 0) { "price não pode ser negativo: $price" }
    }
}
