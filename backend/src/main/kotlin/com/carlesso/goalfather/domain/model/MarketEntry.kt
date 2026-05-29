package com.carlesso.goalfather.domain.model

/**
 * Jogador disponível no mercado, com preço em centavos.
 */
data class MarketEntry(
    val player: Player,
    val price: Long,
) {
    init {
        require(price >= 0) { "price não pode ser negativo: $price" }
    }
}
