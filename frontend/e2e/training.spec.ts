import { expect, test } from './helpers'

// Treino semanal com foco escolhido pelo tecnico (issue #58). Roda contra MSW
// (`npm run e2e`) e contra o backend Spring (`npm run e2e:real`).
//
// O efeito do treino (evolucao/lesao) e verificado no round.spec, que joga a
// rodada ate o fim; aqui interessa a decisao: escolher e o foco ficar gravado.

test.describe('Training focus', () => {
  test('escolhe o foco da semana e ele persiste no clube', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: 'Treino da semana' })).toBeVisible()

    await page.getByRole('button', { name: 'Físico' }).click()
    await expect(page.getByRole('button', { name: 'Físico' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )

    // Sai da tela e volta: o foco vem do clube (estado de servidor), nao de
    // estado local do componente.
    await page.getByRole('link', { name: 'Tabela' }).click()
    await expect(page.getByRole('heading', { name: 'Tabela' })).toBeVisible()
    await page.getByRole('link', { name: 'Clube' }).click()

    await expect(page.getByRole('button', { name: 'Físico' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })
})
