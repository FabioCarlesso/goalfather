import { getToken } from './token'
import type { StreamSpeed } from '../domain/types'

export interface WsUrlOptions {
  // Ritmo da transmissão (issue #115). Omitido → o servidor usa `NORMAL`.
  speed?: StreamSpeed
}

// Monta a URL de um WebSocket do GoalFather (issue #27). O browser não envia
// header `Authorization` no handshake, então o JWT viaja em `?token=` — o
// `JwtHandshakeInterceptor` do backend valida e rejeita conexões sem token.
// `path` deve começar com `/` (ex.: `/ws/round/3`).
export function wsUrl(path: string, { speed }: WsUrlOptions = {}): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const base = `${protocol}//${window.location.host}${path}`
  const params = new URLSearchParams()
  const token = getToken()
  if (token) params.set('token', token)
  if (speed) params.set('speed', speed)
  const query = params.toString()
  return query ? `${base}?${query}` : base
}
