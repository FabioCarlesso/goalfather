import { isRouteErrorResponse, useRouteError } from 'react-router-dom'
import { PageTitle } from '../components/PageTitle'

/**
 * Fronteira de erro do data router (issue #109). Sem ela, URL inexistente e erro
 * de render caem na tela padrão do React Router, que mostra a pilha de erro crua
 * e não oferece saída. O link é um `<a>` de propósito: recarregar o app é a saída
 * mais segura depois de um erro de render.
 */
export function RouteErrorPage() {
  const error = useRouteError()
  const notFound = isRouteErrorResponse(error) && error.status === 404
  const title = notFound ? 'Página não encontrada' : 'Algo deu errado'

  return (
    <main className="mx-auto max-w-5xl px-4 py-12 sm:px-6">
      <PageTitle>{title}</PageTitle>
      <h1 className="text-3xl font-bold text-slate-100">{title}</h1>
      <p className="mt-2 text-slate-400">
        {notFound ? 'Este endereço não existe no GoalFather.' : 'Esta tela encontrou um erro inesperado.'}
      </p>
      <a href="/" className="mt-4 inline-block text-emerald-400 underline hover:text-emerald-300">
        Voltar ao início
      </a>
    </main>
  )
}
