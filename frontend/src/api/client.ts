// Cliente HTTP tipado mínimo sobre `fetch`. Sem axios.
// Os tipos de cada endpoint vêm de `generated.d.ts` (gerado do OpenAPI).
// MSW intercepta no nível de rede em dev, então este código não muda quando
// trocamos mocks pelo backend Spring real.

import type { ErrorResponse } from '../domain/types'
import { getToken } from './token'

const BASE = '' // mesma origem; MSW intercepta em dev, proxy/CORS em prod

export class ApiError extends Error {
  readonly status: number
  readonly body: ErrorResponse | null

  constructor(status: number, body: ErrorResponse | null) {
    super(body?.message ?? `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  // Header montado dinamicamente: Content-Type só quando há corpo, e
  // Authorization quando há token (issue #18). O backend exige JWT em /api/**
  // (exceto /auth/register|login); MSW ignora o header nos mocks.
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`

  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (res.status === 204) return undefined as T

  const text = await res.text()
  const parsed: unknown = text ? JSON.parse(text) : null

  if (!res.ok) {
    throw new ApiError(res.status, parsed as ErrorResponse | null)
  }
  return parsed as T
}

export const api = {
  get:  <T>(path: string)                  => request<T>('GET',  path),
  post: <T>(path: string, body?: unknown)  => request<T>('POST', path, body),
}
