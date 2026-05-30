-- Estatísticas disciplinares por jogador (Fase 3.5.2).
-- Acumuladas entre partidas pelo PlayRoundService a partir dos eventos Card.
ALTER TABLE players ADD COLUMN yellow_cards INTEGER NOT NULL DEFAULT 0;
ALTER TABLE players ADD COLUMN red_cards    INTEGER NOT NULL DEFAULT 0;
