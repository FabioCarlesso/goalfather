import { expect, test } from '@playwright/test'

// Onboarding (FRONTEND.md / issue #8): a primeira visita a "/" cai em
// /welcome; depois de concluir, visitas seguintes vão direto à dashboard.
// Cada teste do Playwright roda em contexto isolado, então o localStorage
// começa vazio — simulando o "primeiro acesso".

test.describe('Onboarding', () => {
  test('primeira visita mostra /welcome; segunda vai direto à dashboard', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/welcome$/)
    await expect(page.getByRole('heading', { name: /Bem-vindo ao GoalFather/i })).toBeVisible()

    await page.getByRole('button', { name: 'Começar' }).click()
    await expect(page).toHaveURL(/\/dashboard$/)

    // Segunda visita à raiz: já onboarded → dashboard sem passar pelo welcome.
    await page.goto('/')
    await expect(page).toHaveURL(/\/dashboard$/)
  })

  test('item Tutorial reabre o welcome mesmo após onboarded', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: 'Pular' }).click()
    await expect(page).toHaveURL(/\/dashboard$/)

    await page.getByRole('link', { name: 'Tutorial' }).click()
    await expect(page).toHaveURL(/\/welcome$/)
    await expect(page.getByRole('heading', { name: /Bem-vindo ao GoalFather/i })).toBeVisible()
  })
})
