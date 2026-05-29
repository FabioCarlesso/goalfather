// Estrutura de slots por formação — necessária na UI para renderizar
// o seletor de escalação (qual posição cada slot exige).
//
// TODO(contrato): mover para o OpenAPI quando o backend Kotlin expuser
// `GET /api/formations` (ou inline no enum como `x-slots`). Por ora,
// duplicamos aqui porque o frontend precisa saber a estrutura para
// renderizar o formulário antes do POST. O backend valida na escalação.

import type { Formation, Position } from './types'

export const FORMATIONS: readonly Formation[] = ['4-4-2', '4-3-3', '3-5-2', '5-3-2']

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
