// Re-exporta os tipos gerados a partir de ../../contract/openapi.yaml.
// Toda a app importa daqui — se algo faltar, é porque o contrato está incompleto.
// Rode `npm run gen:api` após alterar o YAML.

import type { components } from '../api/generated'

export type Player        = components['schemas']['Player']
export type Availability  = components['schemas']['Availability']
export type Club          = components['schemas']['Club']
export type Lineup        = components['schemas']['Lineup']
export type Formation     = components['schemas']['Formation']
export type Posture       = components['schemas']['Posture']
export type Position      = components['schemas']['Position']
export type MarketEntry   = components['schemas']['MarketEntry']
export type Standings     = components['schemas']['Standings']
export type StandingRow   = components['schemas']['StandingRow']
export type MatchSummary  = components['schemas']['MatchSummary']
export type MatchEvent    = components['schemas']['MatchEvent']
export type MatchStats    = components['schemas']['MatchStats']
export type TeamStats     = components['schemas']['TeamStats']
export type TransferResult = components['schemas']['TransferResult']
export type ErrorResponse = components['schemas']['ErrorResponse']
export type Round         = components['schemas']['Round']
export type RoundMatch    = components['schemas']['RoundMatch']
export type RoundStatus   = components['schemas']['RoundStatus']
export type RoundEvent    = components['schemas']['RoundEvent']
export type RoundFinance  = components['schemas']['RoundFinance']
export type Retirement    = components['schemas']['Retirement']
// Treino semanal (issue #58)
export type TrainingFocus     = components['schemas']['TrainingFocus']
export type TrainedAttribute  = components['schemas']['TrainedAttribute']
export type TrainingReport    = components['schemas']['TrainingReport']
export type TrainingEvent     = components['schemas']['TrainingEvent']
export type ReadinessStatus = components['schemas']['ReadinessStatus']

// Histórico de temporadas + carreira do técnico (issue #60)
export type SeasonRecord    = components['schemas']['SeasonRecord']
export type SeasonStanding  = components['schemas']['SeasonStanding']
export type SeasonTopScorer = components['schemas']['SeasonTopScorer']
export type ClubCareer      = components['schemas']['ClubCareer']
export type CareerCampaign  = components['schemas']['CareerCampaign']

// Auth (issue #18) + seleção de clube (issue #19)
export type AuthUser        = components['schemas']['AuthUser']
export type AuthResponse    = components['schemas']['AuthResponse']
export type RegisterRequest = components['schemas']['RegisterRequest']
export type LoginRequest    = components['schemas']['LoginRequest']
export type AvailableClub   = components['schemas']['AvailableClub']
