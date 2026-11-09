import { Outlet, useNavigate } from 'react-router-dom'
import { useClubOffers } from '../../api/queries/useOffers'
import { useAuth } from '../../auth/AuthContext'
import { useMyClubId } from '../../auth/useMyClubId'
import { pendingReceivedCount } from '../../domain/offers'
import { DesktopNav, MobileMenu } from './MainNav'
import { UserMenu } from './UserMenu'

/**
 * Layout do app autenticado: cabeçalho de navegação + conteúdo da rota. Faz o
 * papel de uma página (lê os dados e passa por props); os menus são apresentação.
 *
 * Barra horizontal a partir de `lg`, botão de menu abaixo disso (issue #106). Em
 * `md` (768px) os 7 links, o logo e o indicador de ambiente estouram a largura.
 */
export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  // Badge de propostas recebidas pendentes (issue #63): a mesma query com
  // polling que a Dashboard usa — o técnico fica sabendo em qualquer tela.
  const myClubId = useMyClubId()
  const { data: offers } = useClubOffers(myClubId)
  const pendingOffers = offers ? pendingReceivedCount(offers, myClubId) : 0

  const onLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <>
      <header className="relative z-30 border-b border-slate-800 bg-slate-900/80 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center gap-2 px-4 py-3 sm:px-6 lg:gap-4">
          <span className="whitespace-nowrap text-xl">⚽ <strong className="text-slate-100">GoalFather</strong></span>
          <DesktopNav pendingOffers={pendingOffers} />
          <div className="ml-auto flex items-center gap-2">
            {/* Indicador de ambiente só em dev (issue #114): o build de produção
                elimina o ramo, pois `import.meta.env.DEV` vira `false` literal. */}
            {import.meta.env.DEV && (
              <span className="whitespace-nowrap font-mono text-xs text-text-muted">
                {import.meta.env.VITE_USE_MOCKS === 'false' ? 'API real' : 'MSW mock'}
              </span>
            )}
            <UserMenu username={user?.username} onLogout={onLogout} />
            <MobileMenu pendingOffers={pendingOffers} />
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
        <Outlet />
      </main>
    </>
  )
}
