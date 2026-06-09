package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(name = "market_entries")
class MarketEntryEntity(
    @Id
    @Column(name = "player_id")
    var playerId: Long = 0,

    @Column(name = "price", nullable = false)
    var price: Long = 0,

    /**
     * Lock otimista (issue #21): o Hibernate inclui `version` no
     * `DELETE ... WHERE player_id = ? AND version = ?`. Duas compras
     * simultâneas do mesmo jogador → o segundo a commitar afeta 0 linhas
     * e leva `OptimisticLockException` → traduzido em `PlayerNotAvailable`.
     * Mesma mecânica do `@Version` do clube (issue #19).
     */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
)
