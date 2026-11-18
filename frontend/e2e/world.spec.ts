import { DEFAULT_COUNTRY, DEFAULT_LEAGUE, expect, test } from './helpers'

// Navegação pelo mundo (issue #133): país → competição → tabela, chave, jogos
// e artilharia; calendário do clube; relógio no cabeçalho. Mesmas specs no
// MSW e no backend real: nomes e números vêm da tela, não fixos (os elencos e
// o calendário do backend não batem com os do mock — ver docs/FRONTEND.md).

const tabs = (page: import('@playwright/test').Page) => page.getByRole('navigation', { name: 'Seções da competição' })

test.describe('Mundo', () => {
  test('país → liga: tabela com faixas e desempates, jogos por encaixe e artilharia', async ({ page }) => {
    await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Mundo' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Mundo' })).toBeVisible()
    const brazil = page.getByRole('region', { name: new RegExp(DEFAULT_COUNTRY) })
    await expect(brazil.getByText('sua liga')).toBeVisible()

    await brazil.getByRole('link', { name: new RegExp(DEFAULT_LEAGUE) }).click()
    await expect(page.getByRole('heading', { level: 1, name: DEFAULT_LEAGUE })).toBeVisible()
    await expect(tabs(page).getByRole('link', { name: 'Tabela' })).toHaveAttribute('aria-current', 'page')
    await expect(page.locator('main tbody tr')).toHaveCount(6)
    await expect(page.locator('main tr[aria-current="true"]')).toBeVisible()
    await expect(page.getByTestId('tiebreakers')).toContainText('Pontos › Vitórias')

    await tabs(page).getByRole('link', { name: 'Jogos' }).click()
    await expect(page).toHaveURL(/\/competitions\/bra-serie-a\/jogos$/)
    await expect(page.getByRole('heading', { level: 2, name: /^Rodada \d+$/ })).toBeVisible()
    await expect(page.getByTestId('competition-matches').getByRole('listitem')).toHaveCount(3)

    await tabs(page).getByRole('link', { name: 'Artilharia' }).click()
    await expect(page.getByText(/Ninguém marcou ainda|Gols na temporada/)).toBeVisible()
  })

  test('Copa do Brasil (mata-mata) abre na Chave', async ({ page }) => {
    await page.goto('/world')
    await page.getByRole('link', { name: /Copa do Brasil/ }).click()

    await expect(page.getByRole('heading', { level: 1, name: 'Copa do Brasil' })).toBeVisible()
    await expect(tabs(page).getByRole('link', { name: 'Chave' })).toHaveAttribute('aria-current', 'page')
    await expect(tabs(page).getByRole('link', { name: 'Tabela' })).toHaveCount(0)
    await expect(page.getByRole('region', { name: 'Chave da copa' })).toBeVisible()
    await expect(page.getByTestId('cup-phase-primeira-fase')).toBeVisible()
  })

  test('liga de outro país abre com a tabela dela', async ({ page }) => {
    await page.goto('/world')
    await page.getByRole('link', { name: /Premier League/ }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Premier League' })).toBeVisible()
    await expect(page.locator('main tbody tr')).toHaveCount(4)
  })
})

test.describe('Calendário do clube', () => {
  test('relógio no cabeçalho e semanas com o jogo e a competição', async ({ page }) => {
    const clock = page.getByTestId('game-clock')
    await expect(clock).toContainText(/Semana \d+ · (Meio de semana|Fim de semana)/)
    await expect(page.getByTestId('game-clock-next')).toContainText(/contra .+ \((em casa|fora)\)/)

    await page.getByRole('link', { name: 'Ver calendário' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Calendário' })).toBeVisible()
    const weeks = page.getByRole('list', { name: 'Semanas do calendário' })
    await expect(weeks.locator('> li')).toHaveCount(8)
    await expect(weeks.locator('> li').first()).toHaveAttribute('aria-current', 'date')
    // O próximo jogo do relógio aparece no calendário, com a competição linkada.
    await expect(weeks.getByRole('link', { name: new RegExp(`${DEFAULT_LEAGUE} · Rodada \\d+`) }).first()).toBeVisible()

    // 26 semanas sempre passam por uma data FIFA (março, junho, set/out, novembro).
    await page.getByLabel('Período').selectOption('26')
    await expect(weeks.locator('> li')).toHaveCount(26)
    await expect(weeks.getByText('Data FIFA').first()).toBeVisible()
  })
})
