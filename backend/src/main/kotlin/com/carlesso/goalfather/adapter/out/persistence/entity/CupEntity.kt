package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Copa de uma temporada (issue #61). Separada da `Cup` de domínio — regra de
 * ouro do CLAUDE.md. A chave inteira viaja em `cup_json` (ver V12).
 *
 * Diferente de `SeasonHistoryEntity`, esta linha É atualizada (cada fase
 * disputada grava os resultados), então o `save` padrão do Spring Data —
 * `merge` para id atribuído — é o comportamento certo: cria na primeira vez e
 * substitui depois.
 */
@Entity
@Table(name = "cups")
class CupEntity(
    @Id
    @Column(name = "season")
    var season: Int = 0,

    @Column(name = "cup_json", columnDefinition = "TEXT", nullable = false)
    var cupJson: String = "{}",
)
