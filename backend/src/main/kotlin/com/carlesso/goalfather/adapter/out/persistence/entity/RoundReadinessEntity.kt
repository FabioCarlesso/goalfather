package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/**
 * Marcação de "técnico pronto" para uma rodada (issue #20). A PK composta
 * (round_number, user_id) torna o `markReady` idempotente: re-sinalizar só
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
    @Column(name = "round_number")
    var roundNumber: Int = 0,

    @Column(name = "user_id")
    var userId: Long = 0,
) : Serializable
