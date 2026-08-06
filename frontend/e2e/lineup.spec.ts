import { expect, test } from './helpers'

// Escalação (issue #12): preencher os 11 titulares, salvar e validar que a
// escalação persiste no servidor. Roda contra MSW e contra o backend real.
//
// A persistência é checada por navegação client-side (sair de /lineup e voltar),
// e não por reload físico: um F5 reinicia o estado in-memory do MSW, enquanto a
// navegação SPA força um refetch do clube — validando o round-trip no servidor
// em ambos os modos.

test.describe('Lineup flow', () => {
  test('salvar a escalação e mantê-la após navegar', async ({ page }) => {
    await page.goto('/lineup')
    await expect(page.getByRole('heading', { name: 'Escalação' })).toBeVisible()

    // Os 11 selects de slot vivem na grade (o select de formação fica no header).
    const slots = page.locator('div.grid select')
    await expect(slots).toHaveCount(11)

    // Em 4-4-2 o elenco-semente tem exatamente 1 GK, 4 ZG, 4 MC e 2 AT, então a
    // primeira opção disponível de cada slot preenche titulares distintos.
    for (let i = 0; i < 11; i++) {
      await slots.nth(i).selectOption({ index: 1 })
    }

    const saveBtn = page.getByRole('button', { name: 'Salvar escalação' })
    await expect(saveBtn).toBeEnabled()
    await saveBtn.click()
    await expect(page.getByText('Escalação salva ✓')).toBeVisible({ timeout: 10_000 })

    // Sai e volta — o clube é refetchado e a escalação salva é recarregada.
    await page.getByRole('link', { name: 'Clube' }).click()
    await expect(page).toHaveURL(/\/dashboard$/)
    await page.getByRole('link', { name: 'Escalação' }).click()

    await expect(page.getByRole('heading', { name: 'Escalação' })).toBeVisible()
    await expect(page.getByText('11/11 titulares')).toBeVisible({ timeout: 10_000 })
  })

  // Postura tática (issue #56): a escolha é salva junto da escalação e chega à
  // partida — o KickOff a exibe no placar.
  test('escolher a postura, salvar e vê-la na partida', async ({ page }) => {
    await page.goto('/lineup')
    await expect(page.getByRole('heading', { name: 'Escalação' })).toBeVisible()

    const posture = page.locator('#posture')
    await expect(posture).toHaveValue('BALANCED')
    await posture.selectOption('DEFENSIVE')
    await expect(page.getByText(/concede bem menos/)).toBeVisible()

    const slots = page.locator('div.grid select')
    for (let i = 0; i < 11; i++) {
      await slots.nth(i).selectOption({ index: 1 })
    }
    await page.getByRole('button', { name: 'Salvar escalação' }).click()
    await expect(page.getByText('Escalação salva ✓')).toBeVisible({ timeout: 10_000 })

    // Sai e volta: a postura salva é recarregada do servidor.
    await page.getByRole('link', { name: 'Clube' }).click()
    await expect(page).toHaveURL(/\/dashboard$/)
    await page.getByRole('link', { name: 'Escalação' }).click()
    await expect(page.locator('#posture')).toHaveValue('DEFENSIVE', { timeout: 10_000 })

    // E chega à engine: o placar da partida do usuário mostra a postura de
    // cada lado (o adversário é a IA, sempre EQUILIBRADA). A navegação é
    // client-side de propósito — um `goto` reinicia o estado in-memory do MSW
    // e a postura salva se perderia.
    await page.getByRole('link', { name: 'Rodada' }).click()
    await expect(page).toHaveURL(/\/round$/)
    await page.locator('a[href^="/round/match/"]').filter({ hasText: 'SEU JOGO' }).first().click()
    await expect(page).toHaveURL(/\/round\/match\/\d+$/)
    await expect(page.getByText('Defensiva')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText('Equilibrada')).toBeVisible()
  })
})
