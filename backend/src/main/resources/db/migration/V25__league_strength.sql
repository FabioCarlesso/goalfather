-- Força da liga no catálogo do save (issue #135).
-- Compatível com H2 (MODE=PostgreSQL) e Postgres.
--
-- É o multiplicador de liga do valor de mercado (`ValuationRules`): o mesmo
-- overall vale mais na Premier League do que na Série B. Vem do pack
-- (`competitions.json`, campo `strength`), gravado quando o mundo nasce, como
-- o resto do catálogo. Nula em copa e continental (não têm força própria) e
-- em save anterior à issue — o domínio cai na força de referência do mercado.
ALTER TABLE competition_catalog ADD COLUMN league_strength INT;
