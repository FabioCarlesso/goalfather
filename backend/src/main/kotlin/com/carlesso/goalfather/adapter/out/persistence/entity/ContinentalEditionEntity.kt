package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

/**
 * Uma edição de competição continental (issue #132, V21). Separada da
 * `ContinentalCup` de domínio — regra de ouro do CLAUDE.md. A edição inteira
 * viaja em `edition_json`, como a `Cup` em `cups.cup_json`.
 */
@Entity
@Table(name = "continental_editions")
@IdClass(ContinentalEditionId::class)
class ContinentalEditionEntity(
    @Id
    @Column(name = "competition_id", length = 64)
    var competitionId: String = "",

    @Id
    @Column(name = "season")
    var season: Int = 0,

    @Column(name = "edition_json", columnDefinition = "TEXT", nullable = false)
    var editionJson: String = "{}",
)

/** Chave composta (competição, temporada). `data class` dá o `equals`/`hashCode` que o JPA exige. */
data class ContinentalEditionId(
    var competitionId: String = "",
    var season: Int = 0,
) : Serializable
