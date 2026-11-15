import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { register } from './helpers'

// Onboarding país → liga → clube (issue #131). Roda contra MSW (`npm run e2e`)
// e contra o backend real com o pack E2E (`npm run e2e:real`): só nomes de
// país, liga e clube do pack aparecem aqui — elencos, força e caixa variam.

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']

async function expectNoSeriousViolations(page: Page) {
  const { violations } = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze()
  const blocking = violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
  expect(blocking.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([])
}

const step = (page: Page) => page.getByRole('list', { name: 'Etapas da escolha' }).locator('[aria-current="step"]')
const countries = (page: Page) => page.getByRole('list', { name: 'Países' })
const leagues = (page: Page) => page.getByRole('list', { name: 'Ligas' })
const clubCards = (page: Page) => page.getByRole('list', { name: /Clubes livres/ }).getByRole('article')

test.describe('Onboarding país → liga → clube', () => {
  test('escolhe um clube da Premier League depois de passar pelo Brasil', async ({ page }) => {
    await register(page)

    // Passo 1: países do mundo, com ligas e clubes livres.
    await expect(step(page)).toHaveText('1. País')
    await expect(countries(page).getByRole('button', { name: /Brasil/ })).toContainText(/\d+ ligas? · \d+ clubes? livres?/)
    await expectNoSeriousViolations(page)

    // Passo 2: as ligas do Brasil, da elite para baixo, cada uma com a temporada.
    await countries(page).getByRole('button', { name: /Brasil/ }).click()
    await expect(step(page)).toHaveText('2. Liga')
    await expect(leagues(page).getByRole('button')).toHaveText([
      /^Campeonato Brasileiro Série A.*1ª divisão · Temporada \d{4}/,
      /^Campeonato Brasileiro Série B.*2ª divisão · Temporada \d{4}/,
    ])
    await expectNoSeriousViolations(page)

    // Passo 3: clubes livres da Série A.
    await leagues(page).getByRole('button', { name: /Série A/ }).click()
    await expect(step(page)).toHaveText('3. Clube')
    await expect(clubCards(page).first()).toContainText('Campeonato Brasileiro Série A')
    await expectNoSeriousViolations(page)

    // Volta: o "voltar" do navegador desfaz um passo (a escolha vive na URL)...
    await page.goBack()
    await expect(step(page)).toHaveText('2. Liga')
    // ...e o botão da tela leva aos países.
    await page.getByRole('button', { name: /Voltar aos países/ }).click()
    await expect(step(page)).toHaveText('1. País')

    // Inglaterra → Premier League, temporada que atravessa o ano.
    await countries(page).getByRole('button', { name: /Inglaterra/ }).click()
    const premier = leagues(page).getByRole('button', { name: /Premier League/ })
    await expect(premier).toContainText(/Temporada \d{4}\/\d{2}/)
    await premier.click()

    const card = clubCards(page).first()
    await expect(card).toContainText('Inglaterra · Premier League')
    const clubName = (await card.getAttribute('aria-label')) ?? ''
    expect(clubName).not.toBe('')
    await card.getByRole('button', { name: 'Treinar este clube' }).click()

    // Dashboard do clube escolhido, e a Tabela abre na liga dele.
    await expect(page).toHaveURL(/\/dashboard\/visao-geral$/)
    await expect(page.getByRole('heading', { level: 1, name: clubName })).toBeVisible()
    await page.getByRole('link', { name: 'Tabela' }).click()
    await expect(page.getByRole('heading', { level: 2, name: /^Premier League \d{4}\/\d{2}$/ })).toBeVisible()
    await expect(page.locator('tbody tr[aria-current="true"]')).toContainText(clubName)
  })

  test('o clube escolhido sai da lista do próximo técnico', async ({ page }) => {
    await register(page)
    await countries(page).getByRole('button', { name: /Brasil/ }).click()
    await leagues(page).getByRole('button', { name: /Série A/ }).click()
    const first = clubCards(page).first()
    const taken = (await first.getAttribute('aria-label')) ?? ''
    await first.getByRole('button', { name: 'Treinar este clube' }).click()
    await expect(page).toHaveURL(/\/dashboard\/visao-geral$/)

    // Outro técnico pela API, de dentro desta página: no mock o estado vive
    // no navegador, e o MSW de outro contexto não veria o claim.
    const available = await page.evaluate(async () => {
      const reg = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: `seg_${Date.now()}`, password: 'secret123' }),
      })
      const { token } = (await reg.json()) as { token: string }
      const res = await fetch('/api/clubs/available?competitionId=bra-serie-a', { headers: { Authorization: `Bearer ${token}` } })
      return ((await res.json()) as { name: string; competitionId: string }[])
    })
    expect(available.map((c) => c.name)).not.toContain(taken)
    expect(available.every((c) => c.competitionId === 'bra-serie-a')).toBe(true)
  })
})
