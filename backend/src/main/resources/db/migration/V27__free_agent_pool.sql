-- Pool de livres (issue #139). Compatível com H2 (MODE=PostgreSQL) e Postgres.

-- Viradas do mundo que o livre passou sem clube: depois de
-- FREE_AGENT_IDLE_SEASONS_TO_RETIRE viradas ele se aposenta. Quem já está no mercado num
-- save antigo começa do zero.
ALTER TABLE market_entries ADD COLUMN seasons_without_club INT NOT NULL DEFAULT 0;
