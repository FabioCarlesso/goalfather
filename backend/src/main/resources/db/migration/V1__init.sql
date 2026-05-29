-- GoalFather — schema inicial. Compatível com H2 (MODE=PostgreSQL) e Postgres.

CREATE TABLE clubs (
    id                BIGINT       PRIMARY KEY,
    name              VARCHAR(120) NOT NULL,
    cash              BIGINT       NOT NULL,
    stadium_capacity  INTEGER      NOT NULL,
    lineup_json       TEXT         NULL,
    owner_id          BIGINT       NULL
);

CREATE TABLE players (
    id         BIGINT       PRIMARY KEY,
    club_id    BIGINT       NULL,
    name       VARCHAR(120) NOT NULL,
    position   VARCHAR(2)   NOT NULL,
    overall    INTEGER      NOT NULL,
    pace       INTEGER      NOT NULL,
    shooting   INTEGER      NOT NULL,
    passing    INTEGER      NOT NULL,
    defending  INTEGER      NOT NULL,
    stamina    INTEGER      NOT NULL,
    salary     INTEGER      NOT NULL,
    age        INTEGER      NOT NULL,
    goals      INTEGER      NOT NULL,
    injured    BOOLEAN      NOT NULL
);

CREATE INDEX idx_players_club_id ON players(club_id);

CREATE TABLE market_entries (
    player_id BIGINT PRIMARY KEY,
    price     BIGINT NOT NULL
);

CREATE TABLE rounds (
    number       INTEGER     PRIMARY KEY,
    season       INTEGER     NOT NULL,
    status       VARCHAR(20) NOT NULL,
    matches_json TEXT        NOT NULL
);

CREATE TABLE standings (
    season       INTEGER PRIMARY KEY,
    round_number INTEGER NOT NULL,
    rows_json    TEXT    NOT NULL
);
