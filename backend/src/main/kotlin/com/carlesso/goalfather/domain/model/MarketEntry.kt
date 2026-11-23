package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

/**
 * Um jogador livre listado no mercado e o preço para contratá-lo.
 *
 * [seasonsWithoutClub] (issue #139) conta as viradas do mundo que ele passou
 * sem clube: quem fica tempo demais no pool se aposenta (ver
 * `FREE_AGENT_IDLE_SEASONS_TO_RETIRE` em `MarketPoolRules`).
 */
@Serializable
data class MarketEntry(
    val player: Player,
    val price: Money,
    val seasonsWithoutClub: Int = 0,
) {
    init {
        require(!price.isNegative) { "price não pode ser negativo: $price" }
        require(seasonsWithoutClub >= 0) { "seasonsWithoutClub negativo: $seasonsWithoutClub" }
    }
}
