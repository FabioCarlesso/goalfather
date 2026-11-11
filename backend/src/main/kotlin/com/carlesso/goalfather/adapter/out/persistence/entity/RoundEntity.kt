package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.io.Serializable

/**
 * Entidade JPA dos jogos de um encaixe. Separada do `Round` de domínio —
 * regra de ouro do CLAUDE.md: nada em `domain` conhece JPA.
 *
 * PK = o encaixe (ano ISO + semana + slot, issue #125). A PK antiga era só o
 * número da rodada, que se repetia a cada temporada — por isso a virada
 * reescrevia a linha 1 e o claim precisava comparar `season` para não
 * aplicar efeitos da temporada errada. O encaixe não se repete nunca.
 */
@Entity
@Table(name = "rounds")
class RoundEntity(
    @EmbeddedId
    var id: MatchdayKey = MatchdayKey(),

    @Column(name = "season", nullable = false)
    var season: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: RoundStatusEnum = RoundStatusEnum.Scheduled,

    @Column(name = "matches_json", columnDefinition = "TEXT", nullable = false)
    var matchesJson: String = "[]",

    /**
     * Lock otimista (issue #46): o Hibernate emite
     * `UPDATE ... WHERE <encaixe> = ? AND version = ?` e rejeita o commit se
     * outra instância já avançou a versão. É o que torna a transição de status
     * atômica ENTRE JVMs — o `Mutex` in-JVM anterior só protegia um processo.
     * Mesma mecânica do clube (#19) e do mercado (#21).
     */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
)

enum class RoundStatusEnum { Scheduled, InProgress, Finished }

/**
 * Chave do encaixe nas tabelas `rounds` e `round_readiness` (issue #125).
 * `slot` gravado como texto (`MIDWEEK`/`WEEKEND`): a ordem ALFABÉTICA coincide
 * com a do tempo dentro da semana, então `ORDER BY iso_year, iso_week, slot`
 * é a ordem cronológica sem coluna extra.
 *
 * `data class` + `Serializable` (java.io): o JPA exige `equals`/`hashCode` por
 * valor numa chave composta, e a `data class` os gera.
 */
@Embeddable
data class MatchdayKey(
    @Column(name = "iso_year", nullable = false)
    var isoYear: Int = 0,

    @Column(name = "iso_week", nullable = false)
    var isoWeek: Int = 0,

    @Column(name = "slot", nullable = false, length = 10)
    var slot: String = "",
) : Serializable
