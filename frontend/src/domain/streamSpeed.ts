import type { StreamSpeed } from './types'

// Velocidade da transmissão ao vivo (issue #115). Os valores vêm do contrato
// (`StreamSpeed`); aqui ficam só a ordem de exibição e os rótulos — quem
// decide o ritmo de verdade é o servidor (ou o mock MSW).
export const STREAM_SPEEDS: readonly StreamSpeed[] = ['SLOW', 'NORMAL', 'FAST', 'INSTANT']

export const STREAM_SPEED_LABEL: Record<StreamSpeed, string> = {
  SLOW: 'Lenta',
  NORMAL: 'Normal',
  FAST: 'Rápida',
  INSTANT: 'Instantânea',
}

export const DEFAULT_STREAM_SPEED: StreamSpeed = 'NORMAL'

export const isStreamSpeed = (value: unknown): value is StreamSpeed =>
  typeof value === 'string' && (STREAM_SPEEDS as readonly string[]).includes(value)
