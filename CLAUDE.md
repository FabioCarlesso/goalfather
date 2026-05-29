# CLAUDE.md — Instruções para o Claude Code

Este arquivo orienta o Claude Code ao trabalhar no **GoalFather (GF)**.

## Contexto do projeto

GoalFather é um manager de futebol estilo Elifoot. Backend em **Kotlin + Spring Boot**, frontend em **React + TypeScript + Vite**. É um **projeto de estudo de Kotlin** — o dono tem 15+ anos de Java/Spring Boot e quer aprender Kotlin idiomático. Portanto:

- **Prefira sempre a solução Kotlin idiomática à tradução direta de Java.** Ex.: `sealed interface` + `when` em vez de hierarquias com `instanceof`; `data class` + `copy()` em vez de setters; coroutines/`Flow` em vez de `CompletableFuture`/`@Async`; extension functions em vez de classes utilitárias estáticas.
- **Ao introduzir um recurso Kotlin não óbvio, explique brevemente o porquê** em comentário ou na resposta. O objetivo é aprendizado, não só código funcionando.
- **Frontend é mock-first com OpenAPI como contrato.** Ver [`docs/FRONTEND.md`](docs/FRONTEND.md) — o frontend deve poder evoluir contra mocks MSW sem depender do backend estar pronto.

## A regra de ouro (arquitetura)

**O módulo `domain` é puro: ZERO dependências de Spring, JPA ou qualquer framework.**

- A regra de dependência aponta sempre para dentro: `adapter` → `application` → `domain`. Nunca o contrário.
- Inversão via *ports*: interfaces declaradas em `application/port`, implementadas em `adapter`.
- **NÃO** anote as `data class` de domínio com `@Entity`. Crie entidades JPA separadas em `adapter/out/persistence` e traduza com mappers.
- A engine de simulação deve ser **testável sem subir contexto Spring** (`@SpringBootTest` é proibido para testar domínio).

## Convenções de código

- **Imutabilidade por padrão:** `val` sobre `var`; mutações produzem novas instâncias via `copy()`.
- **Null-safety:** evite `!!`. Use `?.`, `?:`, `requireNotNull`, ou modele a ausência no tipo.
- **Erros de negócio como valores:** use `sealed`/`Result`, não exceptions para fluxo de controle. Exceptions só para casos verdadeiramente excepcionais.
- **Aleatoriedade injetável:** a engine recebe `Random` por construtor, para testes determinísticos com seed fixa.
- **Pacote base:** `com.carlesso.goalfather`
- **Testes:** JUnit 5 + `kotlin.test` + MockK (não Mockito) + `kotlinx-coroutines-test` (`runTest`).

## Estrutura de pacotes (backend)

```
com.carlesso.goalfather
├── domain        // model, event (sealed), engine, rules — PURO
├── application   // port.in, port.out, service (use cases)
├── adapter       // in.web (controllers/DTO), out.persistence (JPA)
└── config        // Spring beans, security, OpenAPI
```

## Convenções do frontend

Plano completo em [`docs/FRONTEND.md`](docs/FRONTEND.md). Resumo das regras invioláveis:

- **Stack:** Vite + React 18 + TypeScript strict + Tailwind + TanStack Query v5 + MSW + React Router.
- **Contrato:** `contract/openapi.yaml` é a única fonte de verdade dos tipos da API. Tipos TS são gerados via `openapi-typescript`. Mudou o YAML → roda `npm run gen:api` → TS quebra onde precisa de ajuste. Não inventar campos no frontend que não estejam no contrato.
- **Mocks no nível de rede:** MSW intercepta `fetch`. Código da app sempre chama `/api/...` real. `VITE_USE_MOCKS=false` aponta para o backend. Não há "modo mock" no código de aplicação.
- **Estado de servidor vive em TanStack Query.** Componentes recebem dados por props; só `pages/` e hooks de `src/api/queries/` tocam dados. `useState` é exclusivo para estado local de UI.
- **Sem `any`.** Se TS não conseguir tipar algo, o contrato está incompleto — corrija o YAML.
- **Não duplicar regras de domínio no frontend.** A engine de mock vive apenas em `src/mocks/engine.ts` e será descartada quando o backend ficar pronto. Componentes nunca calculam força de time, probabilidade de gol, etc.
- **`sealed interface` Kotlin ↔ `oneOf` + `discriminator` OpenAPI:** eventos de partida desambiguam com `switch (event.type)` exaustivo no TS.

## Ordem de desenvolvimento

Siga o roadmap em `docs/ARQUITETURA.md` e `docs/FRONTEND.md`. Backend e frontend evoluem em paralelo, sincronizados pelo contrato OpenAPI:

- **Backend Fase 1 antes de tudo:** domínio + engine + testes, sem Spring.
- **Frontend F0 em paralelo:** escrever `contract/openapi.yaml` e fazer setup Vite+TS+MSW. A partir daí, frontend constrói telas contra mocks enquanto backend constrói camadas Kotlin.
- **Critério de "endpoint pronto":** desligar o handler MSW correspondente e o E2E continuar passando contra `localhost:8080`.

## Fonte de verdade das regras

O protótipo em `prototype/goalfather-web.jsx` define as regras de jogo atuais (formações, atributos, cálculo de força, eventos de partida, mercado, tabela). Ao implementar o backend, **trate o protótipo como especificação executável** — replique e refine essas regras no domínio Kotlin.

## Comandos úteis (após scaffold)

```bash
# Backend
cd backend
./gradlew test            # testes (domínio deve passar sem Spring)
./gradlew bootRun         # sobe a aplicação em :8080
./gradlew ktlintCheck     # lint (se configurado)

# Frontend
cd frontend
npm run dev               # Vite + MSW em :5173
npm run gen:api           # regera src/api/generated.d.ts do contract/openapi.yaml
npm run test              # Vitest (unit + integração com MSW)
npm run e2e               # Playwright contra mocks
npm run e2e:real          # Playwright contra backend real (precisa bootRun)
```

## O que NÃO fazer

- Não vazar o modelo de domínio na API (use DTOs).
- Não colocar lógica de negócio em controllers ou em entidades JPA.
- Não usar `@SpringBootTest` para testar a engine ou regras de domínio.
- Não recorrer a padrões Java verbosos quando há um idioma Kotlin equivalente — este é um projeto de aprendizado de Kotlin.
- **Frontend:** não chamar `fetch` direto de componentes, não inventar campos fora do contrato, não duplicar regras de simulação fora de `src/mocks/engine.ts`.
