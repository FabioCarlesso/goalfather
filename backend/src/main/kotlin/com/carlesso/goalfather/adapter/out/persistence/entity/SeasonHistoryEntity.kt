package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Uma temporada encerrada (issue #60). 1 linha por temporada — a PK por
 * `season` é o que torna a tabela **append-only na prática**: a segunda
 * tentativa de gravar a mesma temporada colide, mesmo vinda de outra réplica.
 *
 * `record_json` carrega o `SeasonRecord` inteiro serializado, mesmo trade-off
 * do `standings.rows_json`: nenhuma consulta filtra por campeão ou artilheiro
 * (a UI lê a temporada toda), e normalizar aqui pagaria joins por leitura sem
 * ganhar nada. Se um dia existir "ranking de artilheiros de todos os tempos",
 * aí sim vale extrair colunas.
 *
 * Sem `@Version`: linha que nunca é atualizada não tem o que travar.
 */
@Entity
@Table(name = "season_history")
class SeasonHistoryEntity(
    @Id
    @Column(name = "season")
    var season: Int = 0,

    @Column(name = "record_json", columnDefinition = "TEXT", nullable = false)
    var recordJson: String = "{}",
)
