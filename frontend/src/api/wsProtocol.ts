// Valores de protocolo dos WebSockets que não cabem no schema das mensagens
// (close codes e motivos) — documentados na descrição das rotas `/ws/*` do
// contrato. Ficam num lugar só para que app, mocks MSW e testes não derivem
// do backend (issue #151).

/** Motivo do close quando o encaixe ainda não foi liberado pelo `playRound`. */
export const WS_REASON_NOT_STARTED = 'Encaixe nao liberado'

/**
 * Close code de `/ws/matches/{id}` para partida de encaixe não liberado.
 * O canal só leva `MatchEvent`, então a recusa vai no close (faixa 4000–4999
 * de aplicação; 4409 ecoa o HTTP 409 `ROUND_NOT_READY`). Em `/ws/slot/{key}`
 * a recusa é a mensagem `RoundNotStarted`, seguida de close 1000.
 */
export const WS_CLOSE_NOT_STARTED = 4409
