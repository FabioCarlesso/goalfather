package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version

/**
 * Entidade JPA da rodada. Separada do `Round` de domínio — regra de ouro do
 * CLAUDE.md: nada em `domain` conhece JPA.
 */
@Entity
@Table(name = "rounds")
class RoundEntity(
    @Id
    @Column(name = "number")
    var number: Int = 0,

    @Column(name = "season", nullable = false)
    var season: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: RoundStatusEnum = RoundStatusEnum.Scheduled,

    @Column(name = "matches_json", columnDefinition = "TEXT", nullable = false)
    var matchesJson: String = "[]",

    /**
     * Lock otimista (issue #46): o Hibernate emite
     * `UPDATE ... WHERE number = ? AND version = ?` e rejeita o commit se
     * outra instância já avançou a versão. É o que torna a transição de status
     * atômica ENTRE JVMs — o `Mutex` in-JVM anterior só protegia um processo.
     * Mesma mecânica do clube (#19) e do mercado (#21).
     */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
)

enum class RoundStatusEnum { Scheduled, InProgress, Finished }
