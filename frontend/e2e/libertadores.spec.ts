import { expect, test } from './helpers'

// Libertadores (issue #132): grupos e chave. O pack do E2E (`data/world/e2e`)
// NÃO tem Libertadores — o backend real responde 404 e a página mostra o estado
// vazio; o MSW serve um retrato da fase de grupos (`src/mocks/libertadores.ts`).
// O mesmo spec cobre os dois: com Libertadores, os grupos; sem, a explicação.
// Jogar até um jogo de Libertadores fica coberto pelo `LibertadoresFlowTest`
// (backend) até o recorte do E2E ganhar a competição.
test.describe('Libertadores', () => {
  test('mostra os grupos sorteados ou explica que o mundo não tem Libertadores', async ({ page }) => {
    await page.goto('/libertadores')

    const empty = page.getByText('Este mundo não tem Libertadores.')
    const title = page.getByRole('heading', { name: /^Copa Libertadores \d{4}$/ })
    await expect(empty.or(title)).toBeVisible()
    test.skip(await empty.isVisible(), 'mundo sem Libertadores (pack do E2E no backend real)')

    for (const letter of 'ABCDEFGH') {
      await expect(page.getByRole('region', { name: `Grupo ${letter}` })).toBeVisible()
    }
    await expect(page.getByTestId('libertadores-group-grupo-a').locator('[data-zone="QUALIFICATION"]')).toHaveCount(2)
    await expect(page.getByRole('heading', { name: 'Mata-mata' })).toBeVisible()
  })
})
