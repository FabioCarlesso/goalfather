-- Suspensão por cartões por competição (issue #130).
-- Compatível com H2 (MODE=PostgreSQL) e Postgres.

-- Amarelos pendentes e suspensões são POR competição: um mapa
-- competição → contador e uma lista de ganchos. Guardados como JSON numa
-- coluna TEXT (mesma escolha do `clubs.lineup_json`): são lidos e gravados
-- sempre junto do jogador, nunca consultados por SQL, e uma tabela filha
-- custaria uma junção a cada leitura de elenco.
--
-- NULL = ficha limpa (nenhum amarelo pendente, nenhuma suspensão) — o estado
-- de todo jogador anterior à issue, então não há backfill.
ALTER TABLE players ADD COLUMN discipline_json TEXT;
