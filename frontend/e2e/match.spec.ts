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

    // Ao apito final o sumário aparece com os números do jogo (issue #57).
    // A partida inteira leva ~7s de stream (80ms por minuto simulado).
    await expect(page.getByText(/Estatísticas da partida/i)).toBeVisible({ timeout: 30_000 })
    await expect(page.getByText('Finalizações')).toBeVisible()

    // Volta para a rodada.
    await page.getByRole('link', { name: /Voltar para a rodada/i }).click()
    await expect(page.getByRole('heading', { name: /Rodada \d+/ })).toBeVisible()
  })

  // Issue #115: no drill-down, pular = reconectar em INSTANT e reprocessar a
  // partida desde o KickOff (o stream é determinístico pelo matchId).
  test('pular para o fim mostra o apito final e o sumário', async ({ page }) => {
    await page.goto('/round')
    await page.getByLabel('Velocidade').selectOption('SLOW')
    await page.locator('a[href^="/round/match/"]').first().click()

    await expect(page.getByText(/Bola rolando/i).first()).toBeVisible({ timeout: 10_000 })
    await page.getByRole('button', { name: 'Pular para o fim' }).click()

    await expect(page.getByText(/Estatísticas da partida/i)).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText(/Fim de jogo/i)).toHaveCount(1)
    await expect(page.getByText('FIM', { exact: true })).toBeVisible()
  })
})
