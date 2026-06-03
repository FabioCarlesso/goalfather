import { useAuth } from './AuthContext'

/**
 * Clube do usuário autenticado. Dentro das rotas protegidas por `<RequireClub>`
 * o `clubId` é garantido — o `?? 0` é só um piso defensivo (nunca alcançado em
 * fluxo normal) para manter o tipo `number` sem `!!`.
 */
export function useMyClubId(): number {
  const { user } = useAuth()
  return user?.clubId ?? 0
}
