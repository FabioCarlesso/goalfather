import { expect, test } from './helpers'

// Seleções nas datas FIFA (issue #134). Roda contra o MSW e contra o backend
// real: o que muda entre os dois é QUEM é convocado (o mock pré-visualiza os 3
// melhores do elenco; o backend convoca pela nacionalidade no mundo inteiro),
// então o teste afirma a estrutura da tela, não os nomes.
//
// Atravessar uma data FIFA jogando (a primeira é em março, ~20 encaixes
// adiante) ficaria lento demais para a suíte; o efeito no clube está coberto
// pelos testes de domínio e de serviço do backend.

test.describe('Seleções', () => {
  test('mostra as datas FIFA, as Eliminatórias da CONMEBOL e o ranking', async ({ page }) => {
    await page.goto('/national-teams')

    await expect(page.getByRole('heading', { name: 'Seleções', level: 1 })).toBeVisible()
    await expect(page.getByText('23/03/2026 a 31/03/2026').first()).toBeVisible()
    const qualifiers = page.getByRole('region', { name: /Tabela: Eliminatórias CONMEBOL 2026–2027/ })
    await expect(qualifiers.getByRole('row')).toHaveCount(11) // cabeçalho + 10 seleções
    await expect(qualifiers.getByRole('rowheader', { name: /Brasil/ })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Ranking de seleções' })).toBeVisible()
  })

  test('a visão geral do clube avisa a convocação antes da janela, quando há convocados', async ({ page }) => {
    await page.goto('/dashboard/visao-geral')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    const notice = page.getByRole('heading', { name: /convocados? para a próxima data FIFA/ })
    // No backend real o clube sorteado pode não ter convocado nenhum; aí o aviso
    // não aparece. Havendo, ele leva à página das seleções.
    if (await notice.waitFor({ timeout: 5_000 }).then(() => true, () => false)) {
      await page.getByRole('link', { name: 'Ver seleções' }).click()
      await expect(page.getByRole('heading', { name: 'Seleções', level: 1 })).toBeVisible()
    }
  })
})
