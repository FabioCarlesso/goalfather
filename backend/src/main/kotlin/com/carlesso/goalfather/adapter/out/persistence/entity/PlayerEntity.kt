package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "players")
class PlayerEntity(
    @Id
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "club_id")
    var clubId: Long? = null,        // null = livre / no mercado

    @Column(name = "name", nullable = false)
    var name: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 2)
    var position: PositionEnum = PositionEnum.MF,

    @Column(name = "overall", nullable = false)
    var overall: Int = 0,

    @Column(name = "pace", nullable = false)
    var pace: Int = 0,

    @Column(name = "shooting", nullable = false)
    var shooting: Int = 0,

    @Column(name = "passing", nullable = false)
    var passing: Int = 0,

    @Column(name = "defending", nullable = false)
    var defending: Int = 0,

    @Column(name = "stamina", nullable = false)
    var stamina: Int = 100,

    @Column(name = "salary", nullable = false)
    var salary: Int = 0,

    @Column(name = "age", nullable = false)
    var age: Int = 25,

    @Column(name = "goals", nullable = false)
    var goals: Int = 0,

    @Column(name = "yellow_cards", nullable = false)
    var yellowCards: Int = 0,

    @Column(name = "red_cards", nullable = false)
    var redCards: Int = 0,

    @Column(name = "injured", nullable = false)
    var injured: Boolean = false,
)

/**
 * Enum interno da persistência. Mantemos um por camada para evitar que
 * mudanças no domínio (ex.: adicionar uma posição) automaticamente
 * impactem o DB. Mapeamento explícito feito em PlayerMapper.
 */
enum class PositionEnum { GK, CB, MF, FW }
