import { test as base, expect, type Page } from '@playwright/test'

// Helpers de auth para o E2E (issues #18/#19). O app agora exige login e
// seleção de clube antes de qualquer tela — cada teste de feature passa por
// aqui primeiro. O estado de auth do mock é persistido no localStorage
// (ver src/mocks/seed.ts), então sobrevive aos `page.goto` de deep-link.

let seq = 0

/** Cadastra um usuário único e reivindica o primeiro clube (Goal Father FC). */
export async function authenticate(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Cadastre-se' }).click()
  const username = `e2e_${Date.now()}_${seq++}`
  await page.getByLabel('Usuário').fill(username)
  await page.getByLabel('Senha').fill('secret123')
  await page.getByRole('button', { name: 'Cadastrar' }).click()

  await expect(page.getByRole('heading', { name: 'Escolha seu clube' })).toBeVisible()
  await page.getByRole('button', { name: 'Treinar este clube' }).first().click()
  await expect(page).toHaveURL(/\/dashboard$/)
}

/**
 * `test` que já entra autenticado e com clube reivindicado. As specs de
 * feature importam daqui em vez de '@playwright/test'.
 */
export const test = base.extend({
  // O 2º argumento da fixture é nomeado `run` (não `use`) de propósito: o
  // plugin react-hooks confundiria `use(...)` com o hook `use` do React.
  page: async ({ page }, run) => {
    await authenticate(page)
    await run(page)
  },
})

export { expect }
