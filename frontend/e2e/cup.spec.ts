import { type Page } from '@playwright/test'
import { expect, test } from './helpers'

/** Data do próximo encaixe ("Semana 6 · Meio de semana") na página — o cabeçalho também mostra a data do jogo (issue #133). */
const nextMatchday = (page: Page) => page.getByRole('main').getByText(/Semana \d+ · (Meio de semana|Fim de semana)/).first()

/**
 * Joga os encaixes que vierem até o próximo ser [name]. Contra o backend real
 * (issue #131) o calendário intercala rodadas de liga entre as fases da copa
 * numa ordem que o mock não reproduz; o que o teste afirma é o fluxo da copa,
 * não a posição dela no calendário.
 */
async function playUntil(page: Page, name: string): Promise<void> {
  const target = page.getByRole('heading', { name })
  for (let step = 0; step < 8; step++) {
    // Dá tempo de a tela assentar depois do resumo antes de decidir avançar.
    if (await target.waitFor({ timeout: 3_000 }).then(() => true, () => false)) return
    const date = (await nextMatchday(page).textContent()) ?? ''
    await page.getByRole('button', { name: 'Estou pronto' }).click()
    await page.getByRole('button', { name: /^(Jogar rodada|Próxima rodada)$/ }).click()
    await expect(nextMatchday(page)).not.toHaveText(date, { timeout: 30_000 })
  }
  await expect(target).toBeVisible()
}

// Copa do Brasil (issues #61 e #131): cada perna da copa é um encaixe do
// calendário como a rodada de liga — mesmo botão, mesma prontidão, mesmo
// WebSocket. Formato do pack do E2E: 1ª fase em jogo único com os 8 mais
// fracos; os 4 mais fortes entram nas quartas, em ida e volta.
//
// Navegação só por links (sem `page.goto` no meio): o estado do MSW vive na
// memória do browser e um reload voltaria a liga para a data 1.

test.describe('Cup flow', () => {
  test('joga a liga, a 1ª fase em jogo único e a ida das quartas, e a chave avança', async ({ page }) => {
    await page.goto('/round')

    // Encaixe 1: rodada de liga, no domingo (issue #125).
    await expect(page.getByRole('heading', { name: 'Rodada 1' })).toBeVisible()
    await expect(page.getByRole('main').getByText(/Semana \d+ · Fim de semana/)).toBeVisible()
    // Instantânea (issue #115): o placar é o mesmo em qualquer velocidade, e a
    // copa não precisa de 3 × ~7s de transmissão para ser testada.
    await page.getByLabel('Velocidade').selectOption('INSTANT')
    await page.getByRole('button', { name: 'Estou pronto' }).click()
    await page.getByRole('button', { name: 'Jogar rodada' }).click()
    await expect(page.getByText(/Pontuações atualizadas após a rodada 1/i)).toBeVisible({ timeout: 30_000 })

    // 1ª fase da copa (jogo único), numa quarta, no mesmo fluxo.
    await playUntil(page, 'Copa — Primeira fase')
    await expect(page.getByRole('main').getByText(/Semana \d+ · Meio de semana/)).toBeVisible()
    await expect(page.getByText(/agregado da volta vai aos pênaltis/)).toBeVisible()
    await page.getByRole('button', { name: 'Estou pronto' }).click()
    await page.getByRole('button', { name: 'Próxima rodada' }).click()
    await expect(page.getByText(/Copa — Primeira fase encerrada/)).toBeVisible({ timeout: 30_000 })

    // Ida das quartas, com os 4 mais fortes já em campo.
    await playUntil(page, 'Copa — Quartas de final — ida')
    await page.getByRole('button', { name: 'Estou pronto' }).click()
    await page.getByRole('button', { name: 'Próxima rodada' }).click()
    await expect(page.getByText(/Copa — Quartas de final — ida encerrada/)).toBeVisible({ timeout: 30_000 })

    // A chave: 1ª fase decidida, quartas sorteadas com a ida jogada e a volta por jogar.
    await page.getByRole('link', { name: 'Copa' }).click()
    await expect(page.getByRole('heading', { name: 'Copa do Brasil 2026' })).toBeVisible()
    await expect(page.getByTestId('cup-phase-primeira-fase').getByText('a disputar')).toHaveCount(0)
    const quarters = page.getByTestId('cup-phase-quartas-de-final')
    await expect(quarters.getByText('em disputa')).toBeVisible()
    await expect(quarters.getByText('A definir')).toHaveCount(0)
    await expect(quarters.getByText('volta a disputar')).toHaveCount(4)
    await expect(page.getByTestId('cup-phase-semifinal').getByText('A definir')).toHaveCount(2)
  })
})
