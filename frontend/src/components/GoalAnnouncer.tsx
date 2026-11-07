import type { MatchEvent } from '../domain/types'

interface Props {
  events: MatchEvent[]
  homeName: string
  awayName: string
  /**
   * Placar que a tela mostra. Vem de fora, e não da contagem dos `Goal` de
   * `events`, porque a `RoundPage` parte de um snapshot da rodada: quem entra
   * no meio da partida recebe só os eventos seguintes, e contar apenas eles
   * anunciaria um placar menor que o do cabeçalho.
   */
  homeGoals: number
  awayGoals: number
}

/**
 * Região `aria-live` invisível que anuncia só o último gol da partida
 * (issue #113): "Gol do Goal Father FC, 2 a 1, aos 63 minutos".
 *
 * Fica fora do feed de propósito: pôr `aria-live` no feed inteiro faria o
 * leitor de tela narrar cada chute para fora e cada cartão. A região precisa
 * existir no DOM antes do primeiro gol — leitores de tela só anunciam
 * mudanças em regiões que já estavam montadas — então ela é sempre
 * renderizada, vazia até sair o primeiro gol.
 */
export function GoalAnnouncer({ events, homeName, awayName, homeGoals, awayGoals }: Props) {
  // `findLast` estreita o tipo pelo predicado; o último gol é o mais recente,
  // então o placar atual é o placar logo depois dele.
  const lastGoal = events.findLast((e) => e.type === 'Goal')
  let message = ''
  if (lastGoal?.type === 'Goal') {
    const minutes = lastGoal.minute === 1 ? 'ao 1 minuto' : `aos ${lastGoal.minute} minutos`
    message = `Gol do ${lastGoal.home ? homeName : awayName}, ${homeGoals} a ${awayGoals}, ${minutes}`
  }

  return (
    <div className="sr-only" aria-live="polite" aria-atomic="true" data-testid="goal-announcer">
      {message}
    </div>
  )
}
