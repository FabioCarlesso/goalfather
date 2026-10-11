import { expect, test } from './helpers'

// Copa mata-mata (issue #61): a fase de copa é uma data do calendário como a
// rodada de liga — mesmo botão, mesma prontidão, mesmo WebSocket. Depois da
// 1ª rodada de liga vem a 1ª fase da copa; jogada a fase, a chave avança.
//
// Navegação só por links (sem `page.goto` no meio): o estado do MSW vive na
// memória do browser e um reload voltaria a liga para a data 1.

test.describe('Cup flow', () => {
  test('joga a rodada de liga, depois a fase da copa, e a chave avança', async ({ page }) => {
    await page.goto('/round')

    // Data 1: rodada de liga.
    await expect(page.getByRole('heading', { name: 'Rodada 1' })).toBeVisible()
    await page.getByRole('button', { name: 'Estou pronto' }).click()
    await page.getByRole('button', { name: 'Jogar rodada' }).click()
    await expect(page.getByText(/Pontuações atualizadas após a rodada 1/i)).toBeVisible({ timeout: 30_000 })

    // Data 2: fase da copa, no mesmo fluxo de prontidão.
    await expect(page.getByRole('heading', { name: 'Copa — Oitavas de final' })).toBeVisible()
    await expect(page.getByText(/empate vai aos pênaltis/)).toBeVisible()
    await page.getByRole('button', { name: 'Estou pronto' }).click()
    await page.getByRole('button', { name: 'Próxima rodada' }).click()
    await expect(page.getByText(/Copa — Oitavas de final encerrada/)).toBeVisible({ timeout: 30_000 })

    // A chave já tem as quartas sorteadas a partir dos vencedores.
    await page.getByRole('link', { name: 'Copa' }).click()
    await expect(page.getByRole('heading', { name: 'Copa 2026' })).toBeVisible()
    const quarters = page.getByTestId('cup-phase-QUARTER_FINALS')
    await expect(quarters.getByText('em disputa')).toBeVisible()
    await expect(quarters.getByText('A definir')).toHaveCount(0)
    await expect(quarters.getByText('a disputar')).toHaveCount(4)
  })
})
