-- Tabelas e histórico por competição do mundo (issue #131).
-- Compatível com H2 (MODE=PostgreSQL) e PostgreSQL.
--
-- Com o mundo do MVP cada liga (Série A, Série B, Premier League…) tem a SUA
-- tabela, na SUA temporada: o Brasil de 2027 convive com a Europa de 2026/27.
-- A PK deixa de ser (temporada, divisão) — a Série A e a Premier League são
-- ambas divisão 1 — e passa a ser (competição, temporada).
--
-- As tabelas são RECRIADAS (como na V7) para não depender do nome da
-- constraint de PK, que difere entre H2 e Postgres. O sufixo `_v20` (e não
-- `_new`) importa no Postgres: a PK da V7 ainda se chama `standings_new_pkey`,
-- e um índice com esse nome colidiria. Saves anteriores eram só
-- do Brasil: a divisão 1 vira a Série A e a 2 a Série B, com os ids do pack.

CREATE TABLE standings_v20 (
    competition_id   VARCHAR(64)  NOT NULL,
    season           INTEGER      NOT NULL,
    competition_name VARCHAR(255) NOT NULL,
    country_code     VARCHAR(3)   NOT NULL,
    season_label     VARCHAR(16)  NOT NULL,
    division         INTEGER      NOT NULL,
    round_number     INTEGER      NOT NULL,
    promotion_spots  INTEGER      NOT NULL DEFAULT 0,
    relegation_spots INTEGER      NOT NULL DEFAULT 0,
    rows_json        TEXT         NOT NULL,
    -- Placares já disputados na liga: insumo do confronto direto.
    results_json     TEXT         NOT NULL DEFAULT '[]',
    PRIMARY KEY (competition_id, season)
);

INSERT INTO standings_v20 (competition_id, season, competition_name, country_code, season_label, division,
                           round_number, promotion_spots, relegation_spots, rows_json)
SELECT CASE division WHEN 1 THEN 'bra-serie-a' WHEN 2 THEN 'bra-serie-b' ELSE 'bra-divisao-' || CAST(division AS VARCHAR(4)) END,
       season,
       CASE division WHEN 1 THEN 'Campeonato Brasileiro Série A' WHEN 2 THEN 'Campeonato Brasileiro Série B'
                     ELSE 'Divisão ' || CAST(division AS VARCHAR(4)) END,
       'BRA',
       CAST(season AS VARCHAR(16)),
       division,
       round_number,
       promotion_spots,
       relegation_spots,
       rows_json
FROM standings;

DROP TABLE standings;
ALTER TABLE standings_v20 RENAME TO standings;

-- Histórico: um record por (temporada, país). Os antigos eram do Brasil.
CREATE TABLE season_history_v20 (
    season       INTEGER    NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    record_json  TEXT       NOT NULL,
    PRIMARY KEY (season, country_code)
);

INSERT INTO season_history_v20 (season, country_code, record_json)
SELECT season, 'BRA', record_json FROM season_history;

DROP TABLE season_history;
ALTER TABLE season_history_v20 RENAME TO season_history;
