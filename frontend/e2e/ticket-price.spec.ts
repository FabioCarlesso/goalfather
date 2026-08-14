import { expect, test } from './helpers'

// Preco de ingresso definido pelo tecnico (issue #59). Roda contra MSW
// (`npm run e2e`) e contra o backend Spring (`npm run e2e:real`).
//
// O efeito do preco (publico e receita) aparece no extrato da rodada, coberto
// pelo round.spec; aqui interessa a decisao: definir e o preco ficar gravado.

test.describe('Ticket price', () => {
  test('define o preco do ingresso e ele persiste no clube', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: 'Preço do ingresso' })).toBeVisible()

    const input = page.getByLabel('Preço (R$)')
    await input.fill('120')
    await page.getByRole('button', { name: 'Definir preço' }).click()

    await expect(page.getByText(/em vigor: R\$\s?120/)).toBeVisible()

    // Sai da tela e volta: o preco vem do clube (estado de servidor), nao de
    // estado local do componente.
    await page.getByRole('link', { name: 'Tabela' }).click()
    await expect(page.getByRole('heading', { name: 'Tabela' })).toBeVisible()
    await page.getByRole('link', { name: 'Clube' }).click()

    await expect(page.getByText(/em vigor: R\$\s?120/)).toBeVisible()
  })

  test('preco fora da faixa nao pode ser enviado', async ({ page }) => {
    await page.goto('/dashboard')

    await page.getByLabel('Preço (R$)').fill('500')

    await expect(page.getByText(/informe um valor entre/i)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Definir preço' })).toBeDisabled()
  })
})
