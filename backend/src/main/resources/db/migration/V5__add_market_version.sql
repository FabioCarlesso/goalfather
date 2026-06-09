-- Lock otimista no mercado (issue #21).
-- Compatível com H2 (MODE=PostgreSQL) e PostgreSQL.
--
-- Em liga multi-usuário dois humanos podem tentar comprar o mesmo jogador ao
-- mesmo tempo. A coluna `version` habilita o lock otimista do Hibernate na
-- entidade MarketEntryEntity: o vencedor remove a entrada do mercado; o
-- perdedor leva OptimisticLockException no commit, traduzida em
-- TransferResult.PlayerNotAvailable. Mesma estratégia do claim de clube (V3).
ALTER TABLE market_entries ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
