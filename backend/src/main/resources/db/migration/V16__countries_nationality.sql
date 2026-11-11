-- País do clube e nacionalidade do jogador (issue #123).
-- Compatível com H2 (MODE=PostgreSQL) e Postgres.

-- Guardamos só o trigrama FIFA (`BRA`, `ARG`, `ENG`). Nome e confederação são
-- dado do catálogo de países (hoje `Countries` no domínio; depois, o pack do
-- mundo da issue #126), resolvidos no mapper. Uma tabela `countries` com FK
-- duplicaria o catálogo no banco e teria de ser mantida em sincronia com ele.
--
-- Backfill = 'BRA': todo clube e jogador anterior à issue era do seed
-- brasileiro, e o domínio não aceita clube sem país.
ALTER TABLE clubs ADD COLUMN country VARCHAR(3) DEFAULT 'BRA' NOT NULL;
ALTER TABLE players ADD COLUMN nationality VARCHAR(3) DEFAULT 'BRA' NOT NULL;

-- Segunda nacionalidade é opcional de verdade: NULL = não tem.
ALTER TABLE players ADD COLUMN second_nationality VARCHAR(3);
