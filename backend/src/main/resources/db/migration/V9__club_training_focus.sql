-- Treino semanal com foco escolhido pelo técnico (issue #58).
-- Compatível com H2 (MODE=PostgreSQL) e Postgres.

-- O foco vale entre rodadas e para o elenco inteiro, então é estado do CLUBE
-- (a escalação, que é a decisão da partida, continua no lineup_json). Ligas
-- existentes — e todo clube da IA — herdam o default de quem não escolheu.
ALTER TABLE clubs ADD COLUMN training_focus VARCHAR(16) NOT NULL DEFAULT 'DESCANSO';
