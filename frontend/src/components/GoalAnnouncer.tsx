import type { MatchEvent } from '../domain/types'

interface Props {
  events: MatchEvent[]
  homeName: string
  awayName: string
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
export function GoalAnnouncer({ events, homeName, awayName }: Props) {
  let home = 0
  let away = 0
  let message = ''
  for (const e of events) {
    if (e.type !== 'Goal') continue
    if (e.home) home++
    else away++
    const minutes = e.minute === 1 ? 'ao 1 minuto' : `aos ${e.minute} minutos`
    message = `Gol do ${e.home ? homeName : awayName}, ${home} a ${away}, ${minutes}`
  }

  return (
    <div className="sr-only" aria-live="polite" aria-atomic="true" data-testid="goal-announcer">
      {message}
    </div>
  )
}
