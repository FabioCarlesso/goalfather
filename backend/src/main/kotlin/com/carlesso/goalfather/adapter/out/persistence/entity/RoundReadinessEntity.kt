package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/**
 * Marcação de "técnico pronto" para um encaixe (issues #20 e #125). A PK
 * composta (encaixe, user_id) torna o `markReady` idempotente: re-sinalizar só
 * atualiza `ready_at`, sem duplicar linha.
 *
 * Entidade JPA separada do domínio (regra de ouro): nenhuma `@Entity` no
 * pacote `domain`.
 */
@Entity
@Table(name = "round_readiness")
class RoundReadinessEntity(
    @EmbeddedId
    var id: RoundReadinessId = RoundReadinessId(),

    @Column(name = "ready_at", nullable = false)
    var readyAt: Instant = Instant.now(),
)

@Embeddable
data class RoundReadinessId(
    @Column(name = "iso_year")
    var isoYear: Int = 0,

    @Column(name = "iso_week")
    var isoWeek: Int = 0,

    @Column(name = "slot", length = 10)
    var slot: String = "",

    @Column(name = "user_id")
    var userId: Long = 0,
) : Serializable
