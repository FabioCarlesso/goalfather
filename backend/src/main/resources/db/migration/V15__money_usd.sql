-- Economia em dólar (issue #122). Compatível com H2 (MODE=PostgreSQL) e Postgres.
--
-- A unidade de todo dinheiro persistido passa de centavos de REAL para
-- centavos de DÓLAR. Os valores do jogo foram rebalanceados dividindo tudo por
-- uma taxa fixa de referência (R$ 5 = US$ 1), o que mantém as proporções da
-- economia (caixa, folha, preços, bilheteria, prêmios) exatamente como antes.
--
-- Um save existente precisa da mesma conversão: sem ela, um caixa de
-- R$ 800 mil seria lido como US$ 800 mil — cinco vezes o dinheiro do seed novo.
-- Num banco novo as tabelas estão vazias e este script não faz nada; o seed
-- já nasce em dólar.
--
-- Fica de fora o histórico em JSON (o JSON das rodadas em `rounds`): é registro
-- do passado, não estado que a economia lê, e reescrever JSON em SQL portável
-- entre H2 e Postgres não compensa.

UPDATE clubs SET cash = cash / 5, ticket_price_cents = ticket_price_cents / 5;

UPDATE players SET salary = salary / 5;

UPDATE market_entries SET price = price / 5;

UPDATE transfer_offers SET amount = amount / 5;

-- O default da coluna acompanha o novo `DEFAULT_TICKET_PRICE_CENTS` (US$ 10).
ALTER TABLE clubs ALTER COLUMN ticket_price_cents SET DEFAULT 1000;
