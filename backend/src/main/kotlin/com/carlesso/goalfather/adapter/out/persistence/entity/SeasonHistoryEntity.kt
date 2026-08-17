package com.carlesso.goalfather.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.domain.Persistable

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
 *
 * **Por que `Persistable`** (achado da review do PR #76). O `save` do Spring
 * Data escolhe entre `persist` e `merge` pelo `isNew()`, e o default olha para
 * o id: `season` é atribuído por nós e nunca é nulo/zero, então a entidade
 * parecia SEMPRE existente e todo `save` virava `merge` — um SELECT seguido de
 * UPDATE. O efeito é que a PK jamais era violada: gravar a mesma temporada duas
 * vezes sobrescrevia a primeira em silêncio, e o `catch` de violação no adapter
 * era código morto. Exatamente o oposto de append-only.
 *
 * Declarando `isNew() = true` o `save` volta a emitir INSERT, e a PK passa a
 * fazer o trabalho que a issue pede: com duas réplicas, quem perder a corrida
 * colide em vez de reescrever a história. `true` fixo é honesto aqui porque
 * este agregado só conhece uma escrita — a de criação (o port
 * `SeasonHistoryRepository` não tem `save` nem `delete`).
 */
@Entity
@Table(name = "season_history")
class SeasonHistoryEntity(
    @Id
    @Column(name = "season")
    var season: Int = 0,

    @Column(name = "record_json", columnDefinition = "TEXT", nullable = false)
    var recordJson: String = "{}",
) : Persistable<Int> {

    override fun getId(): Int = season

    // Não precisa de @Transient: `@Id` está no CAMPO, então o access type é
    // FIELD e o Hibernate ignora métodos ao mapear a entidade.
    override fun isNew(): Boolean = true
}
