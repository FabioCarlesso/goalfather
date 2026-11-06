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
    await expect(page).toHaveTitle(/Escalação · GoalFather/)
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

  // Issue #109: experimentar formações e navegar sem perder trabalho por acidente.
  test.describe('alterações não salvas', () => {
    const UNSAVED = 'Alterações não salvas'

    test('trocar de formação preserva os titulares; só o AT novo fica vazio', async ({ page }) => {
      await page.goto('/lineup')
      await expect(page.getByText('11/11 titulares')).toBeVisible()
      await expect(page.getByText(UNSAVED)).toBeHidden()

      const slots = page.locator('div.grid select')
      const before = await slots.evaluateAll((els) => els.map((el) => (el as HTMLSelectElement).value))

      await page.locator('#formation').selectOption('4-3-3')

      await expect(page.getByText('10/11 titulares')).toBeVisible()
      await expect(page.getByText(UNSAVED)).toBeVisible()
      const after = await slots.evaluateAll((els) => els.map((el) => (el as HTMLSelectElement).value))
      expect(after).toEqual([...before.slice(0, 8), before[9], before[10], ''])

      await page.getByRole('button', { name: 'Restaurar escalação automática' }).click()
      await expect(page.locator('#formation')).toHaveValue('4-4-2')
      await expect(page.getByText('11/11 titulares')).toBeVisible()
      await expect(page.getByText(UNSAVED)).toBeHidden()
    })

    test('navegar pelo menu pede confirmação só com alterações', async ({ page }) => {
      await page.goto('/lineup')
      await expect(page.getByText('11/11 titulares')).toBeVisible()
      await page.locator('#posture').selectOption('OFFENSIVE')

      const dialog = page.getByRole('dialog', { name: 'Sair sem salvar a escalação?' })

      await page.getByRole('link', { name: 'Rodada' }).click()
      await expect(dialog).toBeVisible()
      await page.getByRole('button', { name: 'Ficar' }).click()
      await expect(dialog).toBeHidden()
      await expect(page).toHaveURL(/\/lineup$/)
      await expect(page.locator('#posture')).toHaveValue('OFFENSIVE')

      // Esc é o mesmo que "Ficar".
      await page.getByRole('link', { name: 'Rodada' }).click()
      await expect(dialog).toBeVisible()
      await page.keyboard.press('Escape')
      await expect(dialog).toBeHidden()
      await expect(page).toHaveURL(/\/lineup$/)

      await page.getByRole('link', { name: 'Rodada' }).click()
      await page.getByRole('button', { name: 'Sair sem salvar' }).click()
      await expect(page).toHaveURL(/\/round$/)

      // A edição foi descartada: voltar não pede nada, e sair de novo também não.
      await page.getByRole('link', { name: 'Escalação' }).click()
      await expect(page.locator('#posture')).toHaveValue('BALANCED')
      await page.getByRole('link', { name: 'Rodada' }).click()
      await expect(page).toHaveURL(/\/round$/)
      await expect(dialog).toBeHidden()
    })

    test('recarregar com alterações dispara o aviso nativo do navegador', async ({ page }) => {
      await page.goto('/lineup')
      await expect(page.getByText('11/11 titulares')).toBeVisible()
      await page.locator('#posture').selectOption('OFFENSIVE')

      const dialogs: string[] = []
      page.on('dialog', (d) => {
        dialogs.push(d.type())
        void d.accept()
      })
      await page.reload()

      expect(dialogs).toEqual(['beforeunload'])
      await expect(page.getByText('11/11 titulares')).toBeVisible()
    })

    test('o logout com alterações pendentes vai ao login sem travar', async ({ page }) => {
      await page.goto('/lineup')
      await expect(page.getByText('11/11 titulares')).toBeVisible()
      await page.locator('#posture').selectOption('OFFENSIVE')

      await page.getByRole('button', { name: 'Sair', exact: true }).click()

      await expect(page).toHaveURL(/\/login$/)
      await expect(page.getByRole('dialog')).toBeHidden()
      await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeVisible()
    })
  })
})
