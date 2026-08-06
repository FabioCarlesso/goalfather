// Estrutura de slots por formação — necessária na UI para renderizar
// o seletor de escalação (qual posição cada slot exige).
//
// TODO(contrato): mover para o OpenAPI quando o backend Kotlin expuser
// `GET /api/formations` (ou inline no enum como `x-slots`). Por ora,
// duplicamos aqui porque o frontend precisa saber a estrutura para
// renderizar o formulário antes do POST. O backend valida na escalação.

import type { Formation, Position, Posture } from './types'

export const FORMATIONS: readonly Formation[] = ['4-4-2', '4-3-3', '3-5-2', '5-3-2']

// Postura da partida (issue #56). Só rótulo e ordem de exibição vivem aqui —
// os multiplicadores são regra de domínio e ficam no backend (`enum class
// Posture`), espelhados apenas pela engine de mock em `src/mocks/engine.ts`.
export const POSTURES: readonly Posture[] = ['DEFENSIVE', 'BALANCED', 'OFFENSIVE']

export const POSTURE_LABEL: Record<Posture, string> = {
  DEFENSIVE: 'Defensiva',
  BALANCED: 'Equilibrada',
  OFFENSIVE: 'Ofensiva',
}

export const POSTURE_HINT: Record<Posture, string> = {
  DEFENSIVE: 'Cria menos, mas concede bem menos — para segurar time mais forte.',
  BALANCED: 'Sem ajuste: o placar é decidido por elenco e formação.',
  OFFENSIVE: 'Jogo aberto: você faz mais gols e também toma mais.',
}

export const FORMATION_SLOTS: Record<Formation, readonly Position[]> = {
  '4-4-2': ['GK', 'CB', 'CB', 'CB', 'CB', 'MF', 'MF', 'MF', 'MF', 'FW', 'FW'],
  '4-3-3': ['GK', 'CB', 'CB', 'CB', 'CB', 'MF', 'MF', 'MF', 'FW', 'FW', 'FW'],
  '3-5-2': ['GK', 'CB', 'CB', 'CB', 'MF', 'MF', 'MF', 'MF', 'MF', 'FW', 'FW'],
  '5-3-2': ['GK', 'CB', 'CB', 'CB', 'CB', 'CB', 'MF', 'MF', 'MF', 'FW', 'FW'],
}

export const POSITION_LABEL: Record<Position, string> = {
  GK: 'Goleiro',
  CB: 'Zagueiro',
  MF: 'Meio-campo',
  FW: 'Atacante',
}

export const POSITION_ABBR: Record<Position, string> = {
  GK: 'GL',
  CB: 'ZG',
  MF: 'MC',
  FW: 'AT',
}
