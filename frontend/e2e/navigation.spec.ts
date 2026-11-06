import type { Page } from '@playwright/test'
import { expect, myClubId, registerRival, test } from './helpers'

// Navegação responsiva (issue #106): barra horizontal a partir de `lg` (1024px),
// botão de menu abaixo disso, e nenhuma rolagem horizontal da página.

const menuButton = (page: Page) => page.getByRole('button', { name: /Menu de navegação/ })
const mainNav = (page: Page) => page.getByRole('navigation', { name: 'Principal' })

async function expectNoHorizontalScroll(page: Page, context: string) {
  const { scrollWidth, innerWidth } = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }))
  expect(scrollWidth, `rolagem horizontal em ${context}`).toBeLessThanOrEqual(innerWidth)
}

/** Um rival propõe por um jogador do técnico da UI, que compra um antes para não ficar no elenco mínimo. */
async function receivePendingOffer(page: Page) {
  const rival = await registerRival(page)
  expect(rival, 'clube livre para o rival').not.toBeNull()
  const sellerId = await myClubId(page)
  const result = await page.evaluate(async ({ rival, sellerId }) => {
    const json = { 'Content-Type': 'application/json' }
    const mine = { Authorization: `Bearer ${localStorage.getItem('gf:token')}` }
    const theirs = { Authorization: `Bearer ${rival!.token}` }
    const market = (await (await fetch('/api/market', { headers: mine })).json()) as { player: { id: number } }[]
    await fetch('/api/market/buy', {
      method: 'POST',
      headers: { ...json, ...mine },
      body: JSON.stringify({ clubId: sellerId, playerId: market[0]!.player.id }),
    })
    const club = (await (await fetch(`/api/clubs/${sellerId}`, { headers: theirs })).json()) as
      { squad: { id: number; position: string }[] }
    const target = club.squad.find((p) => p.position !== 'GK')!
    const res = await fetch('/api/offers', {
      method: 'POST',
      headers: { ...json, ...theirs },
      body: JSON.stringify({ buyerClubId: rival!.clubId, sellerClubId: sellerId, playerId: target.id, amount: 100_000_00 }),
    })
    return ((await res.json()) as { type: string }).type
  }, { rival, sellerId })
  expect(result).toBe('Success')
}

test.describe('Navegação com botão de menu (< lg)', () => {
  test.use({ viewport: { width: 375, height: 740 } })

  test('navega por três telas pelo menu, que fecha a cada escolha', async ({ page }) => {
    await expect(mainNav(page)).toBeHidden()

    for (const { label, url } of [
      { label: 'Mercado', url: /\/market$/ },
      { label: 'Tabela', url: /\/standings$/ },
      { label: 'Copa', url: /\/cup$/ },
    ]) {
      await expect(menuButton(page)).toHaveAttribute('aria-expanded', 'false')
      await menuButton(page).click()
      await expect(menuButton(page)).toHaveAttribute('aria-expanded', 'true')

      await mainNav(page).getByRole('link', { name: label }).click()

      await expect(page).toHaveURL(url)
      await expect(mainNav(page)).toBeHidden()
      await expect(page.getByRole('heading', { name: label, level: 1 })).toBeVisible()
      await expectNoHorizontalScroll(page, `${label} em 375px`)
    }
  })

  test('Esc fecha o menu e devolve o foco ao botão', async ({ page }) => {
    await menuButton(page).click()
    await expect(mainNav(page)).toBeVisible()

    await page.keyboard.press('Escape')

    await expect(mainNav(page)).toBeHidden()
    await expect(menuButton(page)).toHaveAttribute('aria-expanded', 'false')
    await expect(menuButton(page)).toBeFocused()
  })

  test('badge de propostas aparece no botão com o menu fechado e no item Clube', async ({ page }) => {
    test.slow() // espera um ciclo do polling de propostas
    await expect(menuButton(page)).toHaveAccessibleName('Menu de navegação')

    await receivePendingOffer(page)

    // O polling da query de propostas é que traz o badge.
    await expect(menuButton(page)).toHaveAccessibleName(/1 proposta/, { timeout: 20_000 })
    await expect(menuButton(page).getByText('1', { exact: true })).toBeVisible()

    await menuButton(page).click()
    await expect(mainNav(page).getByRole('link', { name: /^Clube/ }).getByTitle(/1 proposta/)).toBeVisible()
  })

  test('menu de usuário mostra Tutorial e Sair', async ({ page }) => {
    await page.getByRole('button', { name: /Menu do usuário/ }).click()
    await page.getByRole('link', { name: 'Tutorial' }).click()
    await expect(page).toHaveURL(/\/welcome$/)

    await page.getByRole('button', { name: /Menu do usuário/ }).click()
    await page.getByRole('button', { name: 'Sair' }).click()
    await expect(page).toHaveURL(/\/login$/)
  })
})

test.describe('Em 768px (tablet) continua o botão de menu', () => {
  test.use({ viewport: { width: 768, height: 800 } })

  test('a barra horizontal fica oculta e o menu navega', async ({ page }) => {
    await expect(mainNav(page)).toBeHidden()

    await menuButton(page).click()
    await mainNav(page).getByRole('link', { name: 'Tabela' }).click()

    await expect(page).toHaveURL(/\/standings$/)
    await expect(mainNav(page)).toBeHidden()
  })
})

// A largura útil do cabeçalho é a mesma a partir de `lg` (max-w-5xl): 1024 é o pior caso.
for (const width of [1024, 1366]) {
  test.describe(`Barra horizontal em ${width}px`, () => {
    test.use({ viewport: { width, height: 800 } })

    test('cabe em uma linha, com badge e nome de usuário longo', async ({ page }) => {
      test.slow() // espera um ciclo do polling de propostas
      await expect(menuButton(page)).toBeHidden()

      await receivePendingOffer(page)
      const clube = mainNav(page).getByRole('link', { name: /^Clube/ })
      await expect(clube.getByTitle(/1 proposta/)).toBeVisible({ timeout: 20_000 })

      const tops = await mainNav(page).getByRole('link').evaluateAll((links) =>
        links.map((link) => link.getBoundingClientRect().top),
      )
      expect(new Set(tops).size).toBe(1)

      // Logo e indicador em uma linha só: a linha do cabeçalho tem a altura dos botões.
      const row = page.getByRole('banner').locator('> div')
      const fits = await row.evaluate((el) => el.scrollWidth <= el.clientWidth)
      expect(fits, 'conteúdo do cabeçalho cabe na largura').toBe(true)
      const logo = await page.getByRole('banner').getByText('GoalFather').boundingBox()
      expect(logo?.height ?? Infinity).toBeLessThan(40)
      await expectNoHorizontalScroll(page, `cabeçalho em ${width}px`)
    })
  })
}

// Rotas autenticadas sem parâmetro + as de detalhe, abertas por deep-link.
const ROUTES = ['/welcome', '/dashboard', '/lineup', '/round', '/market', '/standings', '/cup', '/history', '/clubs/2']

for (const width of [360, 768, 1280]) {
  test.describe(`Sem rolagem horizontal em ${width}px`, () => {
    test.use({ viewport: { width, height: 800 } })

    for (const route of ROUTES) {
      test(route, async ({ page }) => {
        await page.goto(route)
        await expect(page.locator('main h1').first()).toBeVisible()
        await page.waitForLoadState('networkidle')
        await expectNoHorizontalScroll(page, `${route} em ${width}px`)
      })
    }

    test('/round/match/:id', async ({ page }) => {
      await page.goto('/round')
      await page.locator('a[href^="/round/match/"]').first().click()
      await expect(page).toHaveURL(/\/round\/match\/\d+$/)
      await expect(page.getByText(/Bola rolando/i).first()).toBeVisible({ timeout: 10_000 })
      await expectNoHorizontalScroll(page, `/round/match/:id em ${width}px`)
    })
  })
}
