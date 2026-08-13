// Rótulos e textos de ajuda do treino semanal (issue #58).
//
// SÓ apresentação. Os números (chance de evolução, recuperação de stamina,
// risco de lesão) são regra de domínio e vivem no backend
// (`enum class TrainingFocus`), espelhados apenas pela engine de mock em
// `src/mocks/engine.ts` — mesma separação de `POSTURE_LABEL`/`POSTURE_HINT`.

import type { TrainedAttribute, TrainingFocus } from './types'

/** Ordem de exibição: do treino que mais desenvolve ao que mais poupa. */
export const TRAINING_FOCUSES: readonly TrainingFocus[] = ['ATAQUE', 'DEFESA', 'FISICO', 'DESCANSO']

export const TRAINING_FOCUS_LABEL: Record<TrainingFocus, string> = {
  ATAQUE: 'Ataque',
  DEFESA: 'Defesa',
  FISICO: 'Físico',
  DESCANSO: 'Descanso',
}

export const TRAINING_FOCUS_HINT: Record<TrainingFocus, string> = {
  ATAQUE: 'Chance de evoluir a finalização. Não devolve forma física e machuca de vez em quando.',
  DEFESA: 'Chance de evoluir a marcação. Não devolve forma física e machuca de vez em quando.',
  FISICO: 'Recupera parte da forma física e evolui velocidade — mas é o treino que mais lesiona.',
  DESCANSO: 'Ninguém evolui, ninguém se machuca: a semana de poupar o elenco.',
}

export const TRAINED_ATTRIBUTE_LABEL: Record<TrainedAttribute, string> = {
  SHOOTING: 'finalização',
  DEFENDING: 'marcação',
  PACE: 'velocidade',
}
