import { useEffect, useId, useRef, type ReactNode } from 'react'

export interface ModalProps {
  /** Controlado pelo chamador: `true` abre, `false` fecha. */
  open: boolean
  /** Chamado quando o usuário fecha o modal (Esc ou botão ×). */
  onClose: () => void
  title: ReactNode
  children: ReactNode
  /** Rodapé com os botões de ação (confirmar, cancelar…). */
  actions?: ReactNode
}

/**
 * Modal sobre o `<dialog>` nativo, sem dependência externa.
 *
 * `showModal()` já entrega de graça o que custaria código para fazer à mão:
 * o resto da página fica inerte (foco preso no diálogo), `Esc` dispara
 * `cancel` → `close`, e o `::backdrop` cobre a tela. O que fazemos aqui:
 * - sincronizar o `open` controlado (React) com o estado imperativo do dialog;
 * - devolver o foco ao elemento que abriu o modal ao fechar (explícito, para
 *   não depender de cada navegador implementar essa restauração).
 */
export function Modal({ open, onClose, title, children, actions }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const triggerRef = useRef<HTMLElement | null>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    if (open && !dialog.open) {
      // Guarda quem tinha o foco antes de abrir: é para lá que ele volta.
      triggerRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
      dialog.showModal()
    } else if (!open && dialog.open) {
      dialog.close()
    }
  }, [open])

  // O evento `close` chega tanto pelo Esc (fechamento nativo) quanto pelo
  // `dialog.close()` do efeito acima. Só avisamos o chamador no primeiro caso
  // (`open` ainda true); nos dois, devolvemos o foco ao gatilho.
  const handleClose = () => {
    triggerRef.current?.focus()
    triggerRef.current = null
    if (open) onClose()
  }

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      onClose={handleClose}
      className="m-auto w-full max-w-md rounded-lg border border-border bg-surface-raised p-0 text-text shadow-xl backdrop:bg-black/60"
    >
      <div className="space-y-4 p-5">
        <div className="flex items-start justify-between gap-3">
          <h2 id={titleId} className="text-lg font-semibold text-text">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Fechar"
            className="rounded text-text-muted hover:text-slate-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
          >
            ×
          </button>
        </div>
        <div className="text-sm text-slate-300">{children}</div>
        {actions && <div className="flex justify-end gap-2">{actions}</div>}
      </div>
    </dialog>
  )
}
