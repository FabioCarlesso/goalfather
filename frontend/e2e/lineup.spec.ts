import { expect, test } from '@playwright/test'

// Escalação (issue #12): preencher os 11 titulares, salvar e validar que a
// escalação persiste no servidor. Roda contra MSW e contra o backend real.
//
// A persistência é checada por navegação client-side (sair de /lineup e voltar),
// e não por reload físico: um F5 reinicia o estado in-memory do MSW, enquanto a
// navegação SPA força um refetch do clube — validando o round-trip no servidor
// em ambos os modos.

test.describe('Lineup flow', () => {
  test('salvar a escalação e mantê-la após navegar', async ({ page }) => {
    await page.goto('/lineup')
    await expect(page.getByRole('heading', { name: 'Escalação' })).toBeVisible()

    // Os 11 selects de slot vivem na grade (o select de formação fica no header).
    const slots = page.locator('div.grid select')
    await expect(slots).toHaveCount(11)

    // Em 4-4-2 o elenco-semente tem exatamente 1 GK, 4 ZG, 4 MC e 2 AT, então a
    // primeira opção disponível de cada slot preenche titulares distintos.
    for (let i = 0; i < 11; i++) {
      await slots.nth(i).selectOption({ index: 1 })
    }

    const saveBtn = page.getByRole('button', { name: 'Salvar escalação' })
    await expect(saveBtn).toBeEnabled()
    await saveBtn.click()
    await expect(page.getByText('Escalação salva ✓')).toBeVisible({ timeout: 10_000 })

    // Sai e volta — o clube é refetchado e a escalação salva é recarregada.
    await page.getByRole('link', { name: 'Clube' }).click()
    await expect(page).toHaveURL(/\/dashboard$/)
    await page.getByRole('link', { name: 'Escalação' }).click()

    await expect(page.getByRole('heading', { name: 'Escalação' })).toBeVisible()
    await expect(page.getByText('11/11 titulares')).toBeVisible({ timeout: 10_000 })
  })
})
