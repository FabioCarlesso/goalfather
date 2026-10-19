import { expect, test } from './helpers'
import type { Page } from '@playwright/test'

// Propostas entre técnicos humanos (issue #63). Roda contra MSW (`npm run e2e`)
// e contra o backend Spring (`npm run e2e:real`).
//
// O segundo técnico (o "rival") age pela API, de dentro da página: o `fetch`
// do `page.evaluate` passa pelo service worker do MSW no modo mock e pelo
// proxy do Vite no modo real — o mesmo caminho do app, nos dois modos. A
// sessão do técnico da UI não muda: o rival manda o próprio token no header.
//
// Toda navegação do técnico da UI depois do setup é CLIENT-SIDE (cliques no
// menu): no modo mock, um reload devolveria o estado do mock ao seed e
// apagaria a compra e a proposta feitas no meio do teste.

interface Rival {
  token: string
  clubId: number
  clubName: string
}

/** Cadastra um segundo técnico e reivindica o primeiro clube livre. `null` se não houver clube. */
async function registerRival(page: Page): Promise<Rival | null> {
  return page.evaluate(async (username) => {
    const json = { 'Content-Type': 'application/json' }
    const reg = await fetch('/api/auth/register', {
      method: 'POST',
      headers: json,
      body: JSON.stringify({ username, password: 'secret123' }),
    })
    const { token } = (await reg.json()) as { token: string }
    const auth = { Authorization: `Bearer ${token}` }
    const available = (await (await fetch('/api/clubs/available', { headers: auth })).json()) as
      { id: number; name: string }[]
    const club = available[0]
    if (!club) return null
    await fetch(`/api/clubs/${club.id}/claim`, { method: 'POST', headers: auth })
    return { token, clubId: club.id, clubName: club.name }
  }, `rival_${Date.now()}_${Math.floor(Math.random() * 1e6)}`)
}

/** Clube do técnico logado na UI (lido de /me com o token da própria sessão). */
async function myClubId(page: Page): Promise<number> {
  return page.evaluate(async () => {
    const res = await fetch('/api/auth/me', { headers: { Authorization: `Bearer ${localStorage.getItem('gf:token')}` } })
    return ((await res.json()) as { clubId: number }).clubId
  })
}

/** Compra pela UI o primeiro jogador do mercado. `false` se o mercado estiver vazio. */
async function buyFromMarket(page: Page): Promise<boolean> {
  await page.getByRole('navigation').getByRole('link', { name: 'Mercado' }).click()
  await expect(page.getByRole('heading', { name: 'Mercado' })).toBeVisible()
  const buy = page.getByRole('button', { name: 'Comprar' })
  await expect(buy.first().or(page.getByText('Nenhum jogador disponível'))).toBeVisible()
  if ((await buy.count()) === 0) return false
  await buy.first().click()
  await expect(page.getByText('Contratação concluída')).toBeVisible({ timeout: 10_000 })
  return true
}

test.describe('Offers flow', () => {
  test('proposta recebida é aceita e o jogador muda de clube', async ({ page }) => {
    // Com 11 jogadores o clube não pode vender (elenco mínimo): compra um antes.
    test.skip(!(await buyFromMarket(page)), 'mercado vazio (estado já consumido em e2e:real)')
    const rival = await registerRival(page)
    test.skip(rival == null, 'nenhum clube livre para o rival (estado já consumido em e2e:real)')

    // O rival propõe por um jogador de linha do elenco do técnico da UI.
    const sellerId = await myClubId(page)
    const created = await page.evaluate(async ({ rival, sellerId }) => {
      const club = (await (await fetch(`/api/clubs/${sellerId}`, {
        headers: { Authorization: `Bearer ${rival.token}` },
      })).json()) as { squad: { id: number; position: string }[] }
      const target = club.squad.find((p) => p.position !== 'GK')!
      const res = await fetch('/api/offers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${rival.token}` },
        body: JSON.stringify({ buyerClubId: rival.clubId, sellerClubId: sellerId, playerId: target.id, amount: 100_000_00 }),
      })
      return ((await res.json()) as { type: string }).type
    }, { rival: rival!, sellerId })
    expect(created).toBe('Success')

    await page.getByRole('navigation').getByRole('link', { name: /^Clube/ }).click()
    const panel = page.getByRole('region', { name: /Propostas/ })
    await expect(panel.getByText(rival!.clubName)).toBeVisible({ timeout: 20_000 })
    const sellButtons = page.getByRole('button', { name: 'Vender' })
    const before = await sellButtons.count()

    await panel.getByRole('button', { name: 'Aceitar' }).click()

    await expect(panel.getByText('Aceita')).toBeVisible({ timeout: 10_000 })
    await expect(sellButtons).toHaveCount(before - 1)
  })

  test('proposta enviada pela página do rival e depois retirada', async ({ page }) => {
    const rival = await registerRival(page)
    test.skip(rival == null, 'nenhum clube livre para o rival (estado já consumido em e2e:real)')

    // O rival compra um jogador para poder vender sem ficar abaixo do mínimo.
    const bought = await page.evaluate(async (rival) => {
      const auth = { Authorization: `Bearer ${rival.token}` }
      const market = (await (await fetch('/api/market', { headers: auth })).json()) as { player: { id: number } }[]
      if (market.length === 0) return false
      const res = await fetch('/api/market/buy', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ clubId: rival.clubId, playerId: market[0]!.player.id }),
      })
      return ((await res.json()) as { type: string }).type === 'Success'
    }, rival!)
    test.skip(!bought, 'mercado vazio (estado já consumido em e2e:real)')

    // Tabela → clube do rival → proposta por um jogador de linha.
    await page.getByRole('navigation').getByRole('link', { name: 'Tabela' }).click()
    await page.getByRole('link', { name: rival!.clubName }).first().click()
    await expect(page.getByRole('heading', { name: rival!.clubName })).toBeVisible()
    await expect(page.getByText('Técnico humano — aceita propostas')).toBeVisible()

    const outfield = page.getByRole('listitem').filter({ hasNotText: 'GK ·' })
    await outfield.first().getByRole('button', { name: 'Propor' }).click()
    await page.getByLabel('Valor da proposta (R$)').fill('50000')
    await page.getByRole('button', { name: 'Enviar proposta' }).click()
    await expect(page.getByText(/Proposta enviada por/)).toBeVisible({ timeout: 10_000 })

    // A proposta aparece nas enviadas da Dashboard; retirar a encerra.
    await page.getByRole('navigation').getByRole('link', { name: /^Clube/ }).click()
    const panel = page.getByRole('region', { name: /Propostas/ })
    await panel.getByRole('button', { name: 'Retirar' }).click()
    await expect(panel.getByText('Retirada')).toBeVisible({ timeout: 10_000 })
  })
})
