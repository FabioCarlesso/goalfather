-- Histórico de temporadas (issue #60).
-- Compatível com H2 (MODE=PostgreSQL) e Postgres.

-- Tabela APPEND-ONLY: uma linha por temporada encerrada, gravada na virada
-- e nunca mais tocada. A PK por `season` é a garantia de que duas réplicas
-- disputando a virada (issue #46) não gravam dois campeões para o mesmo ano —
-- a segunda colide e o adapter traduz a colisão em "já registrado".
--
-- `record_json` guarda o SeasonRecord inteiro (campeão, artilheiro e a
-- classificação final de todas as divisões), mesmo trade-off do
-- `standings.rows_json`: nenhuma consulta filtra por dentro do snapshot, e
-- normalizar pagaria joins por leitura sem ganhar nada.
CREATE TABLE season_history (
    season      INTEGER NOT NULL,
    record_json TEXT    NOT NULL,
    PRIMARY KEY (season)
);
