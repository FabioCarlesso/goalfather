import { expect, test } from './helpers'

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

    // Liga compartilhada (issue #20): sinalizar "pronto" destrava o jogo.
    const startBtn = page.getByRole('button', { name: 'Jogar rodada' })
    await expect(startBtn).toBeDisabled()
    await page.getByRole('button', { name: 'Estou pronto' }).click()

    await expect(startBtn).toBeEnabled()
    await startBtn.click()

    // Evento KickOff chega quase imediato
    await expect(page.getByText(/Bola rolando/i).first()).toBeVisible({ timeout: 10_000 })

    // Banner de pontuacoes atualizadas (sinal de rodada encerrada) e top-3
    await expect(page.getByText(/Pontuações atualizadas após a rodada/i)).toBeVisible({
      timeout: 30_000,
    })

    // Extrato do treino da semana (issue #58) — o foco aplicado aparece
    // mesmo quando ninguem evoluiu.
    await expect(page.getByRole('heading', { name: /Treino da semana —/ })).toBeVisible()

    // Tabela por divisao (issue #47): as duas divisoes aparecem, com a zona
    // de promocao/rebaixamento indicada na legenda.
    await page.getByRole('link', { name: 'Tabela' }).click()
    await expect(page.getByRole('heading', { name: 'Tabela' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Divisão 1' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Divisão 2' })).toBeVisible()
    await expect(page.getByText(/Rebaixamento/i)).toBeVisible()
    await expect(page.getByText(/Promoção/i)).toBeVisible()
    // Pelo menos uma linha tem pontos > 0 (vencedor de algum jogo)
    await expect(page.locator('tbody tr td:nth-child(8)')
      .filter({ hasText: /^[1-9]\d*$/ })
      .first()).toBeVisible()
  })
})
