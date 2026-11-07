import type { Page } from '@playwright/test'
import { expect, test } from './helpers'

// Escalação (issue #12): preencher os 11 titulares, salvar e validar que a
// escalação persiste no servidor. Roda contra MSW e contra o backend real.
//
// A persistência é checada por navegação client-side (sair de /lineup e voltar),
// e não por reload físico: um F5 reinicia o estado in-memory do MSW, enquanto a
// navegação SPA força um refetch do clube — validando o round-trip no servidor
// em ambos os modos.

// Botões de slot do campinho (FormationGrid). `data-slot` guarda o índice no
// array da escalação — a ordem visual vai do ataque ao gol.
const slotButtons = (page: Page) => page.locator('button[data-slot]')

const slotIds = (page: Page) =>
  slotButtons(page).evaluateAll((els) =>
    els
      .map((el) => el as HTMLButtonElement)
      .sort((a, b) => Number(a.dataset.slot) - Number(b.dataset.slot))
      .map((b) => b.dataset.playerId ?? ''),
  )

const reserves = (page: Page) => page.getByRole('region', { name: /Reservas/ })

test.describe('Lineup flow', () => {
  // Campinho (issue #110): o elenco-semente tem 11 jogadores, então o 4-4-2
  // automático já é completo. Trocar para 4-3-3 tira um MC (vai para a reserva)
  // e abre o 3º AT — montar a partir daí exercita slot → reserva de verdade.
  test('salvar a escalação e mantê-la após navegar', async ({ page }) => {
    await page.goto('/lineup')
    await expect(page).toHaveTitle(/Escalação · GoalFather/)
    await expect(page.getByRole('heading', { name: 'Escalação' })).toBeVisible()

    await expect(slotButtons(page)).toHaveCount(11)
    await page.locator('#formation').selectOption('4-3-3')
    await expect(page.getByText('10/11 titulares')).toBeVisible()

    await page.getByRole('button', { name: 'Atacante 3: vazio' }).click()
    await reserves(page).getByRole('button', { name: /^Carlos Almeida/ }).click()
    await expect(page.getByText('11/11 titulares')).toBeVisible()

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
    await expect(page.locator('#formation')).toHaveValue('4-3-3')
    await expect(page.getByRole('button', { name: /^Atacante 3: Carlos Almeida.*fora de posição$/ })).toBeVisible()
  })

  test('montar a escalação só com teclado', async ({ page }) => {
    await page.goto('/lineup')
    await expect(page.getByText('11/11 titulares')).toBeVisible()

    // Formação pelo teclado: o select nativo troca de valor com as setas.
    await page.locator('#formation').focus()
    await page.keyboard.press('ArrowDown')
    await expect(page.locator('#formation')).toHaveValue('4-3-3')
    await expect(page.getByText('10/11 titulares')).toBeVisible()

    // Tab a partir do select de postura entra no campo pelo ataque: AT 1, AT 2, AT 3.
    await page.locator('#posture').focus()
    const empty = page.getByRole('button', { name: 'Atacante 3: vazio' })
    for (let i = 0; i < 10 && !(await empty.evaluate((el) => el === document.activeElement)); i++) {
      await page.keyboard.press('Tab')
    }
    await expect(empty).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(empty).toHaveAttribute('aria-pressed', 'true')

    const benched = reserves(page).getByRole('button', { name: /^Carlos Almeida/ })
    await benched.focus()
    await page.keyboard.press('Space')
    await expect(page.getByText('11/11 titulares')).toBeVisible()

    // Troca dois titulares de lugar, ainda pelo teclado.
    await page.getByRole('button', { name: /^Goleiro 1:/ }).focus()
    await page.keyboard.press('Enter')
    await page.getByRole('button', { name: /^Zagueiro 1:/ }).focus()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('button', { name: /^Goleiro 1: Rodrigo Alves/ })).toBeVisible()

    const save = page.getByRole('button', { name: 'Salvar escalação' })
    await save.focus()
    await page.keyboard.press('Enter')
    await expect(page.getByText('Escalação salva ✓')).toBeVisible({ timeout: 10_000 })
  })

  test('em 375px o campinho cabe sem rolagem horizontal', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await page.goto('/lineup')
    await expect(page.getByText('11/11 titulares')).toBeVisible()

    // 5-3-2 é a linha mais cheia (5 zagueiros lado a lado).
    for (const formation of ['4-4-2', '5-3-2'] as const) {
      await page.locator('#formation').selectOption(formation)
      await expect(slotButtons(page)).toHaveCount(11)
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      )
      expect(overflow, `rolagem horizontal em ${formation}`).toBeLessThanOrEqual(0)
    }
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

    // O 4-4-2 automático já é completo: basta salvar.
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

      const before = await slotIds(page)

      await page.locator('#formation').selectOption('4-3-3')

      await expect(page.getByText('10/11 titulares')).toBeVisible()
      await expect(page.getByText(UNSAVED)).toBeVisible()
      const after = await slotIds(page)
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

      await page.getByRole('button', { name: /Menu do usuário/ }).click()
      await page.getByRole('button', { name: 'Sair', exact: true }).click()

      await expect(page).toHaveURL(/\/login$/)
      await expect(page.getByRole('dialog')).toBeHidden()
      await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeVisible()
    })
  })
})
