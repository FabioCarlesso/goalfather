// Armazenamento do JWT (issue #18). Único ponto que toca o localStorage para
// o token — `client.ts` lê daqui para montar o header `Authorization`, e o
// AuthContext escreve no login/logout. Tolerante a ambientes sem localStorage
// (SSR/testes) para nunca explodir.

const TOKEN_KEY = 'gf:token'

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token)
  } catch {
    /* ignora — sessão dura só enquanto a aba viver */
  }
}

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* ignora */
  }
}
