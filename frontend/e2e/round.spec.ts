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
    // Critério da issue #112: o resumo cabe sem rolagem em 1280×800.
    await page.setViewportSize({ width: 1280, height: 800 })
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

    // Resumo da rodada (issue #112): resultado, posicao e financas do clube
    // do tecnico inteiros na tela, sem rolar. O botao fica no cabecalho do
    // painel; o rodape (posicao + caixa) e o que tem mais chance de cair.
    const summary = page.getByRole('region', { name: 'Resumo da rodada' })
    await expect(summary.getByRole('heading', { name: 'Sua partida' })).toBeInViewport({ ratio: 1 })
    await expect(summary.getByTestId('my-match-score')).toBeInViewport({ ratio: 1 })
    await expect(summary.getByText(/Você está em \d+º/)).toBeInViewport({ ratio: 1 })
    await expect(summary.getByText(/salários$/)).toBeInViewport({ ratio: 1 })
    await expect(summary.getByRole('button', { name: 'Próxima rodada' })).toBeInViewport({ ratio: 1 })

    // "Ver detalhes da partida" rola ate o feed da partida do usuario.
    await summary.getByRole('button', { name: 'Ver detalhes da partida' }).click()
    await expect(page.getByRole('heading', { name: /Detalhes da sua partida/ })).toBeInViewport()

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
