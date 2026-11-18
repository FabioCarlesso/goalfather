-- Seleções nas datas FIFA (issue #134).
-- Compatível com H2 (MODE=PostgreSQL) e Postgres.

-- Convocado: a variante `Availability.OnInternationalDuty(country, until)` do
-- domínio. As duas colunas andam juntas (as duas nulas = não convocado), como
-- o `injured_for_rounds` faz com a lesão. Sem backfill: ninguém estava
-- convocado antes da issue.
ALTER TABLE players ADD COLUMN duty_country VARCHAR(3);
ALTER TABLE players ADD COLUMN duty_until DATE;

-- Uma linha por data FIFA já convocada (janela futura não é gravada: a prévia
-- da lista sai da regra pura). `status` repete a variante do JSON só para
-- leitura humana/SQL; o domínio lê o `window_json` (`NationalWindow`, com as
-- listas e, depois de jogada, os placares). Classificação das Eliminatórias e
-- ranking NÃO são gravados: saem dos placares a cada leitura.
CREATE TABLE national_windows (
    start_date  DATE        PRIMARY KEY,
    end_date    DATE        NOT NULL,
    status      VARCHAR(16) NOT NULL,
    window_json TEXT        NOT NULL
);
