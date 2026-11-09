import { useId } from 'react'
import { STREAM_SPEEDS, STREAM_SPEED_LABEL, isStreamSpeed } from '../domain/streamSpeed'
import type { StreamSpeed } from '../domain/types'
import { Button } from './ui/Button'

export interface SpeedControlProps {
  speed: StreamSpeed
  onSpeedChange: (speed: StreamSpeed) => void
  /** Mostra "Pular para o fim" (transmissão em andamento e ainda não instantânea). */
  canSkip: boolean
  onSkip: () => void
  /** Transmissão em andamento: a nova velocidade só vale para a próxima. */
  live?: boolean
}

/**
 * Seletor de velocidade da transmissão + "Pular para o fim" (issue #115).
 * Apresentação pura: a tela decide o que a velocidade e o pulo fazem.
 */
export function SpeedControl({ speed, onSpeedChange, canSkip, onSkip, live = false }: SpeedControlProps) {
  const id = useId()
  return (
    <div className="flex flex-wrap items-center gap-2 text-sm">
      <label htmlFor={id} className="text-text-muted">
        Velocidade
      </label>
      <select
        id={id}
        value={speed}
        onChange={(e) => {
          if (isStreamSpeed(e.target.value)) onSpeedChange(e.target.value)
        }}
        className="rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-slate-200"
      >
        {STREAM_SPEEDS.map((s) => (
          <option key={s} value={s}>
            {STREAM_SPEED_LABEL[s]}
          </option>
        ))}
      </select>
      {canSkip && (
        <Button variant="secondary" size="sm" onClick={onSkip}>
          Pular para o fim
        </Button>
      )}
      {live && <span className="text-xs text-text-muted">A velocidade vale a partir da próxima transmissão.</span>}
    </div>
  )
}
