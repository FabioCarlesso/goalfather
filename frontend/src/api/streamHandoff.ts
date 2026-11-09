// "Pular para o fim" (issue #115) sem mensagem cliente → servidor: abre-se
// uma conexão NOVA com `speed=INSTANT`, que reprocessa o stream desde o início
// (é determinístico), e a antiga é aposentada.
//
// A antiga só fecha quando a nova termina. No backend real, o stream de uma
// conexão fechada segue até o fim sem pacing e é quem chega primeiro que
// encerra a rodada — e só ele recebe o `RoundFinished` completo (finanças,
// treino, `SeasonFinished`); o outro recebe apenas o replay com a tabela.
// Mantendo a antiga aberta (e pausada pelo ritmo dela), a nova vence a corrida.
//
// Enquanto isso a antiga fica muda: os handlers saem já, para que nem as
// mensagens dela nem o seu `close` mexam no estado da tela.
export function handOff(previous: WebSocket | null, next: WebSocket): void {
  if (!previous || previous === next) return
  previous.onopen = null
  previous.onmessage = null
  previous.onerror = null
  previous.onclose = null
  // `addEventListener` (e não `onclose`) para não brigar com o handler que a
  // tela registra na nova conexão.
  next.addEventListener('close', () => previous.close(), { once: true })
}
