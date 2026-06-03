import { expect, test } from '@playwright/test'
import { authenticate } from './helpers'

// Auth + onboarding (issues #18/#19): sem sessão, qualquer rota cai no /login.
// Após cadastrar e escolher um clube, o usuário chega à dashboard; o tutorial
// (/welcome) continua acessível pelo item de menu. Cada teste do Playwright
// roda em contexto isolado, então o localStorage começa vazio (primeiro acesso).

test.describe('Auth + onboarding', () => {
  test('sem sessão, a raiz redireciona para o login', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('heading', { name: /GoalFather/i })).toBeVisible()
  })

  test('cadastrar e escolher clube leva à dashboard; Tutorial reabre o welcome', async ({ page }) => {
    await authenticate(page)
    await expect(page.getByRole('heading', { name: 'Goal Father FC' })).toBeVisible()

    await page.getByRole('link', { name: 'Tutorial' }).click()
    await expect(page).toHaveURL(/\/welcome$/)
    await expect(page.getByRole('heading', { name: /Bem-vindo ao GoalFather/i })).toBeVisible()
  })
})
