import { test as base } from '@playwright/test'
import { chooseClub, expect, offerContract, register, test } from './helpers'

// Janelas de transferência (issue #137). Rodam contra MSW (`npm run e2e`) e
// contra o backend Spring (`npm run e2e:real`).
//
// O mundo do E2E (pack `data/world/e2e/v1` e o mock) começa em 04/01/2026 com
// o mercado do Brasil ABERTO (a CBF abre no 1º dia no recorte do E2E) e o da
// Inglaterra FECHADO até 15/06/2026 (o recorte tira a janela de inverno) —
// ver `E2ePack.e2eCalendar`. Assim os dois estados existem na data inicial,
// sem jogar dezenas de encaixes.

test('clube brasileiro vê a janela aberta no mercado e no cabeçalho', async ({ page }) => {
  await page.goto('/market')
  await expect(page.getByRole('heading', { name: 'Mercado' })).toBeVisible()
  await expect(page.locator('main').getByTestId('transfer-window')).toHaveText('Janela aberta até 03/03/2026')
  await expect(page.locator('header').getByTestId('transfer-window')).toHaveText('Janela aberta até 03/03/2026')
})

base('comprar fora da janela é recusado e mostra quando ela abre', async ({ page }) => {
  await register(page)
  await chooseClub(page, { country: 'Inglaterra', league: 'Premier League' })

  await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Mercado' }).click()
  await expect(page.getByRole('heading', { name: 'Mercado' })).toBeVisible()
  await expect(page.locator('main').getByTestId('transfer-window')).toHaveText('Mercado fechado · abre em 15/06/2026')

  const buyButtons = page.getByRole('button', { name: 'Comprar' })
  await expect(buyButtons.first().or(page.getByText('Nenhum jogador disponível'))).toBeVisible()
  const before = await buyButtons.count()
  base.skip(before === 0, 'mercado vazio')
  const cash = page.getByText(/^Caixa:/)
  await expect(cash).toContainText('US$')
  const cashBefore = await cash.textContent()

  await offerContract(page, buyButtons.first())

  // A recusa é do backend (`TransferWindowClosed`), com a data da próxima abertura.
  await expect(page.getByText('Janela de transferências fechada — abre em 15/06/2026.')).toBeVisible({ timeout: 10_000 })
  await expect(buyButtons).toHaveCount(before)
  await expect(cash).toHaveText(cashBefore ?? '')
})
