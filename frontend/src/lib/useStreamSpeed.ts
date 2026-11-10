import { useCallback, useState } from 'react'
import { DEFAULT_STREAM_SPEED, isStreamSpeed } from '../domain/streamSpeed'
import type { StreamSpeed } from '../domain/types'

export const STREAM_SPEED_KEY = 'gf:streamSpeed'

/** Velocidade salva; ausente, inválida ou sem localStorage → `NORMAL`. */
export const readStreamSpeed = (): StreamSpeed => {
  try {
    const stored = localStorage.getItem(STREAM_SPEED_KEY)
    return isStreamSpeed(stored) ? stored : DEFAULT_STREAM_SPEED
  } catch {
    return DEFAULT_STREAM_SPEED // modo privado/bloqueado: só não lembra
  }
}

const writeStreamSpeed = (speed: StreamSpeed) => {
  try {
    localStorage.setItem(STREAM_SPEED_KEY, speed)
  } catch {
    /* ignora — a escolha vale só para esta sessão */
  }
}

/**
 * Preferência de velocidade da transmissão (issue #115). Estado de UI por
 * usuário, não dado de servidor: por isso `localStorage` e não TanStack Query.
 * Rodada e partida compartilham a mesma chave — a escolha numa vale na outra.
 */
export function useStreamSpeed() {
  const [speed, setSpeedState] = useState<StreamSpeed>(readStreamSpeed)
  const setSpeed = useCallback((next: StreamSpeed) => {
    setSpeedState(next)
    writeStreamSpeed(next)
  }, [])
  return { speed, setSpeed }
}
