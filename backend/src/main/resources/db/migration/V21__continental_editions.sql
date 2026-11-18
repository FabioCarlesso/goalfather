-- Competições continentais (issue #132): a Libertadores com fase de grupos e
-- mata-mata. Compatível com H2 (MODE=PostgreSQL) e Postgres.

-- Uma edição por (competição, temporada). `edition_json` guarda a
-- `ContinentalCup` inteira (grupos sorteados, resultados de grupo, o mata-mata
-- e a sede da final), o mesmo trade-off de `cups.cup_json`: a UI lê a edição
-- de uma vez e nenhuma consulta filtra por dentro dela. A linha é reescrita a
-- cada encaixe disputado — só por quem venceu o claim do encaixe (issue #46),
-- então dispensa lock.
CREATE TABLE continental_editions (
    competition_id VARCHAR(64) NOT NULL,
    season         INTEGER     NOT NULL,
    edition_json   TEXT        NOT NULL,
    PRIMARY KEY (competition_id, season)
);
