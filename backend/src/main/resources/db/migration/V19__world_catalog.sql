-- Mundo do MVP montado a partir do pack (issue #131).
-- Compatível com H2 (MODE=PostgreSQL) e Postgres.
--
-- O pack (`data/world/`) é a FONTE de um jogo novo; estas tabelas guardam o
-- que ESTE save está jogando. Mudar o pack depois não mexe num jogo em
-- andamento — a mesma ideia de gravar clubes e jogadores em vez de relê-los.

-- Países do save. A V16 guardava só o trigrama e resolvia nome/confederação
-- no catálogo fixo do domínio (`Countries`, 16 países). O pack traz ~100 (as
-- nacionalidades dos jogadores reais), então o catálogo passa a ser dado do
-- save, lido pelo mapper de persistência no lugar do catálogo fixo.
CREATE TABLE countries (
    code          VARCHAR(3)   PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    confederation VARCHAR(16)  NOT NULL,
    iso_alpha2    VARCHAR(16)  NOT NULL
);

-- Catálogo de competições do save (`GameCompetition`, port
-- `CompetitionCatalogRepository`). Uma linha por competição, na ordem do pack
-- (`sort_order`). O formato (sealed no domínio) vai como JSON de um registro
-- próprio da persistência — é lido e gravado inteiro, nunca consultado por SQL.
-- `kind` + `tier` espelham o `sealed CompetitionKind`: LEAGUE exige tier,
-- NATIONAL_CUP não tem.
CREATE TABLE competition_catalog (
    id                         VARCHAR(64)  PRIMARY KEY,
    sort_order                 INT          NOT NULL,
    name                       VARCHAR(120) NOT NULL,
    country                    VARCHAR(3)   NOT NULL,
    kind                       VARCHAR(16)  NOT NULL,
    tier                       INT,
    season_span                VARCHAR(16)  NOT NULL,
    pauses_on_fifa_windows     BOOLEAN      NOT NULL,
    format_json                TEXT         NOT NULL,
    tiebreakers                VARCHAR(255) NOT NULL,
    foreign_player_limit       INT,
    yellows_for_suspension     INT          NOT NULL,
    matches_per_red_card       INT          NOT NULL,
    resets_yellows_at_knockout BOOLEAN      NOT NULL
);
