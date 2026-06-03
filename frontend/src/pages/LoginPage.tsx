import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { errorMessage } from '../api/errorMessages'

type Mode = 'login' | 'register'

/**
 * Login / cadastro (issue #18). Um único formulário alterna entre os dois
 * modos. No sucesso navega para `/` — as guardas de rota decidem o destino
 * (`/select-club` se ainda não há clube, senão `/dashboard`).
 */
export function LoginPage() {
  const { login, register } = useAuth()
  const navigate = useNavigate()

  const [mode, setMode] = useState<Mode>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      if (mode === 'login') await login(username.trim(), password)
      else await register(username.trim(), password)
      navigate('/')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  const valid = username.trim().length >= 3 && password.length >= 6

  return (
    <section className="mx-auto max-w-sm space-y-6">
      <header className="space-y-2 text-center">
        <h1 className="text-3xl font-bold text-slate-100">⚽ GoalFather</h1>
        <p className="text-slate-400">
          {mode === 'login' ? 'Entre para treinar seu clube.' : 'Crie sua conta de técnico.'}
        </p>
      </header>

      <form onSubmit={onSubmit} className="space-y-4 rounded-lg border border-slate-800 bg-slate-900/60 p-6">
        <label className="block text-sm text-slate-400">
          Usuário
          <input
            type="text"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-slate-100"
          />
        </label>
        <label className="block text-sm text-slate-400">
          Senha
          <input
            type="password"
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-slate-100"
          />
        </label>

        {error && <p className="text-sm text-red-400">{error}</p>}

        <button
          type="submit"
          disabled={!valid || submitting}
          className="w-full rounded-md bg-emerald-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-emerald-500 disabled:cursor-not-allowed disabled:bg-slate-700"
        >
          {submitting ? '…' : mode === 'login' ? 'Entrar' : 'Cadastrar'}
        </button>
      </form>

      <p className="text-center text-sm text-slate-400">
        {mode === 'login' ? 'Não tem conta?' : 'Já tem conta?'}{' '}
        <button
          type="button"
          onClick={() => {
            setMode((m) => (m === 'login' ? 'register' : 'login'))
            setError(null)
          }}
          className="text-emerald-400 hover:text-emerald-300"
        >
          {mode === 'login' ? 'Cadastre-se' : 'Entrar'}
        </button>
      </p>
    </section>
  )
}
