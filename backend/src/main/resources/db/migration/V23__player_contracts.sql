-- Contratos com data de término (issue #136).
-- Compatível com H2 (MODE=PostgreSQL) e Postgres.

-- O vínculo `Employment.UnderContract(clubId, salary, start, end, releaseClause)`
-- do domínio: `club_id` e `salary` já existiam; entram as datas e a cláusula.
-- As três nulas = livre (`club_id` nulo). Mesma postura da V22 com a convocação:
-- o banco não tem soma de tipos, o mapper reconstrói o `sealed`.
ALTER TABLE players ADD COLUMN contract_start DATE;
ALTER TABLE players ADD COLUMN contract_end DATE;
ALTER TABLE players ADD COLUMN release_clause BIGINT;

-- Backfill: todo jogador que já está num elenco ganha um CONTRATO PADRÃO, para o
-- save em andamento não perder ninguém na primeira virada. Padrão = 3
-- temporadas a contar da temporada corrente do país do clube (a maior das
-- tabelas dele; 2026 num save sem tabela), terminando no fim de temporada da
-- liga: 31/12 no ano civil, 31/05 nas ligas da UEFA (temporada 2026 = 2026/27,
-- que termina em maio de 2027). O salário que o jogador já tinha vira o do
-- contrato, sem recálculo. Livre do mercado (club_id nulo) fica sem contrato e
-- passa a valer salário zero — quem não tem clube não recebe.
--
-- A lista UEFA é a do catálogo `Countries` do domínio; o pack do MVP só tem
-- ligas europeias desses países. Os valores ficam explícitos no SQL (e não numa
-- função do banco) para a migration rodar igual no H2 e no Postgres.
UPDATE players
SET contract_start = CAST(
        CAST(COALESCE((SELECT MAX(s.season) FROM standings s
                       WHERE s.country_code = (SELECT c.country FROM clubs c WHERE c.id = players.club_id)), 2026)
             AS VARCHAR(4)) || '-01-01' AS DATE),
    contract_end = CASE
        WHEN (SELECT c.country FROM clubs c WHERE c.id = players.club_id) IN ('ENG', 'ESP', 'ITA', 'GER', 'FRA', 'POR')
        THEN CAST(
            CAST(COALESCE((SELECT MAX(s.season) FROM standings s
                           WHERE s.country_code = (SELECT c.country FROM clubs c WHERE c.id = players.club_id)), 2026) + 3
                 AS VARCHAR(4)) || '-05-31' AS DATE)
        ELSE CAST(
            CAST(COALESCE((SELECT MAX(s.season) FROM standings s
                           WHERE s.country_code = (SELECT c.country FROM clubs c WHERE c.id = players.club_id)), 2026) + 2
                 AS VARCHAR(4)) || '-12-31' AS DATE)
    END
WHERE club_id IS NOT NULL;

UPDATE players SET salary = 0 WHERE club_id IS NULL;
