import { test as base, expect, type Page } from '@playwright/test'

// Helpers de auth para o E2E (issues #18/#19). O app agora exige login e
// seleção de clube antes de qualquer tela — cada teste de feature passa por
// aqui primeiro. O estado de auth do mock é persistido no localStorage
// (ver src/mocks/seed.ts), então sobrevive aos `page.goto` de deep-link.

let seq = 0

// Modo `npm run e2e:real` (issue #101): os specs foram escritos para o MSW, onde
// cada teste começa com estado isolado. Contra o backend real todos dividem uma
// liga só, então cada teste começa resetando-a. O reset só existe no profile
// `e2e` do backend (`SPRING_PROFILES_ACTIVE=dev,e2e ./gradlew bootRun`).
const REAL_BACKEND = process.env.VITE_USE_MOCKS === 'false'
const BACKEND_URL = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080'

async function resetBackend(page: Page): Promise<void> {
  // Direto no backend, sem passar pelo proxy do Vite (que só cobre /api e /ws).
  const res = await page.request.post(`${BACKEND_URL}/test-support/reset`)
  if (res.status() === 404) {
    throw new Error(
      `POST ${BACKEND_URL}/test-support/reset → 404: o backend não está no profile e2e ` +
        '(suba com SPRING_PROFILES_ACTIVE=dev,e2e ./gradlew bootRun).',
    )
  }
  expect(res.status(), 'reset do backend e2e').toBe(204)
}

/** Texto literal dentro de uma RegExp (nomes de clube e jogador vêm da tela). */
export const escapeRegExp = (text: string): string => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/** Cadastra um usuário único pela UI e para na escolha de clube (onboarding). */
export async function register(page: Page): Promise<void> {
  if (REAL_BACKEND) await resetBackend(page)
  await page.goto('/login')
  await page.getByRole('button', { name: 'Cadastre-se' }).click()
  const username = `e2e_${Date.now()}_${seq++}`
  await page.getByLabel('Usuário').fill(username)
  await page.getByLabel('Senha').fill('secret123')
  await page.getByRole('button', { name: 'Cadastrar' }).click()
  await expect(page.getByRole('heading', { name: 'Escolha seu clube' })).toBeVisible()
}

/** Liga em que o técnico da UI entra por padrão — onde `registerRival` também procura clube. */
export const DEFAULT_COUNTRY = 'Brasil'
export const DEFAULT_LEAGUE = 'Campeonato Brasileiro Série A'
export const DEFAULT_LEAGUE_ID = 'bra-serie-a'

/**
 * Onboarding (issue #131): país → liga → o PRIMEIRO clube livre da liga (o
 * mais forte, pela ordem do servidor). Devolve o nome do clube escolhido —
 * lido da tela, porque mock e backend real não têm os mesmos dados.
 */
export async function chooseClub(
  page: Page,
  { country = DEFAULT_COUNTRY, league = DEFAULT_LEAGUE }: { country?: string; league?: string } = {},
): Promise<string> {
  await page.getByRole('list', { name: 'Países' }).getByRole('button', { name: new RegExp(escapeRegExp(country)) }).click()
  await page.getByRole('list', { name: 'Ligas' }).getByRole('button', { name: new RegExp(escapeRegExp(league)) }).click()
  const card = page.getByRole('list', { name: /Clubes livres/ }).getByRole('article').first()
  const clubName = (await card.getAttribute('aria-label')) ?? ''
  expect(clubName, 'nome do clube no card').not.toBe('')
  await card.getByRole('button', { name: 'Treinar este clube' }).click()
  await expect(page).toHaveURL(/\/dashboard\/visao-geral$/)
  return clubName
}

/**
 * Cadastra um usuário único e escolhe o primeiro clube livre da Série A.
 * No modo real, reseta o backend antes — por isso fica aqui, e não na fixture:
 * `welcome.spec` usa o `test` base e chama `authenticate` direto.
 */
export async function authenticate(page: Page): Promise<string> {
  await register(page)
  return chooseClub(page)
}

export interface Rival {
  token: string
  clubId: number
  clubName: string
}

/**
 * Cadastra um segundo técnico e reivindica o primeiro clube livre da liga
 * (default: a do técnico da UI, para o rival aparecer na tabela dele). `null`
 * se não houver clube.
 */
export async function registerRival(page: Page, competitionId = DEFAULT_LEAGUE_ID): Promise<Rival | null> {
  return page.evaluate(async ({ username, competitionId }) => {
    const json = { 'Content-Type': 'application/json' }
    const reg = await fetch('/api/auth/register', {
      method: 'POST',
      headers: json,
      body: JSON.stringify({ username, password: 'secret123' }),
    })
    const { token } = (await reg.json()) as { token: string }
    const auth = { Authorization: `Bearer ${token}` }
    const available = (await (await fetch(`/api/clubs/available?competitionId=${competitionId}`, { headers: auth })).json()) as
      { id: number; name: string }[]
    const club = available[0]
    if (!club) return null
    await fetch(`/api/clubs/${club.id}/claim`, { method: 'POST', headers: auth })
    return { token, clubId: club.id, clubName: club.name }
  }, { username: `rival_${Date.now()}_${Math.floor(Math.random() * 1e6)}`, competitionId })
}

/**
 * Joga a rodada a partir da /round já aberta: pronto → jogar, em ritmo lento
 * para a transmissão ainda estar no ar quando o teste seguir. Desde a issue
 * #151 o drill-down de partida só transmite encaixe LIBERADO — antes do `play`
 * ele seria uma prévia do resultado —, então quem abre `/round/match/:id`
 * passa por aqui. Não navega: `lineup.spec` chega à /round client-side para
 * não reiniciar o estado do MSW.
 */
export async function startRound(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { name: /Rodada \d+/ })).toBeVisible()
  await page.getByLabel('Velocidade').selectOption('SLOW')
  await page.getByRole('button', { name: 'Estou pronto' }).click()
  await page.getByRole('button', { name: 'Jogar rodada' }).click()
  await expect(page.getByText(/Bola rolando/i).first()).toBeVisible({ timeout: 10_000 })
}

/** Clube do técnico logado na UI (lido de /me com o token da própria sessão). */
export async function myClubId(page: Page): Promise<number> {
  return page.evaluate(async () => {
    const res = await fetch('/api/auth/me', { headers: { Authorization: `Bearer ${localStorage.getItem('gf:token')}` } })
    return ((await res.json()) as { clubId: number }).clubId
  })
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
