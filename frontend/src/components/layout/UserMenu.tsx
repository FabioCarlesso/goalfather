import { NavLink } from 'react-router-dom'
import { Button } from '../ui/Button'
import { navLinkClass } from './navLinkClass'
import { useDisclosure } from './useDisclosure'

interface UserMenuProps {
  username?: string
  onLogout: () => void
}

/**
 * Menu de usuário, nos dois layouts: nome, Tutorial e Sair. Abaixo de `lg` o botão
 * mostra só o ícone; a partir daí, também o nome, truncado para não empurrar a barra.
 *
 * É um painel simples (`aria-expanded`), não um `role="menu"`: esse papel exigiria
 * navegação por setas, e aqui o Tab entre um link e um botão já resolve.
 */
export function UserMenu({ username, onLogout }: UserMenuProps) {
  const { open, toggle, close, rootRef, panelId } = useDisclosure()

  return (
    <div ref={rootRef} className="relative">
      <Button
        variant="secondary"
        size="sm"
        className="min-h-10"
        aria-label={username ? `Menu do usuário ${username}` : 'Menu do usuário'}
        aria-expanded={open}
        aria-controls={panelId}
        onClick={toggle}
      >
        <svg aria-hidden="true" viewBox="0 0 20 20" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
          <circle cx="10" cy="6.5" r="3.5" />
          <path d="M3.5 17.5c.8-3.4 3.3-5 6.5-5s5.7 1.6 6.5 5" />
        </svg>
        {username && <span className="hidden max-w-24 truncate lg:inline">{username}</span>}
      </Button>
      <div
        id={panelId}
        hidden={!open}
        className="absolute right-0 top-full mt-2 w-56 rounded-lg border border-border bg-surface-raised p-2 shadow-xl"
      >
        {open && (
          <>
            {username && (
              <p className="truncate px-3 py-2 text-sm font-medium text-text" title={username}>
                {username}
              </p>
            )}
            <NavLink to="/welcome" onClick={close} className={navLinkClass('block px-3 py-2 text-sm')}>
              Tutorial
            </NavLink>
            <Button variant="secondary" size="sm" className="mt-2 w-full" onClick={onLogout}>
              Sair
            </Button>
          </>
        )}
      </div>
    </div>
  )
}
