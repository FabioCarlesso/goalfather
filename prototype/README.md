# Protótipo web — GoalFather

Protótipo React **jogável** que serve como especificação executável das regras de jogo e referência de UX para o backend Kotlin.

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

Ao implementar o backend (ver `../docs/ARQUITETURA.md`), replique as regras encontradas aqui no domínio Kotlin:

| Protótipo (JS) | Backend (Kotlin) |
|---|---|
| `makePlayer(...)` | `data class Player` |
| `FORMATIONS` | `enum class Formation` |
| `simulateMatch(...)` | `MatchSimulator.simulate(): Flow<MatchEvent>` |
| eventos `{type: "goal"...}` | `sealed interface MatchEvent` |
| `calcTeamStrength(...)` | `fun Lineup.teamStrength()` |
