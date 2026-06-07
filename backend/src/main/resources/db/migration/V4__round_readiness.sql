-- Sincronização de rodadas em liga compartilhada (issue #20).
-- Compatível com H2 (MODE=PostgreSQL) e PostgreSQL.
--
-- Cada técnico humano (dono de clube) sinaliza "pronto" por rodada. A rodada
-- só simula quando TODOS sinalizaram, dando tempo de escalar antes do apito.
-- PK composta (round_number, user_id) torna o "marcar pronto" idempotente.
CREATE TABLE round_readiness (
    round_number INTEGER   NOT NULL,
    user_id      BIGINT    NOT NULL,
    ready_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (round_number, user_id)
);
