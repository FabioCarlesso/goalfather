-- Fadiga e lesões com duração (issue #54).
-- Compatível com H2 (MODE=PostgreSQL) e Postgres.

-- A lesão deixa de ser um booleano eterno e passa a ter duração em rodadas.
-- 0 = apto. O par (injured, duração) permitiria estados sem sentido, então a
-- coluna booleana é substituída, não somada: quem estava lesionado herda uma
-- rodada de afastamento e volta na virada seguinte.
ALTER TABLE players ADD COLUMN injured_for_rounds INTEGER NOT NULL DEFAULT 0;

UPDATE players SET injured_for_rounds = 1 WHERE injured = TRUE;

ALTER TABLE players DROP COLUMN injured;
