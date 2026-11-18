// Latência simulada das rotas REST do MSW, para o `npm run dev` parecer rede
// real (spinners, estados de "salvando"). O E2E zera via `VITE_MOCK_LATENCY_MS=0`
// (ver playwright.config.ts, issue #156): 120ms × centenas de chamadas pesavam
// minutos na suíte sem testar nada. Ausente/inválido → padrão.
const DEFAULT_MOCK_LATENCY_MS = 120

export function parseMockLatency(raw: string | undefined): number {
  if (raw === undefined || raw.trim() === '') return DEFAULT_MOCK_LATENCY_MS
  const ms = Number(raw)
  return Number.isFinite(ms) && ms >= 0 ? ms : DEFAULT_MOCK_LATENCY_MS
}

export const MOCK_LATENCY_MS = parseMockLatency(import.meta.env.VITE_MOCK_LATENCY_MS)
