import { expect, test } from './helpers'

// Renovação de contrato com exigência do jogador (issue #138). Roda contra MSW
// (`npm run e2e`) e contra o backend Spring (`npm run e2e:real`): os números
// (o que ele pede) vêm da resposta do servidor, nunca do teste.

test.describe('Renovação de contrato', () => {
  test('duração fora do que ele aceita é recusada com o motivo; oferecer o pedido renova', async ({ page }) => {
    await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: /^Clube/ }).click()
    await page.getByRole('navigation', { name: 'Seções do clube' }).getByRole('link', { name: 'Elenco' }).click()

    // Um jogador de 24 a 29 anos: no auge, ele não assina 5 temporadas (regra do backend).
    const row = page.getByRole('listitem').filter({ hasText: / · 2[4-9]a/ }).first()
    const renew = row.getByRole('button', { name: /^Renovar / })
    await expect(renew).toBeVisible()
    const name = ((await renew.getAttribute('aria-label')) ?? '').replace('Renovar ', '')
    await renew.click()

    const dialog = page.getByRole('dialog', { name: `Renovar com ${name}` })
    await expect(dialog).toBeVisible()
    // Salário alto (sem ofensa, não esfria) e 5 temporadas: recusa pela duração.
    await dialog.getByLabel(/Salário mensal/).fill('1000000')
    await dialog.getByLabel('Duração').selectOption('5')
    await dialog.getByRole('button', { name: 'Oferecer renovação' }).click()
    await expect(dialog.getByRole('alert')).toContainText('contrato longo demais', { timeout: 10_000 })

    // Copiar o pedido traz a duração para a faixa dele; agora ele aceita.
    await dialog.getByRole('button', { name: 'Oferecer o que ele pede' }).click()
    await dialog.getByRole('button', { name: 'Oferecer renovação' }).click()

    await expect(page.getByText(`${name} renovou o contrato!`)).toBeVisible({ timeout: 10_000 })
    await expect(dialog).toBeHidden()
  })
})
