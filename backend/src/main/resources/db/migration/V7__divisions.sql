-- Múltiplas divisões com promoção/rebaixamento (issue #47).
-- Compatível com H2 (MODE=PostgreSQL) e Postgres.

-- Clube pertence a uma divisão; ligas existentes viram "divisão única" (1).
ALTER TABLE clubs ADD COLUMN division INTEGER NOT NULL DEFAULT 1;

-- Standings passa a ser 1 linha por (temporada, divisão). Recriar a tabela
-- evita depender do nome auto-gerado da constraint de PK (que difere entre
-- H2 e Postgres). As vagas de promoção/rebaixamento são persistidas junto
-- do snapshot (0 = divisão única histórica: sem zona).
CREATE TABLE standings_new (
    season           INTEGER NOT NULL,
    division         INTEGER NOT NULL DEFAULT 1,
    round_number     INTEGER NOT NULL,
    promotion_spots  INTEGER NOT NULL DEFAULT 0,
    relegation_spots INTEGER NOT NULL DEFAULT 0,
    rows_json        TEXT    NOT NULL,
    PRIMARY KEY (season, division)
);

INSERT INTO standings_new (season, division, round_number, rows_json)
SELECT season, 1, round_number, rows_json FROM standings;

DROP TABLE standings;
ALTER TABLE standings_new RENAME TO standings;
