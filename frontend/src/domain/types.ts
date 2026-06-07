// Re-exporta os tipos gerados a partir de ../../contract/openapi.yaml.
// Toda a app importa daqui — se algo faltar, é porque o contrato está incompleto.
// Rode `npm run gen:api` após alterar o YAML.

import type { components } from '../api/generated'

export type Player        = components['schemas']['Player']
export type Club          = components['schemas']['Club']
export type Lineup        = components['schemas']['Lineup']
export type Formation     = components['schemas']['Formation']
export type Position      = components['schemas']['Position']
export type MarketEntry   = components['schemas']['MarketEntry']
export type Standings     = components['schemas']['Standings']
export type StandingRow   = components['schemas']['StandingRow']
export type MatchSummary  = components['schemas']['MatchSummary']
export type MatchEvent    = components['schemas']['MatchEvent']
export type TransferResult = components['schemas']['TransferResult']
export type ErrorResponse = components['schemas']['ErrorResponse']
export type Round         = components['schemas']['Round']
export type RoundMatch    = components['schemas']['RoundMatch']
export type RoundStatus   = components['schemas']['RoundStatus']
export type RoundEvent    = components['schemas']['RoundEvent']
export type RoundFinance  = components['schemas']['RoundFinance']
export type ReadinessStatus = components['schemas']['ReadinessStatus']

// Auth (issue #18) + seleção de clube (issue #19)
export type AuthUser        = components['schemas']['AuthUser']
export type AuthResponse    = components['schemas']['AuthResponse']
export type RegisterRequest = components['schemas']['RegisterRequest']
export type LoginRequest    = components['schemas']['LoginRequest']
export type AvailableClub   = components['schemas']['AvailableClub']
