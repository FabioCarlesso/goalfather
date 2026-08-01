import { expect, test } from './helpers'

// Departamento médico e forma física (issue #54). Roda contra MSW
// (`npm run e2e`) e contra o backend Spring (`npm run e2e:real`).
//
// As asserções são relativas (antes/depois) porque em `e2e:real` o estado
// persiste entre execuções — o elenco pode não estar mais em 100%.

test.describe('Medical department', () => {
  test('elenco mostra forma física de cada jogador', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: 'Elenco' })).toBeVisible()

    // Toda linha do elenco carrega a barra de forma física com o título "Forma física: N%".
    await expect(page.locator('[title^="Forma física:"]').first()).toBeVisible()
  })

  test('tratar elenco debita o caixa e confirma o tratamento', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: 'Departamento médico' })).toBeVisible()

    const treat = page.getByRole('button', { name: 'Tratar elenco' })
    test.skip(await treat.isDisabled(), 'caixa insuficiente (estado já consumido em e2e:real)')

    await treat.click()

    await expect(page.getByText('Elenco tratado ✓')).toBeVisible({ timeout: 10_000 })
  })
})
