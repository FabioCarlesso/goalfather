import toast, { ToastBar, Toaster } from 'react-hot-toast'

/**
 * Toaster do app. Erros persistem até serem dispensados (issue #6), então todo
 * aviso precisa de um botão de fechar: sem ele, só recarregar a página os tirava
 * da tela, e eles chegam a cobrir os botões do canto inferior.
 */
export function AppToaster() {
  return (
    <Toaster
      position="bottom-right"
      toastOptions={{
        duration: 4000,
        error: { duration: Infinity },
        style: { background: '#1e293b', color: '#e2e8f0', border: '1px solid #334155' },
      }}
    >
      {(t) => (
        <ToastBar toast={t}>
          {({ icon, message }) => (
            <>
              {icon}
              {message}
              <button
                type="button"
                aria-label="Fechar aviso"
                onClick={() => toast.dismiss(t.id)}
                className="rounded px-1.5 text-slate-400 transition-colors hover:text-slate-100 focus-visible:outline-2 focus-visible:outline-emerald-400"
              >
                ×
              </button>
            </>
          )}
        </ToastBar>
      )}
    </Toaster>
  )
}
