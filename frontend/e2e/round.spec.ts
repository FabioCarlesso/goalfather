import { expect, registerRival, test } from './helpers'

// Fluxo critico do FRONTEND.md: abrir /round, jogar a rodada, ver o feed
// da partida do usuario chegar ao FullTime, e validar que a tabela foi
// atualizada com o resultado.
//
// MSW intercepta no browser quando `VITE_USE_MOCKS != 'false'`. Em modo
// `npm run e2e:real`, esta suite roda contra o backend Spring real em
// :8080. Mesmo teste, dois alvos — assim o swap mock -> real eh
// validado por aqui.

test.describe('Round flow', () => {
  test('joga a rodada completa e atualiza a tabela', async ({ page }) => {
    // Critério da issue #112: o resumo cabe sem rolagem em 1280×800.
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto('/round')

    await expect(page.getByRole('heading', { name: /Rodada \d+/ })).toBeVisible()
    await expect(page.getByText('SEU JOGO')).toBeVisible()

    // Liga compartilhada (issue #20): sinalizar "pronto" destrava o jogo.
    const startBtn = page.getByRole('button', { name: 'Jogar rodada' })
    await expect(startBtn).toBeDisabled()
    await page.getByRole('button', { name: 'Estou pronto' }).click()

    await expect(startBtn).toBeEnabled()
    await startBtn.click()

    // Evento KickOff chega quase imediato
    await expect(page.getByText(/Bola rolando/i).first()).toBeVisible({ timeout: 10_000 })

    // Banner de pontuacoes atualizadas (sinal de rodada encerrada) e top-3
    await expect(page.getByText(/Pontuações atualizadas após a rodada/i)).toBeVisible({
      timeout: 30_000,
    })

    // Extrato do treino da semana (issue #58) — o foco aplicado aparece
    // mesmo quando ninguem evoluiu.
    await expect(page.getByRole('heading', { name: /Treino da semana —/ })).toBeVisible()

    // Resumo da rodada (issue #112): resultado, posicao e financas do clube
    // do tecnico inteiros na tela, sem rolar. O botao fica no cabecalho do
    // painel; o rodape (posicao + caixa) e o que tem mais chance de cair.
    const summary = page.getByRole('region', { name: 'Resumo da rodada' })
    await expect(summary.getByRole('heading', { name: 'Sua partida' })).toBeInViewport({ ratio: 1 })
    await expect(summary.getByTestId('my-match-score')).toBeInViewport({ ratio: 1 })
    await expect(summary.getByText(/Você está em \d+º/)).toBeInViewport({ ratio: 1 })
    await expect(summary.getByText(/salários$/)).toBeInViewport({ ratio: 1 })
    await expect(summary.getByRole('button', { name: 'Próxima rodada' })).toBeInViewport({ ratio: 1 })

    // "Ver detalhes da partida" rola ate o feed da partida do usuario.
    await summary.getByRole('button', { name: 'Ver detalhes da partida' }).click()
    await expect(page.getByRole('heading', { name: /Detalhes da sua partida/ })).toBeInViewport()

    // Tabela por divisao (issue #47): as duas divisoes aparecem, com a zona
    // de promocao/rebaixamento indicada na legenda.
    await page.getByRole('link', { name: 'Tabela' }).click()
    await expect(page.getByRole('heading', { name: 'Tabela' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Divisão 1' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Divisão 2' })).toBeVisible()
    await expect(page.getByText(/Rebaixamento/i)).toBeVisible()
    await expect(page.getByText(/Promoção/i)).toBeVisible()
    // Pelo menos uma linha tem pontos > 0 (vencedor de algum jogo)
    await expect(page.locator('tbody tr td:nth-child(8)')
      .filter({ hasText: /^[1-9]\d*$/ })
      .first()).toBeVisible()
  })

  // Issue #115: "Pular para o fim" abre uma conexão INSTANT e a antiga só fecha
  // depois. O extrato do treino só vem no RoundFinished de quem ENCERRA a
  // rodada — vê-lo aqui prova que a conexão nova venceu a corrida (no backend
  // real, a antiga também está viva) e que nada do resumo se perdeu.
  test('pular para o fim leva ao resumo completo da rodada', async ({ page }) => {
    await page.goto('/round')
    await expect(page.getByRole('heading', { name: /Rodada \d+/ })).toBeVisible()
    // Lenta: garante que a rodada ainda está no ar quando o clique chegar.
    await page.getByLabel('Velocidade').selectOption('SLOW')

    await page.getByRole('button', { name: 'Estou pronto' }).click()
    await page.getByRole('button', { name: 'Jogar rodada' }).click()
    await expect(page.getByText(/Bola rolando/i).first()).toBeVisible({ timeout: 10_000 })

    await page.getByRole('button', { name: 'Pular para o fim' }).click()

    // Em INSTANT o fim chega em bem menos que os ~14s da velocidade lenta.
    await expect(page.getByText(/Pontuações atualizadas após a rodada/i)).toBeVisible({ timeout: 5_000 })
    await expect(page.getByRole('heading', { name: /Treino da semana —/ })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Pular para o fim' })).toHaveCount(0)
    // Finanças também só vêm no RoundFinished completo.
    await expect(page.getByText(/bilheteria/)).toBeVisible()
    // Placares aplicados à tabela: o líder pontuou.
    await expect(page.getByRole('listitem').filter({ hasText: /pts/ }).first()).toHaveText(/[1-9]\d* pts/)
  })

  test('lembra a velocidade escolhida entre sessões', async ({ page }) => {
    await page.goto('/round')
    const select = page.getByLabel('Velocidade')
    await expect(select).toHaveValue('NORMAL')
    await select.selectOption('FAST')

    await page.reload()
    await expect(page.getByLabel('Velocidade')).toHaveValue('FAST')
  })

  // Issue #151: o gate de prontidão vale no WS, não só no `POST /round/play`.
  // Com um técnico pendente, abrir `/ws/slot/{key}` direto (sem a UI) não pode
  // jogar o encaixe: chega só o `RoundNotStarted`, e a data corrente segue a
  // mesma, ainda `Scheduled`.
  test('WS de encaixe não liberado não simula nada, mesmo aberto sem passar pelo play', async ({ page }) => {
    await page.goto('/round')
    await expect(page.getByRole('heading', { name: /Rodada \d+/ })).toBeVisible()
    const rival = await registerRival(page)
    expect(rival, 'precisa de um clube livre para o segundo técnico').not.toBeNull()

    // Pronto → play → WS no MESMO evaluate, a milissegundos um do outro: no
    // profile `e2e` o timeout da prontidão é 5s, e passar dele liberaria o
    // encaixe de verdade (escape hatch da #45) — o teste mediria outra coisa.
    const outcome = await page.evaluate(async () => {
      const token = localStorage.getItem('gf:token') ?? ''
      const auth = { Authorization: `Bearer ${token}` }
      const current = async () =>
        (await (await fetch('/api/league/round/current', { headers: auth })).json()) as
          { matchday: { key: string }; status: string }
      const before = await current()
      await fetch('/api/league/round/ready', { method: 'POST', headers: auth })
      const play = await fetch('/api/league/round/play', { method: 'POST', headers: auth })

      const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
      const url = `${protocol}//${location.host}/ws/slot/${before.matchday.key}?token=${token}&speed=INSTANT`
      const { messages, code } = await new Promise<{ messages: string[]; code: number }>((resolve) => {
        const received: string[] = []
        const socket = new WebSocket(url)
        // Sem close em 10s = regressão (ex.: o gate sumiu e o stream segue):
        // falha rápido, com o que chegou até ali, em vez do timeout global.
        const timer = setTimeout(() => resolve({ messages: received, code: -1 }), 10_000)
        socket.onmessage = (e) => received.push(String(e.data))
        socket.onclose = (e) => {
          clearTimeout(timer)
          resolve({ messages: received, code: e.code })
        }
      })
      const after = await current()
      return { playStatus: play.status, messages, code, before, after }
    })

    expect(outcome.playStatus).toBe(409)
    expect(outcome.messages.map((m) => (JSON.parse(m) as { type: string }).type)).toEqual(['RoundNotStarted'])
    expect(outcome.code).toBe(1000)
    expect(outcome.after.matchday.key).toBe(outcome.before.matchday.key)
    expect(outcome.after.status).toBe('Scheduled')
  })
})
