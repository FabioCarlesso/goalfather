import { Link, useNavigate } from 'react-router-dom'

const ONBOARDED_KEY = 'gf:onboarded'

/** O usuário já passou pelo onboarding? Lido no redirect da rota "/". */
export const isOnboarded = (): boolean => {
  try {
    return localStorage.getItem(ONBOARDED_KEY) === 'true'
  } catch {
    return false // ambientes sem localStorage (SSR/testes) caem no onboarding
  }
}

const markOnboarded = () => {
  try {
    localStorage.setItem(ONBOARDED_KEY, 'true')
  } catch {
    /* ignora — onboarding apenas reaparece na próxima visita */
  }
}

const cards = [
  { to: '/lineup', icon: '📋', title: 'Escalação', desc: 'Monte os 11 titulares e a formação tática.' },
  { to: '/round', icon: '⚽', title: 'Rodada', desc: 'Jogue a rodada e acompanhe a partida ao vivo.' },
  { to: '/market', icon: '💰', title: 'Mercado', desc: 'Compre e venda jogadores para reforçar o elenco.' },
  { to: '/standings', icon: '🏆', title: 'Tabela', desc: 'Acompanhe a classificação do campeonato.' },
] as const

export function WelcomePage() {
  const navigate = useNavigate()

  const finish = () => {
    markOnboarded()
    navigate('/dashboard')
  }

  return (
    <section className="mx-auto max-w-2xl space-y-8">
      <header className="space-y-3 text-center">
        <h1 className="text-4xl font-bold text-slate-100">Bem-vindo ao GoalFather ⚽</h1>
        <p className="text-slate-400">
          Você é o técnico do <strong className="text-slate-200">Goal Father FC</strong>.
          Escale o time, jogue a rodada e gerencie o caixa rumo ao título.
        </p>
      </header>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {cards.map((c) => (
          <Link
            key={c.to}
            to={c.to}
            onClick={markOnboarded}
            className="rounded-lg border border-slate-800 bg-slate-900/40 p-4 transition-colors hover:border-emerald-700/50 hover:bg-emerald-900/10"
          >
            <div className="text-lg font-semibold text-slate-100">
              <span className="mr-2">{c.icon}</span>{c.title}
            </div>
            <p className="mt-1 text-sm text-slate-400">{c.desc}</p>
          </Link>
        ))}
      </div>

      <div className="flex items-center justify-center gap-3">
        <button
          onClick={finish}
          className="rounded-md bg-emerald-600 px-5 py-2 text-sm font-medium text-white transition-colors hover:bg-emerald-500"
        >
          Começar
        </button>
        <button
          onClick={finish}
          className="rounded-md border border-slate-700 px-5 py-2 text-sm text-slate-400 transition-colors hover:text-slate-200"
        >
          Pular
        </button>
      </div>
    </section>
  )
}
