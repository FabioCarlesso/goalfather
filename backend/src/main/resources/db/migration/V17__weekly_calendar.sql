-- Calendário semanal com dois encaixes (issue #125).
-- Compatível com H2 (MODE=PostgreSQL) e PostgreSQL.
--
-- A "rodada" deixa de ser um número global que recomeçava a cada temporada e
-- passa a ser um ENCAIXE: ano ISO + semana ISO + slot (MIDWEEK/WEEKEND). O
-- encaixe nunca se repete, então vira a PK — a antiga PK `number` obrigava a
-- virada a reescrever a linha 1 e o claim a comparar `season`.
--
-- As tabelas são RECRIADAS, não migradas: um número de rodada não diz em que
-- semana ela cai (isso é decisão do agendador, que roda em Kotlin). Um banco
-- com save em andamento sobe sem encaixe corrente e o boot (DataInitializer)
-- reinicia a temporada corrente no calendário novo — documentado lá. Em dev
-- com Docker, recriar o volume do Postgres também resolve.
--
-- `stage_json` (issue #61) some: o que se disputa passou a ser dado de CADA
-- partida, dentro de `matches_json`, porque um encaixe pode juntar liga e copa.
DROP TABLE round_readiness;
DROP TABLE rounds;

CREATE TABLE rounds (
    iso_year     INTEGER     NOT NULL,
    iso_week     INTEGER     NOT NULL,
    slot         VARCHAR(10) NOT NULL,
    season       INTEGER     NOT NULL,
    status       VARCHAR(20) NOT NULL,
    matches_json TEXT        NOT NULL,
    version      BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (iso_year, iso_week, slot)
);

-- Prontidão multiplayer (issue #20) por encaixe.
CREATE TABLE round_readiness (
    iso_year INTEGER     NOT NULL,
    iso_week INTEGER     NOT NULL,
    slot     VARCHAR(10) NOT NULL,
    user_id  BIGINT      NOT NULL,
    ready_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (iso_year, iso_week, slot, user_id)
);

-- Prazo das propostas entre técnicos (issue #63) passa a contar ENCAIXES
-- jogados, não rodadas. O valor guardado continua válido: cada rodada antiga
-- era exatamente um clique de "jogar", como cada encaixe agora.
ALTER TABLE transfer_offers RENAME COLUMN rounds_left TO slots_left;
