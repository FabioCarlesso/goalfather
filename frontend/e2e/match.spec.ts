import { expect, test } from './helpers'

// Drill-down de partida (issue #9): clicar num card da rodada abre a página
// da partida, que conecta em /ws/matches/{id} e mostra o feed ao vivo.
// Funciona contra mocks (MSW matchStream) e contra o backend Spring real
// (MatchWebSocketHandler) — mesmo teste, dois alvos.

test.describe('Match drill-down', () => {
  test('abre o feed de uma partida a partir do card da rodada', async ({ page }) => {
    await page.goto('/round')
    await expect(page.getByRole('heading', { name: /Rodada \d+/ })).toBeVisible()

    // Abre uma partida que NÃO é a do usuário (sem o selo "SEU JOGO").
    const otherMatch = page
      .locator('a[href^="/round/match/"]')
      .filter({ hasNotText: 'SEU JOGO' })
      .first()
    await otherMatch.click()

    await expect(page).toHaveURL(/\/round\/match\/\d+$/)
    // O KickOff chega quase imediato → "Bola rolando" no feed.
    await expect(page.getByText(/Bola rolando/i).first()).toBeVisible({ timeout: 10_000 })

    // Volta para a rodada.
    await page.getByRole('link', { name: /Voltar para a rodada/i }).click()
    await expect(page.getByRole('heading', { name: /Rodada \d+/ })).toBeVisible()
  })
})
