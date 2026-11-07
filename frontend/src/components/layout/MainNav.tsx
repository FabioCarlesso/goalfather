import { NavLink } from 'react-router-dom'
import { Button } from '../ui/Button'
import { navLinkClass } from './navLinkClass'
import { useDisclosure } from './useDisclosure'

const nav = [
  { to: '/dashboard', label: 'Clube' },
  { to: '/lineup',    label: 'Escalação' },
  { to: '/round',     label: 'Rodada' },
  { to: '/market',    label: 'Mercado' },
  { to: '/standings', label: 'Tabela' },
  { to: '/cup',       label: 'Copa' },
  { to: '/history',   label: 'Histórico' },
] as const

const pendingOffersTitle = (count: number) => `${count} proposta(s) aguardando resposta`

const BADGE = 'rounded-full bg-amber-500 px-1.5 text-[10px] font-bold text-slate-950'

interface NavLinksProps {
  pendingOffers: number
  linkLayout: string
  onNavigate?: () => void
}

function NavLinks({ pendingOffers, linkLayout, onNavigate }: NavLinksProps) {
  return nav.map((n) => (
    <NavLink key={n.to} to={n.to} onClick={onNavigate} className={navLinkClass(linkLayout)}>
      {n.label}
      {n.to === '/dashboard' && pendingOffers > 0 && (
        <span className={`ml-1.5 ${BADGE}`} title={pendingOffersTitle(pendingOffers)}>
          {pendingOffers}
        </span>
      )}
    </NavLink>
  ))
}

/** Barra horizontal, a partir de `lg`. */
export function DesktopNav({ pendingOffers }: { pendingOffers: number }) {
  return (
    <nav aria-label="Principal" className="hidden items-center gap-1 lg:flex">
      <NavLinks pendingOffers={pendingOffers} linkLayout="whitespace-nowrap px-2 py-1.5 text-sm" />
    </nav>
  )
}

/**
 * Botão de menu e lista de links, abaixo de `lg`. O painel se posiciona em
 * relação ao `<header>`, para ocupar toda a largura da tela.
 *
 * O contêiner com `panelId` existe sempre, para o `aria-controls` nunca apontar
 * para um id ausente; os links só entram no DOM com o menu aberto.
 */
export function MobileMenu({ pendingOffers }: { pendingOffers: number }) {
  const { open, toggle, close, rootRef, panelId } = useDisclosure()
  const label =
    pendingOffers > 0 ? `Menu de navegação, ${pendingOffersTitle(pendingOffers)}` : 'Menu de navegação'

  return (
    <div ref={rootRef} className="lg:hidden">
      <Button
        variant="secondary"
        size="sm"
        className="relative min-h-10"
        aria-label={label}
        aria-expanded={open}
        aria-controls={panelId}
        onClick={toggle}
      >
        <svg aria-hidden="true" viewBox="0 0 20 20" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
          <path d={open ? 'M5 5l10 10M15 5L5 15' : 'M3 5h14M3 10h14M3 15h14'} />
        </svg>
        {/* O número já está no `aria-label`: aqui é só o sinal visual. */}
        {pendingOffers > 0 && (
          <span aria-hidden="true" className={`absolute -right-1.5 -top-1.5 ${BADGE}`}>
            {pendingOffers}
          </span>
        )}
      </Button>
      <div
        id={panelId}
        hidden={!open}
        className="absolute inset-x-0 top-full max-h-[calc(100dvh-4rem)] overflow-y-auto border-b border-border bg-surface-raised shadow-xl lg:hidden"
      >
        {open && (
          <nav aria-label="Principal" className="flex flex-col gap-1 p-2">
            <NavLinks pendingOffers={pendingOffers} linkLayout="px-3 py-3 text-base" onNavigate={close} />
          </nav>
        )}
      </div>
    </div>
  )
}
