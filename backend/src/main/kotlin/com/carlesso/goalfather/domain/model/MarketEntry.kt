package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MarketEntry(
    val player: Player,
    val price: Money,
) {
    init {
        require(!price.isNegative) { "price não pode ser negativo: $price" }
    }
}
