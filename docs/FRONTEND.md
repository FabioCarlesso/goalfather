# 🖥️ GoalFather — Plano do Frontend

> Estratégia **mock-first** com **OpenAPI como contrato** entre frontend e backend.
> Permite construir a UI completa antes do backend Kotlin estar pronto, e trocar mocks por endpoints reais sem alterar o código da aplicação.

---

## 1. Princípios

1. **Contrato antes de código.** O `openapi.yaml` é a fonte de verdade. Tanto os mocks quanto o cliente HTTP são derivados dele. Divergência entre frontend e backend vira erro de compilação, não bug em runtime.
2. **Mocks no nível da rede, não da aplicação.** O código da app sempre faz `fetch('/api/...')`. Quem responde — MSW em dev, Spring em prod — é decisão de ambiente, não de código.
3. **Estado de servidor ≠ estado de UI.** Tudo que vem da API passa por TanStack Query (cache, invalidação, refetch). `useState` é só para estado local de tela.
4. **TypeScript estrito.** O custo de tipos pagos uma vez compensa o ganho ao swap mock → real (tipos gerados detectam quebra de contrato).
5. **O protótipo é referência, não base.** `prototype/goalfather-web.jsx` define regras e UX; o `frontend/` é reescrita limpa, com separação de responsabilidades.

---

## 2. Stack

| Camada | Tecnologia | Por quê |
|---|---|---|
| Build / dev server | **Vite** | Setup rápido, HMR instantâneo, padrão React moderno |
| Linguagem | **TypeScript (strict)** | Tipos gerados do OpenAPI dão segurança no swap mock→real |
| UI | **React 18** | Familiar (já é o do protótipo) |
| Estilo | **Tailwind CSS** | O protótipo já usa classes utilitárias — migração natural |
| Estado de servidor | **TanStack Query v5** | Cache, refetch, mutations, invalidação — padrão de facto |
| Estado de UI | `useState` / `useReducer` | Sem Redux/Zustand até precisar de verdade |
| Cliente HTTP | **`fetch` + wrapper tipado** | Sem axios; tipos vêm do OpenAPI |
| Roteamento | **React Router v6** | Telas: dashboard, escalação, partida, mercado, tabela |
| Mocks | **MSW (Mock Service Worker)** | Intercepta no Service Worker → mesmo `fetch` da app |
| Contrato | **OpenAPI 3.1** em `contract/openapi.yaml` | Compartilhado com backend (springdoc) |
| Tipos gerados | **`openapi-typescript`** | Gera `.d.ts` do `openapi.yaml` |
| WebSocket | **`native WebSocket` + mock server local** | Stream de `MatchEvent` ao vivo |
| Testes | **Vitest** + **React Testing Library** + **Playwright** (E2E) | Vitest = Jest compatível, Playwright = fluxo de partida |
| Lint / format | **ESLint** + **Prettier** | Padrão |

> **Decisões deliberadas:**
> - **Sem Next.js / SSR.** GoalFather é SPA single-player; SSR só agrega complexidade.
> - **Sem Redux.** TanStack Query cobre 95% do estado; o resto é local.
> - **Sem axios.** Wrapper sobre `fetch` é ~30 linhas e usa tipos gerados.

---

## 3. Estrutura de pastas

```
goalfather/
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── tailwind.config.js
│   ├── index.html
│   ├── public/
│   │   └── mockServiceWorker.js     # gerado pelo MSW
│   └── src/
│       ├── main.tsx                  # entrypoint (monta MSW em dev)
│       ├── App.tsx                   # router
│       ├── api/
│       │   ├── client.ts             # fetch wrapper + tipos gerados
│       │   ├── generated.d.ts        # gerado por openapi-typescript
│       │   └── queries/              # hooks TanStack Query por recurso
│       │       ├── useClub.ts
│       │       ├── useMatch.ts
│       │       ├── useMarket.ts
│       │       └── useStandings.ts
│       ├── mocks/
│       │   ├── browser.ts            # setupWorker do MSW
│       │   ├── handlers.ts           # handlers HTTP
│       │   ├── ws.ts                 # mock de WebSocket para partida ao vivo
│       │   ├── seed.ts               # dados iniciais (espelha protótipo)
│       │   └── engine.ts             # simulação local de partida (porta JS da engine Kotlin)
│       ├── pages/
│       │   ├── DashboardPage.tsx
│       │   ├── LineupPage.tsx
│       │   ├── MatchPage.tsx         # consome WS
│       │   ├── MarketPage.tsx
│       │   └── StandingsPage.tsx
│       ├── components/               # apresentação pura, sem fetch
│       │   ├── PlayerCard.tsx
│       │   ├── FormationGrid.tsx
│       │   ├── MatchEventFeed.tsx
│       │   └── ...
│       ├── domain/                   # tipos e helpers cross-tela
│       │   ├── types.ts              # re-exporta tipos do OpenAPI
│       │   └── formatters.ts
│       └── styles/
│           └── index.css
└── contract/
    └── openapi.yaml                  # fonte de verdade (compartilhada com backend)
```

---

## 4. O contrato OpenAPI

`contract/openapi.yaml` define endpoints, schemas e tipos de evento. **Mesmo arquivo** é consumido pelo frontend (gera tipos TS) e pelo backend (springdoc valida que a implementação bate com o spec).

Endpoints da Fase 3 (ver [`ARQUITETURA.md §7`](ARQUITETURA.md#7-adapters)) traduzidos para schemas:

```yaml
# contract/openapi.yaml (esboço)
openapi: 3.1.0
info: { title: GoalFather API, version: 0.1.0 }
paths:
  /api/clubs/{id}:
    get:
      operationId: getClub
      responses:
        '200': { content: { application/json: { schema: { $ref: '#/components/schemas/Club' } } } }
  /api/clubs/{id}/lineup:
    post:
      operationId: saveLineup
      requestBody: { content: { application/json: { schema: { $ref: '#/components/schemas/LineupRequest' } } } }
      responses: { '204': { description: ok } }
  /api/clubs/{id}/matches:
    post:
      operationId: playMatch
      parameters: [{ name: round, in: query, required: true, schema: { type: integer } }]
      responses:
        '200': { content: { application/json: { schema: { $ref: '#/components/schemas/MatchSummary' } } } }
  /api/market:           { get:  { operationId: listMarket,   responses: { '200': { ... } } } }
  /api/market/buy:       { post: { operationId: buyPlayer,    responses: { '200': { ... } } } }
  /api/market/sell:      { post: { operationId: sellPlayer,   responses: { '200': { ... } } } }
  /api/league/standings: { get:  { operationId: getStandings, responses: { '200': { ... } } } }
components:
  schemas:
    Player: { type: object, required: [id, name, position, overall, ...], properties: { ... } }
    Club:   { type: object, required: [id, name, cash, squad], properties: { ... } }
    MatchEvent:
      oneOf:                                # mapeia o sealed interface do Kotlin
        - $ref: '#/components/schemas/KickOffEvent'
        - $ref: '#/components/schemas/GoalEvent'
        - $ref: '#/components/schemas/CardEvent'
        - $ref: '#/components/schemas/InjuryEvent'
        - $ref: '#/components/schemas/SaveEvent'
        - $ref: '#/components/schemas/FullTimeEvent'
      discriminator: { propertyName: type }
    # ... demais schemas
```

> **Mapeamento `sealed interface` ↔ OpenAPI `oneOf` + `discriminator`:** o Kotlin emite `{"type": "Goal", "minute": 23, ...}` via kotlinx.serialization com `@JsonClassDiscriminator("type")`. O frontend desambigua com `switch (event.type)` exaustivo (TS sabe inferir).

### Geração de tipos

```bash
npx openapi-typescript ../contract/openapi.yaml -o src/api/generated.d.ts
```

Rodar como script `npm run gen:api` e em pre-commit. Mudou o YAML → tipos novos → TS quebra onde quem consome não acompanhou. Esse é o ponto.

---

## 5. Mocks com MSW

`src/mocks/handlers.ts` implementa cada endpoint do contrato. Em dev, `main.tsx` ativa o worker; em produção, ele simplesmente não é registrado.

```ts
// src/mocks/handlers.ts (esboço)
import { http, HttpResponse } from 'msw'
import { state } from './seed'
import { simulateMatch } from './engine'

export const handlers = [
  http.get('/api/clubs/:id', ({ params }) =>
    HttpResponse.json(state.clubs[Number(params.id)])
  ),

  http.post('/api/clubs/:id/lineup', async ({ params, request }) => {
    const body = await request.json() as { players: number[]; formation: string }
    state.clubs[Number(params.id)].lineup = body
    return new HttpResponse(null, { status: 204 })
  }),

  http.post('/api/clubs/:id/matches', ({ params, request }) => {
    const round = Number(new URL(request.url).searchParams.get('round'))
    return HttpResponse.json(simulateMatch(Number(params.id), round))
  }),

  // ... market, standings, etc.
]
```

```ts
// src/main.tsx
async function enableMocks() {
  if (import.meta.env.DEV && import.meta.env.VITE_USE_MOCKS !== 'false') {
    const { worker } = await import('./mocks/browser')
    await worker.start({ onUnhandledRequest: 'bypass' })
  }
}
enableMocks().then(() => ReactDOM.createRoot(...).render(<App />))
```

**Para apontar para o backend real:** `VITE_USE_MOCKS=false npm run dev` (ou variável de ambiente em prod). Zero alteração no código da app.

### Engine de mock

`src/mocks/engine.ts` é uma porta JS *simplificada* da engine Kotlin (mesma matemática de força + RNG). Vive só nos mocks — quando o backend chegar, é descartada. **Não duplicar regras na pasta `src/` da app.**

---

## 6. WebSocket de partida ao vivo

Endpoint real: `ws://host/ws/matches/{id}?token=<jwt>` → stream de `MatchEvent` (JSON por mensagem).

> **Autenticação (issue #27):** o browser não envia `Authorization` no handshake de WebSocket, então o JWT viaja no query param `?token=`. O helper `src/api/wsUrl.ts` anexa o token salvo; o backend valida no `JwtHandshakeInterceptor` e rejeita o handshake (401) sem token válido.

**Mock local:** servidor WS embarcado no Vite via plugin (ex.: `vite-plugin-mock-server` ou um middleware customizado em `vite.config.ts`). Ao receber conexão, dispara `setInterval` que emite eventos respeitando o mesmo formato `{type, minute, ...}` do OpenAPI.

```ts
// src/pages/MatchPage.tsx (esboço)
useEffect(() => {
  const ws = new WebSocket(`${WS_BASE}/ws/matches/${matchId}`)
  ws.onmessage = (e) => {
    const event = JSON.parse(e.data) as MatchEvent     // tipo do OpenAPI
    setEvents((prev) => [...prev, event])
    if (event.type === 'FullTime') ws.close()
  }
  return () => ws.close()
}, [matchId])
```

Quando o backend Kotlin tiver `/ws/matches/{id}` (Spring WebSocket + `Flow` → `WebSocketSession`), basta trocar `WS_BASE`. Formato JSON é o mesmo porque ambos vêm do contrato.

---

## 7. Fluxo de dados (uma tela típica)

```
DashboardPage
   │
   ├── useClub(clubId)              ← hook TanStack Query
   │      │
   │      └── apiClient.getClub(id) ← fetch tipado
   │             │
   │             ├── (dev)  → MSW handler → seed local
   │             └── (prod) → Spring REST controller
   │
   └── render(<ClubSummary club={data} />)
```

Componentes de apresentação (`components/`) **nunca** chamam `fetch` — recebem dados por props. Só `pages/` e hooks de `api/queries/` tocam dados.

---

## 8. Roadmap do frontend (alinhado às fases do backend)

| Fase | Frontend | Backend correspondente |
|---|---|---|
| **F0 — Contrato** | Escrever `contract/openapi.yaml` cobrindo os 9 endpoints. Setup Vite + TS + Tailwind + Query + MSW. | — |
| **F1 — Mocks completos** | Implementar todas as telas (dashboard, escalação, partida, mercado, tabela) contra MSW. Engine de mock em JS. | Em paralelo: Fase 1 do backend (domínio puro + engine) |
| **F2 — Swap por endpoint** | Conforme controllers Kotlin ficam prontos, desligar o handler MSW correspondente. App não percebe. | Fase 2/3 do backend |
| **F3 — WebSocket real** | Trocar mock WS pelo endpoint Spring. | Fase 3 do backend |
| **F4 — Polimento** | Animações de partida, persistência local (localStorage para preferências de UI), PWA opcional. | Fase 4 do backend |
| **F5 — Multiplayer** | Login, seleção de clube, lobby de liga. | Fase 5 do backend |

Critério de "swap pronto" de um endpoint: o teste E2E (Playwright) da tela passa com `VITE_USE_MOCKS=false` apontando para `localhost:8080`.

---

## 9. Testes

| Tipo | Ferramenta | Localização | Comando |
|---|---|---|---|
| Unidade | Vitest | `src/**/*.test.ts` | `npm test` |
| Componente | RTL + Vitest + jsdom | `src/pages/*.test.tsx` | `npm test` |
| Integração HTTP | MSW node (`setupServer`) | reusa `src/mocks/handlers` | `npm test` |
| E2E | Playwright | `e2e/*.spec.ts` | `npm run e2e` |

**Setup compartilhado:**
- `src/test/setup.ts` — wire de `@testing-library/jest-dom/vitest` + ciclo de vida do `setupServer` MSW.
- `src/test/render.tsx` — `renderWithProviders` envolvendo componente em `QueryClientProvider` + `MemoryRouter`.
- `playwright.config.ts` — `webServer` sobe `npm run dev` automaticamente; `baseURL = http://localhost:5173`.

**Suíte E2E em dois modos** (critério "endpoint pronto" do roadmap):
- `npm run e2e` → MSW ligado. Rápido, determinístico, roda no CI sem backend.
- `npm run e2e:real` → `VITE_USE_MOCKS=false`. Exige backend em `localhost:8080`. Mesmas specs.

**Decisão registrada:** WS handlers do MSW são no-op em `setupServer` (`msw/node`). Para validar o stream de partida ao vivo, usa-se Playwright (browser real) com MSW interceptando o `new WebSocket()`.

**Outras decisões:**
- TS strict permanece habilitado nos arquivos de teste — `tsconfig.app.json` os exclui só para evitar contagem no `build`.
- Sem libs extra de assertions (jest-dom já dá `toBeInTheDocument`, `toBeEnabled`, etc.).
- E2E rastreia com `trace: 'retain-on-failure'` + screenshot. Reports em `playwright-report/` (gitignored).

---

## 10. Primeiros passos práticos

1. Criar `contract/openapi.yaml` esboçando os 9 endpoints + schemas (sem precisar implementar nada).
2. `npm create vite@latest frontend -- --template react-ts`
3. Instalar: `@tanstack/react-query`, `msw`, `openapi-typescript`, `tailwindcss`, `react-router-dom`.
4. Configurar Tailwind + ESLint + Prettier + tsconfig strict.
5. Gerar `src/api/generated.d.ts` a partir do contrato.
6. Escrever `client.ts` (wrapper sobre `fetch` que usa os operationIds tipados).
7. Implementar handlers MSW para 2 endpoints simples (`getClub`, `getStandings`) e renderizar dashboard básico.
8. A partir daí, iterar tela por tela.

---

## 11. O que NÃO fazer

- Não inventar campos no frontend que não existem no `openapi.yaml`. Se faltar, atualize o contrato primeiro.
- Não chamar `fetch` direto de componentes. Sempre via hooks em `api/queries/`.
- Não duplicar a engine de simulação em `src/` — mocks vivem em `src/mocks/`, e serão descartados.
- Não usar `any`. Se TS não souber, é porque o contrato está incompleto.
- Não criar wrapper "anti-corrupção" sobre os tipos gerados. Use-os direto — esse é o ponto.

---

*Documento de planejamento do frontend — GoalFather · v1.0*
