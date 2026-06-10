const ONBOARDED_KEY = 'gf:onboarded'

/** O usuário já passou pelo onboarding? Lido no redirect da rota "/". */
export const isOnboarded = (): boolean => {
  try {
    return localStorage.getItem(ONBOARDED_KEY) === 'true'
  } catch {
    return false // ambientes sem localStorage (SSR/testes) caem no onboarding
  }
}

export const markOnboarded = () => {
  try {
    localStorage.setItem(ONBOARDED_KEY, 'true')
  } catch {
    /* ignora — onboarding apenas reaparece na próxima visita */
  }
}
