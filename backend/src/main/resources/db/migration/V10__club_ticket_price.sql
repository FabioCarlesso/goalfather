-- Preço de ingresso definido pelo técnico (issue #59).
-- Compatível com H2 (MODE=PostgreSQL) e Postgres.

-- O preço vale entre rodadas e para o estádio do clube, então é estado do
-- CLUBE — igual ao foco de treino (V9). O default é o preço fixo que
-- `FinanceRules` praticava antes desta issue (R$ 50), para que ligas
-- existentes e todo clube da IA continuem com a bilheteria de sempre.
ALTER TABLE clubs ADD COLUMN ticket_price_cents BIGINT NOT NULL DEFAULT 5000;
