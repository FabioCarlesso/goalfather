import { expect, test } from '@playwright/test'

// Fluxo critico do FRONTEND.md: abrir /round, jogar a rodada, ver o feed
// da partida do usuario chegar ao FullTime, e validar que a tabela foi
// atualizada com o resultado.
//
// MSW intercepta no browser quando `VITE_USE_MOCKS != 'false'`. Em modo
// `npm run e2e:real`, esta suite roda contra o backend Spring real em
// :8080. Mesmo teste, dois alvos — assim o swap mock -> real eh
// validado por aqui.

test.describe('Round flow', () => {
  test('joga a rodada completa e atualiza a tabela', async ({ page }) => {
    await page.goto('/round')

    await expect(page.getByRole('heading', { name: /Rodada \d+/ })).toBeVisible()
    await expect(page.getByText('SEU JOGO')).toBeVisible()

    const startBtn = page.getByRole('button', { name: 'Jogar rodada' })
    await expect(startBtn).toBeEnabled()
    await startBtn.click()

    // Evento KickOff chega quase imediato
    await expect(page.getByText(/Bola rolando/i).first()).toBeVisible({ timeout: 10_000 })

    // Esperar todas as partidas finalizarem (~7-10s reais) — botao volta
    // a ficar habilitado como "Proxima rodada"
    await expect(page.getByRole('button', { name: 'Próxima rodada' })).toBeEnabled({
      timeout: 30_000,
    })

    // Banner de pontuacoes atualizadas e top-3
    await expect(page.getByText(/Pontuações atualizadas após a rodada/i)).toBeVisible()

    // Tabela tem que refletir 1 jogo jogado para todos os 6 clubes
    await page.getByRole('link', { name: 'Tabela' }).click()
    await expect(page.getByRole('heading', { name: 'Tabela' })).toBeVisible()
    // Pelo menos uma linha tem pontos > 0 (vencedor de algum jogo)
    await expect(page.locator('tbody tr td:nth-child(8)')
      .filter({ hasText: /^[1-9]\d*$/ })
      .first()).toBeVisible()
  })
})
