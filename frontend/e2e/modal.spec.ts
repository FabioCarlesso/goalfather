import type { Page } from '@playwright/test'
import { expect, test } from './helpers'

// Backdrop do `Modal` (issue #119): o `::backdrop` precisa ser preto
// translúcido, com a página visível por trás, não um bloco opaco.
//
// Guarda de regressão. Na investigação da #119, Chromium 148 e Chrome 157 com
// perfil limpo calcularam `oklab(0 0 0 / 0.6)`, ou seja, o CSS do `Modal` está
// certo; o fundo oliva opaco visto no teste manual só aparecia num perfil do
// Chrome com extensões. Se alguém trocar a classe `backdrop:` por algo opaco,
// este teste acusa.

/** Lê a cor calculada do `::backdrop` do `<dialog>` aberto como [r, g, b, a]. */
async function backdropRgba(page: Page): Promise<number[]> {
  const color = await page.evaluate(() => {
    const dialog = document.querySelector('dialog[open]')
    if (!dialog) throw new Error('nenhum <dialog> aberto')
    return getComputedStyle(dialog, '::backdrop').backgroundColor
  })
  // Normaliza `rgb(r, g, b)`, `rgba(r, g, b, a)`, `rgb(r g b / a)` e
  // `oklab(L a b / alfa)`, que é como o Chromium devolve o `color-mix(in oklab)`
  // do Tailwind v4. Para preto, os três canais são 0 em qualquer um dos formatos.
  const nums = color.match(/[\d.]+/g)?.map(Number) ?? []
  expect(nums.length, `cor inesperada: ${color}`).toBeGreaterThanOrEqual(3)
  return [nums[0]!, nums[1]!, nums[2]!, nums[3] ?? 1]
}

test('o backdrop do modal é preto translúcido', async ({ page }) => {
  await page.goto('/dashboard/elenco')
  await page.getByRole('button', { name: /^Vender / }).first().click()
  await expect(page.getByRole('dialog')).toBeVisible()

  const [r, g, b, a] = await backdropRgba(page)
  expect(Math.max(r!, g!, b!), 'canais RGB do backdrop').toBeLessThanOrEqual(10)
  expect(a, 'alfa do backdrop').toBeGreaterThanOrEqual(0.5)
  expect(a, 'alfa do backdrop').toBeLessThanOrEqual(0.7)
})
