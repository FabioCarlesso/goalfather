package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version

/**
 * Entidade JPA do clube. Separada do `Club` de domínio — a regra
 * de ouro de CLAUDE.md: nada em domain conhece JPA.
 *
 * `lineupJson`: serialização da escalação salva (formação + ordem dos
 * 11 IDs). Persistir como TEXT funciona em qualquer DB (H2/Postgres).
 * Conversão domínio↔JSON é feita no mapper.
 */
@Entity
@Table(name = "clubs")
class ClubEntity(
    @Id
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "name", nullable = false)
    var name: String = "",

    @Column(name = "cash", nullable = false)
    var cash: Long = 0,

    @Column(name = "stadium_capacity", nullable = false)
    var stadiumCapacity: Int = 0,

    @Column(name = "lineup_json", columnDefinition = "TEXT")
    var lineupJson: String? = null,

    @Column(name = "owner_id")
    var ownerId: Long? = null,

    /** Divisão (tier) do clube na temporada corrente (issue #47). */
    @Column(name = "division", nullable = false)
    var division: Int = 1,

    /**
     * Foco de treino da semana (issue #58). Gravado como texto — o enum de
     * domínio não desce até a camada JPA (regra de ouro do CLAUDE.md); a
     * tradução acontece no mapper, e um valor desconhecido no banco cai no
     * default em vez de derrubar a leitura do clube.
     */
    @Column(name = "training_focus", nullable = false, length = 16)
    var trainingFocus: String = "DESCANSO",

    /**
     * Lock otimista (issue #19): o Hibernate incrementa `version` a cada
     * update e rejeita o save se a versão lida não bate com a do banco. Dois
     * `claim` simultâneos no mesmo clube → o segundo leva
     * `OptimisticLockException` → traduzido em 409.
     */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
)
