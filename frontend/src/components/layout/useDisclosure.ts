import { useCallback, useEffect, useId, useRef, useState, type MouseEvent } from 'react'

/**
 * Estado de UI de um painel abre/fecha (menu mobile e menu de usuário).
 *
 * Fecha com `Esc`, devolvendo o foco ao botão, e quando o clique ou o foco saem
 * de `rootRef`. Este último evita dois menus abertos ao mesmo tempo, inclusive
 * navegando só pelo teclado. `panelId` liga o botão ao painel via `aria-controls`.
 */
export function useDisclosure() {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLElement | null>(null)
  const panelId = useId()

  const close = useCallback(() => setOpen(false), [])

  // O botão é guardado no clique, em vez de receber um `ref`: o `Button` de `ui/`
  // não expõe `ref` no tipo das props, e o `currentTarget` já é o elemento certo.
  const toggle = useCallback((event: MouseEvent<HTMLElement>) => {
    triggerRef.current = event.currentTarget
    setOpen((current) => !current)
  }, [])

  useEffect(() => {
    if (!open) return

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      setOpen(false)
      triggerRef.current?.focus()
    }
    const onOutside = (event: Event) => {
      if (event.target instanceof Node && !rootRef.current?.contains(event.target)) setOpen(false)
    }

    document.addEventListener('keydown', onKeyDown)
    document.addEventListener('pointerdown', onOutside)
    document.addEventListener('focusin', onOutside)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.removeEventListener('pointerdown', onOutside)
      document.removeEventListener('focusin', onOutside)
    }
  }, [open])

  return { open, toggle, close, rootRef, panelId }
}
