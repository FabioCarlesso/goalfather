# Protótipo web — GoalFather

Protótipo React **jogável** que serve como especificação executável das regras de jogo e referência de UX.

> ⚠️ **Não é a base do frontend de produção.** O frontend real vive em `../frontend/` (Vite + TS + MSW, mock-first contra `../contract/openapi.yaml`). Este protótipo é referência viva: a UX e as regras saem daqui, o código não. Ver [`../docs/FRONTEND.md`](../docs/FRONTEND.md) para a estratégia completa.

## O que ele define

- **Formações:** 4-4-2, 4-3-3, 3-5-2, 5-3-2
- **Atributos de jogador:** overall, pace, shooting, passing, defending, stamina, salário, idade, gols
- **Cálculo de força:** média de overall da escalação (`teamStrength`)
- **Engine de partida:** eventos minuto a minuto (gol, defesa, cartão), resultado probabilístico baseado em força + aleatoriedade
- **Mercado:** compra/venda com preço e salário
- **Finanças:** caixa, bilheteria, folha salarial, ampliação de estádio
- **Tabela:** classificação de 20 times atualizada por rodada
- **Temporadas:** reset e progressão

## Como rodar

O arquivo `goalfather-web.jsx` é um componente React único, sem dependências externas além de React. Para rodar:

1. Crie um projeto Vite + React: `npm create vite@latest goalfather-proto -- --template react`
2. Substitua `src/App.jsx` pelo conteúdo de `goalfather-web.jsx` (ajuste o export se necessário)
3. `npm install && npm run dev`

> O protótipo usa apenas `useState`/`useEffect`/`useRef` — sem libs de estado ou roteamento. Estado é mantido em memória (não há persistência).

## Uso como especificação

**Backend** (ver `../docs/ARQUITETURA.md`) — replique as regras no domínio Kotlin:

| Protótipo (JS) | Backend (Kotlin) |
|---|---|
| `makePlayer(...)` | `data class Player` |
| `FORMATIONS` | `enum class Formation` |
| `simulateMatch(...)` | `MatchSimulator.simulate(): Flow<MatchEvent>` |
| eventos `{type: "goal"...}` | `sealed interface MatchEvent` |
| `calcTeamStrength(...)` | `fun Lineup.teamStrength()` |

**Frontend** (ver `../docs/FRONTEND.md`) — extraia UX e formato dos eventos, mas reescreva com separação clara:

| Protótipo (tudo num `.jsx`) | Frontend real (`../frontend/src/`) |
|---|---|
| `useState` com tudo dentro | TanStack Query + props |
| `simulateMatch` inline | `src/mocks/engine.ts` (descartado quando backend ficar pronto) |
| Componente único monolítico | `pages/` + `components/` separados |
| Sem tipos | TypeScript strict + tipos gerados do OpenAPI |
