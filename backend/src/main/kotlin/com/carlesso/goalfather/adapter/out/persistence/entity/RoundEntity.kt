package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

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
)

enum class RoundStatusEnum { Scheduled, InProgress, Finished }
