// Canal "sessão expirou" (issue #28). Quando um `/api/**` responde 401 fora do
// fluxo de login, o `client.ts` emite aqui e o `AuthProvider` reage forçando
// logout → as guardas de rota levam a `/login`.
//
// Por que um pub/sub minúsculo em vez de chamar o Context direto? A camada de
// rede (`client.ts`) é pura e não pode importar React/Context sem inverter a
// dependência. Um `Set<Listener>` desacopla: a rede só sinaliza, quem reage é o
// provider. É o equivalente TS de um port/observer — sem trazer uma lib de
// eventos só para isso.

type Listener = () => void

const listeners = new Set<Listener>()

/** Inscreve um ouvinte e devolve a função de cancelamento (para o cleanup do efeito). */
export function onSessionExpired(listener: Listener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** Notifica todos os ouvintes que a sessão expirou (401 inesperado). */
export function emitSessionExpired(): void {
  listeners.forEach((listener) => listener())
}
