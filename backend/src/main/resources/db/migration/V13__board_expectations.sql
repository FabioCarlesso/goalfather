-- Metas da diretoria (issue #62).
-- Compatível com H2 (MODE=PostgreSQL) e Postgres.

-- A meta da temporada e a confiança no técnico são estado do CLUBE, como o
-- foco de treino (V9) e o preço do ingresso (V10): andam a cada rodada, dentro
-- da mesma gravação do clube. Colunas nulas = save anterior à issue; a meta é
-- fixada na primeira rodada que o clube disputar (ou na próxima virada).
-- Sem backfill: a meta depende da força de TODOS os elencos da divisão, que é
-- regra de domínio (`boardExpectations`), não SQL.
ALTER TABLE clubs ADD COLUMN board_season INTEGER;
ALTER TABLE clubs ADD COLUMN board_objective VARCHAR(16);
ALTER TABLE clubs ADD COLUMN board_target_position INTEGER;
ALTER TABLE clubs ADD COLUMN board_confidence INTEGER;

-- Demissão mais recente do técnico (o "game over" da v1). `dismissal_json`
-- guarda o `Dismissal` inteiro — mesmo trade-off de `lineup_json`: é um valor
-- lido de uma vez só e nenhuma consulta filtra por dentro dele.
ALTER TABLE users ADD COLUMN dismissal_json TEXT;
