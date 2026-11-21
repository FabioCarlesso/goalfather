-- Termos pessoais e renovação (issue #138).
-- Compatível com H2 (MODE=PostgreSQL) e Postgres.

-- Os termos que o jogador aceitou na proposta entre técnicos: o aceite assina
-- exatamente esse contrato. Nulos numa proposta gravada antes desta migration
-- (o aceite dela transfere o contrato antigo, como na #136). As três colunas
-- andam juntas: `contract_salary` nulo = proposta sem termos.
ALTER TABLE transfer_offers ADD COLUMN contract_salary BIGINT;
ALTER TABLE transfer_offers ADD COLUMN contract_years INT;
ALTER TABLE transfer_offers ADD COLUMN contract_release_clause BIGINT;

-- Esfriamento da renovação: depois de uma oferta ofensiva, o jogador não ouve
-- outra proposta do clube até `cooldown_until` (exclusive), na data do jogo.
-- Uma linha por jogador × clube: trocar de clube zera a conversa. Sem FK em
-- `player_id`, pelo motivo da `transfer_offers`: aposentado é apagado de
-- `players`, e a linha órfã não atrapalha ninguém.
CREATE TABLE contract_talks (
    player_id      BIGINT NOT NULL,
    club_id        BIGINT NOT NULL,
    cooldown_until DATE   NOT NULL,
    PRIMARY KEY (player_id, club_id)
);
