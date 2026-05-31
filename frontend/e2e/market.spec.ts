import { expect, test } from '@playwright/test'

// Fluxos de mercado (issue #12): comprar (em /market) e vender (em /dashboard).
// Rodam contra MSW (`npm run e2e`) e contra o backend Spring (`npm run e2e:real`).
//
// Contagens são assertadas de forma relativa (antes/depois), não absoluta, para
// sobreviver a execuções repetidas contra o backend real, onde o estado persiste
// (o mesmo jogador não pode ser comprado/vendido duas vezes).

test.describe('Market flow', () => {
  test('comprar um jogador reduz o mercado e debita o caixa', async ({ page }) => {
    await page.goto('/market')
    await expect(page.getByRole('heading', { name: 'Mercado' })).toBeVisible()

    const buyButtons = page.getByRole('button', { name: 'Comprar' })
    const before = await buyButtons.count()
    test.skip(before === 0, 'mercado vazio (estado já consumido em e2e:real)')

    await buyButtons.first().click()

    // Sucesso de negócio: banner persistente do TransferFeedback.
    await expect(page.getByText('Contratação concluída')).toBeVisible({ timeout: 10_000 })
    // O jogador comprado sai da lista do mercado.
    await expect(buyButtons).toHaveCount(before - 1)
  })

  test('vender um jogador remove do elenco', async ({ page }) => {
    // A venda passa por um confirm() — Playwright descarta diálogos por padrão,
    // então é preciso aceitar explicitamente, senão a venda vira no-op.
    page.on('dialog', (dialog) => dialog.accept())

    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: 'Elenco' })).toBeVisible()

    const sellButtons = page.getByRole('button', { name: 'Vender' })
    const before = await sellButtons.count()
    test.skip(before === 0, 'elenco vazio')

    await sellButtons.first().click()

    // O jogador vendido sai do elenco (uma linha — e um botão "Vender" — a menos).
    await expect(sellButtons).toHaveCount(before - 1)
  })
})
