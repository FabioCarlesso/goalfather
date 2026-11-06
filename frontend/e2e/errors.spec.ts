import { expect, test } from './helpers'

test.describe('Erros de tela', () => {
  test('retry que falha não empilha toast, e o toast some quando a tela se recupera', async ({ page }) => {
    await page.goto('/market')
    await expect(page.getByRole('heading', { name: 'Mercado' })).toBeVisible()

    // Na própria página porque o MSW responde pelo service worker, fora do `page.route`.
    await page.evaluate(() => {
      const original = window.fetch
      const state = { fail: true, calls: 0 }
      Object.assign(window, { failStandings: state })
      window.fetch = (input, init) => {
        const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
        if (state.fail && url.includes('/api/league/standings')) {
          state.calls++
          return Promise.resolve(Response.json({ code: 'INTERNAL_ERROR', message: 'x' }, { status: 500 }))
        }
        return original(input, init)
      }
    })
    const calls = () => page.evaluate(() => (window as unknown as { failStandings: { calls: number } }).failStandings.calls)

    await page.getByRole('link', { name: 'Tabela' }).click()
    const retry = page.getByRole('button', { name: 'Tentar novamente' })
    await expect(retry).toBeVisible()

    const before = await calls()
    await retry.click()
    // Duas chamadas: a do clique e a retentativa automática (`retry: 1` em `main.tsx`).
    // Sem esperar a segunda, a falha desligada abaixo deixaria a retentativa se
    // recuperar sozinha, e o botão nunca voltaria.
    await expect.poll(calls).toBe(before + 2)
    await expect(retry).toBeVisible()
    await expect(page.getByRole('button', { name: 'Fechar aviso' })).toHaveCount(1)

    await page.evaluate(() => {
      (window as unknown as { failStandings: { fail: boolean } }).failStandings.fail = false
    })
    await retry.click()

    await expect(page.getByRole('heading', { name: 'Tabela' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Fechar aviso' })).toHaveCount(0)
  })

  test('URL inexistente mostra a tela do app, com saída', async ({ page }) => {
    await page.goto('/rota-que-nao-existe')

    await expect(page.getByRole('heading', { name: 'Página não encontrada' })).toBeVisible()
    await page.getByRole('link', { name: 'Voltar ao início' }).click()
    await expect(page).not.toHaveURL(/rota-que-nao-existe/)
  })
})
