package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "market_entries")
class MarketEntryEntity(
    @Id
    @Column(name = "player_id")
    var playerId: Long = 0,

    @Column(name = "price", nullable = false)
    var price: Long = 0,
)
