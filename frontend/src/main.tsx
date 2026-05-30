import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider, QueryCache, MutationCache } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { App } from './App.tsx'
import { errorMessage } from './api/errorMessages'
import './index.css'

// Tratamento global de erros/sucessos via toast (issue #6). Centralizado nos
// caches do QueryClient — qualquer query/mutation que falhe dispara um toast
// vermelho com mensagem amigável, sem cada componente repetir o handling.
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      staleTime: 30_000,
      retry: 1,
    },
  },
  queryCache: new QueryCache({
    onError: (error) => toast.error(errorMessage(error)),
  }),
  mutationCache: new MutationCache({
    onError: (error) => toast.error(errorMessage(error)),
    // Toast verde quando a mutation declara `meta.successMessage` (ex.:
    // escalação, ampliação). Compra/venda tratam o sucesso no próprio hook,
    // pois o resultado de negócio vive no payload (TransferResult).
    onSuccess: (_data, _vars, _ctx, mutation) => {
      const msg = mutation.meta?.successMessage
      if (typeof msg === 'string') toast.success(msg)
    },
  }),
})

// Inicializa MSW antes de montar a árvore React.
// Em produção (ou com VITE_USE_MOCKS=false), apenas pula e fala com o backend real.
async function enableMocks() {
  if (import.meta.env.DEV && import.meta.env.VITE_USE_MOCKS !== 'false') {
    const { worker } = await import('./mocks/browser')
    await worker.start({
      onUnhandledRequest: 'bypass',
      serviceWorker: { url: '/mockServiceWorker.js' },
    })
  }
}

enableMocks().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    </StrictMode>,
  )
})
