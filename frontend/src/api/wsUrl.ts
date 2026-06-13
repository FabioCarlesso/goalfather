import { getToken } from './token'

// Monta a URL de um WebSocket do GoalFather (issue #27). O browser não envia
// header `Authorization` no handshake, então o JWT viaja em `?token=` — o
// `JwtHandshakeInterceptor` do backend valida e rejeita conexões sem token.
// `path` deve começar com `/` (ex.: `/ws/round/3`).
export function wsUrl(path: string): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const base = `${protocol}//${window.location.host}${path}`
  const token = getToken()
  return token ? `${base}?token=${encodeURIComponent(token)}` : base
}
