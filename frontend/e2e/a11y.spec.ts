import AxeBuilder from '@axe-core/playwright'
import { test as base, type Page } from '@playwright/test'
import { expect, test } from './helpers'

// Acessibilidade automatizada (issue #113): roda o axe-core nas telas
// principais e falha em violações `serious`/`critical` de WCAG 2.1 A/AA.
// Contra o MSW basta — o markup é o mesmo no backend real.
//
// O que o axe pega e o lint (`eslint-plugin-jsx-a11y`) não: contraste,
// `lang` do documento, nome acessível calculado de verdade (ex.: botão `×`
// sem `aria-label`), região rolável sem foco, landmarks.

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']

/** Espera a tela sair do skeleton antes de auditar: o axe vê o DOM do instante. */
async function waitForContent(page: Page) {
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await expect(page.getByTestId('skeleton')).toHaveCount(0)
}

async function expectNoSeriousViolations(page: Page) {
  const { violations } = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze()
  const blocking = violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
  // Mensagem legível no relatório: regra, impacto e os seletores afetados.
  const summary = blocking.map(
    (v) => `${v.id} (${v.impact}): ${v.help}\n  ${v.nodes.map((n) => n.target.join(' ')).join('\n  ')}`,
  )
  expect(summary, 'violações serious/critical do axe').toEqual([])
}

const ROUTES = [
  // Clube tem sub-rotas por aba (#107); cada aba tem markup próprio.
  { name: 'Clube — visão geral', path: '/dashboard/visao-geral' },
  { name: 'Clube — elenco', path: '/dashboard/elenco' },
  { name: 'Clube — finanças', path: '/dashboard/financas' },
  { name: 'Clube — departamento', path: '/dashboard/departamento' },
  { name: 'Escalação', path: '/lineup' },
  { name: 'Rodada', path: '/round' },
  { name: 'Mercado', path: '/market' },
  { name: 'Tabela', path: '/standings' },
  { name: 'Copa', path: '/cup' },
  { name: 'Histórico', path: '/history' },
] as const

test.describe('Acessibilidade (axe)', () => {
  for (const { name, path } of ROUTES) {
    test(`${name} (${path}) sem violações serious/critical`, async ({ page }) => {
      await page.goto(path)
      await waitForContent(page)
      await expectNoSeriousViolations(page)
    })
  }

  // Depois da rodada a tela ganha feed cheio, sumário de estatísticas e
  // banners — markup que a auditoria da rodada "fria" não vê. De quebra,
  // confere a região `aria-live` dos gols contra o próprio feed.
  test('Rodada jogada sem violações e com gols anunciados', async ({ page }) => {
    await page.goto('/round')
    // Instantânea (issue #115): a auditoria é do markup final, não do ritmo.
    await page.getByLabel('Velocidade').selectOption('INSTANT')
    await page.getByRole('button', { name: 'Estou pronto' }).click()
    await page.getByRole('button', { name: 'Jogar rodada' }).click()
    await expect(page.getByText(/Pontuações atualizadas após a rodada/i)).toBeVisible({
      timeout: 30_000,
    })

    const announcer = page.getByTestId('goal-announcer')
    await expect(announcer).toHaveAttribute('aria-live', 'polite')
    // O placar sai do mock aleatório: com gol, a região traz o último; sem
    // gol (0 × 0), fica vazia.
    const goals = await page.getByText(/GOL!/).count()
    if (goals > 0) await expect(announcer).toHaveText(/^Gol do .+, \d+ a \d+, aos? \d+ minutos?$/)
    else await expect(announcer).toBeEmpty()

    await expectNoSeriousViolations(page)
  })
})

// Login fica fora da fixture autenticada: é a tela de quem ainda não entrou.
base('Login (/login) sem violações serious/critical', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await expectNoSeriousViolations(page)
})
