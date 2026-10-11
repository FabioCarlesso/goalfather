-- Copa mata-mata paralela ao campeonato (issue #61).
-- Compatível com H2 (MODE=PostgreSQL) e Postgres.

-- Uma copa por temporada. `cup_json` guarda a `Cup` inteira (fases sorteadas,
-- confrontos e resultados), mesmo trade-off de `standings.rows_json` e
-- `season_history.record_json`: a UI lê a chave toda de uma vez e nenhuma
-- consulta filtra por dentro dela. A linha é reescrita a cada fase disputada
-- — só por quem venceu o claim da rodada (issue #46), então dispensa lock.
CREATE TABLE cups (
    season   INTEGER NOT NULL,
    cup_json TEXT    NOT NULL,
    PRIMARY KEY (season)
);

-- O que cada data do calendário disputa: rodada de liga ou fase de copa
-- (`RoundStage`, sealed no domínio). Nulo nas rodadas gravadas antes da copa,
-- que o mapper lê como rodada de liga de mesmo número — exatamente o que elas
-- eram. Sem backfill.
ALTER TABLE rounds ADD COLUMN stage_json TEXT;
