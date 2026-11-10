import { expect, test } from './helpers'

// Fluxos de mercado (issue #12): comprar (em /market) e vender (em /dashboard).
// Rodam contra MSW (`npm run e2e`) e contra o backend Spring (`npm run e2e:real`).
//
// Contagens são assertadas de forma relativa (antes/depois), não absoluta, para
// sobreviver a execuções repetidas contra o backend real, onde o estado persiste
// (o mesmo jogador não pode ser comprado/vendido duas vezes).

test.describe('Market flow', () => {
  test('comprar um jogador reduz o mercado e debita o caixa', async ({ page }) => {
    await page.goto('/market')
    await expect(page).toHaveTitle(/Mercado · GoalFather/)
    await expect(page.getByRole('heading', { name: 'Mercado' })).toBeVisible()

    const buyButtons = page.getByRole('button', { name: 'Comprar' })
    await expect(buyButtons.first().or(page.getByText('Nenhum jogador disponível'))).toBeVisible()
    const before = await buyButtons.count()
    test.skip(before === 0, 'mercado vazio (estado já consumido em e2e:real)')

    const cash = page.getByText(/^Caixa:/)
    await expect(cash).toContainText('US$')
    const cashBefore = await cash.textContent()

    await buyButtons.first().click()

    // Sucesso de negócio: toast disparado pelo useBuyPlayer.
    await expect(page.getByText(/contratado!/)).toBeVisible({ timeout: 10_000 })
    // O jogador comprado sai da lista do mercado e o caixa do topo é debitado.
    await expect(buyButtons).toHaveCount(before - 1)
    await expect(cash).not.toHaveText(cashBefore ?? '')
  })

  test('comprar acima do caixa continua possível e a recusa vem do backend', async ({ page }) => {
    await page.goto('/market')
    const buyButtons = page.getByRole('button', { name: 'Comprar' })
    await expect(buyButtons.first().or(page.getByText('Nenhum jogador disponível'))).toBeVisible()
    const cash = page.getByText(/^Caixa:/)
    await expect(cash).toContainText('US$')

    // Gasta o caixa nos mais baratos (ordem padrão) até alguém ficar caro demais.
    // Espera lista e caixa atualizarem antes de reavaliar, senão compraria a mais.
    const overCash = page.getByRole('listitem').filter({ hasText: 'acima do seu caixa' })
    for (let bought = 0; bought < 6 && (await overCash.count()) === 0; bought++) {
      const count = await buyButtons.count()
      if (count <= 1) break
      const cashBefore = (await cash.textContent()) ?? ''
      await buyButtons.first().click()
      await expect(buyButtons).toHaveCount(count - 1, { timeout: 10_000 })
      await expect(cash).not.toHaveText(cashBefore)
    }
    test.skip((await overCash.count()) === 0, 'ninguém acima do caixa com o estado atual do mercado')

    const button = overCash.first().getByRole('button', { name: 'Comprar' })
    await expect(button).toBeEnabled()
    await button.click()

    await expect(page.getByText(/^Caixa insuficiente/)).toBeVisible({ timeout: 10_000 })
    await expect(overCash.first()).toBeVisible()
  })

  test('filtro, ordenação e busca sobrevivem ao recarregar a página', async ({ page }) => {
    await page.goto('/market?sort=age')
    const search = page.getByLabel('Buscar por nome')
    await expect(search.or(page.getByText('Nenhum jogador disponível'))).toBeVisible()
    test.skip(!(await search.isVisible()), 'mercado vazio (estado já consumido em e2e:real)')

    // Cada mudança parte do estado já aplicado na URL, então espera ele assentar
    // antes da próxima: o router aplica a navegação dentro de uma transição.
    await page.getByRole('button', { name: 'AT', exact: true }).click()
    await expect(page).toHaveURL(/pos=FW/)
    await page.getByRole('button', { name: 'Ordem: crescente' }).click()
    await expect(page).toHaveURL(/dir=desc/)
    // `pressSequentially` digita tecla a tecla: letra perdida aqui acusa o campo
    // voltando a ser controlado pelo valor da URL, que muda dentro de uma transição.
    await search.pressSequentially('Aparício Vidigal', { delay: 0 })
    await expect(search).toHaveValue('Aparício Vidigal')
    await expect(page).toHaveURL(/pos=FW/)
    await expect(page).toHaveURL(/sort=age/)
    await expect(page).toHaveURL(/dir=desc/)
    await expect(page).toHaveURL(/q=Apar/)
    await expect(page.getByText('Nenhum jogador encontrado.')).toBeVisible()

    await page.reload()

    await expect(page.getByLabel('Buscar por nome')).toHaveValue('Aparício Vidigal')
    await expect(page.getByRole('button', { name: 'AT', exact: true })).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByRole('button', { name: 'Ordem: decrescente' })).toBeVisible()
    await expect(page.getByLabel('Ordenar por')).toHaveValue('age')

    // A digitação usa `replace`: voltar desfaz o sentido da ordenação, não a busca letra a letra.
    await page.getByRole('button', { name: 'Limpar filtros' }).click()
    await expect(page.getByLabel('Buscar por nome')).toHaveValue('')
    await expect(page).not.toHaveURL(/q=/)
    await page.goBack()
    await expect(page.getByLabel('Buscar por nome')).toHaveValue('Aparício Vidigal')
  })

  test('em 360px os controles não criam rolagem horizontal na área do Mercado', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 })
    await page.goto('/market')
    const section = page.locator('main section')
    await expect(section.getByRole('heading', { name: 'Mercado' })).toBeVisible()
    await expect(section.getByRole('button', { name: 'Comprar' }).first().or(section.getByText('Nenhum jogador disponível'))).toBeVisible()

    const overflow = await section.evaluate((el) => el.scrollWidth - el.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
    const box = await section.boundingBox()
    expect((box?.x ?? 0) + (box?.width ?? 0)).toBeLessThanOrEqual(360)
  })

  test('vender um jogador remove do elenco', async ({ page }) => {
    await page.goto('/dashboard/elenco')
    await expect(page).toHaveTitle(/Clube · GoalFather/)
    await expect(page.getByRole('heading', { name: 'Elenco' })).toBeVisible()

    const sellButtons = page.getByRole('button', { name: /^Vender / })
    await expect(sellButtons.first()).toBeVisible()
    const before = await sellButtons.count()
    test.skip(before === 0, 'elenco vazio')

    // A venda pede confirmação num modal (issue #107): cancelar não vende…
    await sellButtons.first().click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: 'Cancelar' }).click()
    await expect(dialog).toBeHidden()
    await expect(sellButtons).toHaveCount(before)

    // …e confirmar tira o jogador do elenco (uma linha — e um "Vender" — a menos).
    await sellButtons.first().click()
    await dialog.getByRole('button', { name: 'Confirmar venda' }).click()
    await expect(sellButtons).toHaveCount(before - 1)
  })
})
