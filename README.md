# ⚽ GoalFather (GF)

[![CI](https://github.com/FabioCarlesso/goalfather/actions/workflows/ci.yml/badge.svg)](https://github.com/FabioCarlesso/goalfather/actions/workflows/ci.yml)

> *"I'm gonna make him a transfer he can't refuse."*

Manager de futebol estilo Elifoot. Backend em **Kotlin + Spring Boot**, frontend em **React + TypeScript + Vite** (mock-first com MSW). Projeto pessoal para estudar **Kotlin no backend** partindo de uma base sólida de Java/Spring Boot.

---

## 🎯 O que é

GoalFather é um jogo de gerenciamento de futebol onde você acumula os papéis de técnico e presidente de um clube: escala o time, define táticas, compra e vende jogadores, administra finanças e estádio, e disputa um campeonato simulado rodada a rodada.

- **Agora:** single-player (1 técnico vs. IA, como o Elifoot clássico)
- **Depois:** multiplayer (vários técnicos na mesma liga) — arquitetura já preparada

## 🧠 Objetivo de aprendizado

Este é um projeto de estudo. As prioridades, em ordem:

1. **Idiomas Kotlin** — coroutines, `Flow`, `sealed`, DSLs, null-safety, data classes
2. **Arquitetura limpa / DDD** — domínio puro isolado de framework
3. **Engine de simulação robusta** — função pura, testável e determinística
4. **DevOps** — Docker multi-stage, CI/CD

> Veja o mapeamento completo "conceito do jogo → recurso Kotlin" em [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md#2-por-que-kotlin-brilha-neste-domínio).

---

## 📂 Estrutura do repositório

```
goalfather/
├── README.md              ← você está aqui
├── CLAUDE.md              ← instruções para o Claude Code
├── LICENSE                ← MIT
├── .gitignore
├── docs/
│   ├── ARQUITETURA.md     ← plano de arquitetura do backend (LEIA PRIMEIRO)
│   └── FRONTEND.md        ← plano do frontend (mock-first com OpenAPI)
├── contract/              ← contrato compartilhado FE ↔ BE (a ser criado)
│   └── openapi.yaml       ← fonte de verdade dos endpoints e schemas
├── prototype/
│   ├── goalfather-web.jsx ← protótipo React jogável (referência de UX/regras)
│   └── README.md          ← como rodar o protótipo
├── backend/               ← código Kotlin/Spring Boot (a ser criado)
└── frontend/              ← Vite + React + TS + MSW (a ser criado)
```

---

## 🚀 Começando o desenvolvimento

### 1. Leia a arquitetura
Comece por [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) (backend) e [`docs/FRONTEND.md`](docs/FRONTEND.md) (frontend). Juntos definem camadas, domínio, engine, contrato OpenAPI, estratégia mock-first e o roadmap.

### 2. Explore o protótipo
O [protótipo web](prototype/) é a **fonte de verdade das regras de jogo e da UX**: formações, atributos de jogador, cálculo de força, eventos de partida, fluxo de mercado e tabela. Use-o como especificação executável — tanto o backend Kotlin quanto o frontend novo se inspiram dele, mas nenhum dos dois é uma "promoção" do protótipo.

### 3. Construa em paralelo, ligados pelo contrato

**Backend — regra de ouro:** escreva o domínio puro e seus testes ANTES de qualquer controller Spring.
**Frontend — regra de ouro:** o `contract/openapi.yaml` é a fonte de verdade; tudo no `src/` deriva dele (tipos gerados, mocks MSW, cliente HTTP).

```bash
# Backend (a fazer)
cd backend
./gradlew test       # a engine deve ser testável SEM subir contexto Spring
./gradlew bootRun    # API em :8080 (H2 file-based: estado persiste entre reinícios)
rm -rf backend/data/ # reseta o banco de desenvolvimento (recriado no próximo bootRun)

# Frontend (a fazer)
cd frontend
npm run dev          # Vite + MSW em :5173 (sem precisar do backend)
npm run gen:api      # regera tipos TS a partir do contrato
```

Critério de "endpoint pronto": desliga o handler MSW correspondente e o E2E continua passando contra `localhost:8080`.

### 4. Autenticação (Fase 5)

A partir da Fase 5 a API exige **JWT** em `/api/**` (exceto `/api/auth/register` e `/api/auth/login`). O fluxo do usuário é: **cadastrar/entrar → escolher um clube sem dono (`/select-club`) → dashboard**.

Variáveis de ambiente do backend:

| Variável | Default (dev) | Descrição |
|---|---|---|
| `JWT_SECRET` | segredo de dev embutido | Chave HMAC-SHA256 (≥ 32 bytes). **Defina em produção.** |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | Validade do token em milissegundos. |
| `AUTH_LOGIN_MAX_ATTEMPTS` | `5` | Tentativas inválidas de login por `IP + username` antes do `429`. |
| `AUTH_LOGIN_WINDOW` | `1m` | Janela do limite de login (ex.: `30s`, `1m`, `5m`). |
| `AUTH_REGISTER_MAX_ATTEMPTS` | `20` | Cadastros por `IP` antes do `429` (folgado por causa de NAT/CGNAT). |
| `AUTH_REGISTER_WINDOW` | `1h` | Janela do limite de cadastro. |

```bash
# Gere um segredo forte para produção:
export JWT_SECRET="$(openssl rand -base64 48)"
```

> **Fail-fast (issue #30):** o backend **aborta o boot** se o `JWT_SECRET` ainda
> for o default de desenvolvimento e o profile não for dev/test (ex.: `prod`).
> Como o default está versionado no repositório, subir com ele permitiria forjar
> tokens. O `bootRun` local (sem profile) continua usando o default sem fricção.

> **Rate limiting (issue #43):** `POST /api/auth/login` e `POST /api/auth/register`
> têm proteção contra brute force. Excedido o limite na janela, respondem `429
> Too Many Requests` com header `Retry-After`. Login conta por `IP + username`
> (só tentativas inválidas; login OK zera o contador); register conta por `IP`.
> Premissa: **instância única** (contador em memória via Caffeine). O IP vem do
> header `X-Real-IP`, que o nginx seta com `$remote_addr` (não forjável pelo
> cliente); sem proxy, usa o IP do socket.

No frontend (MSW) o fluxo funciona sem backend: o mock implementa register/login/me/available/claim e persiste a sessão no `localStorage`.

---

## 🗺️ Roadmap

| Fase | Entrega | Foco de estudo |
|---|---|---|
| **1** | Domínio puro + engine + testes (sem Spring) · Frontend setup + contrato OpenAPI | data/sealed classes, enums, null-safety |
| **2** | Use cases + persistência (JPA, Flyway, Caffeine) · Telas contra mocks MSW | coroutines, `Result`/sealed para erros |
| **3** | API REST + WebSocket · Swap incremental mock → endpoint real | `Flow` → WebSocket, serialização, OpenAPI bidirecional |
| **4** | DSL de seed + temporadas + suíte de testes ampla · Polimento de UI | `@DslMarker`, builders |
| **5** | Multiplayer (auth, liga compartilhada, concorrência) · Lobby/seleção de clube | `Mutex`, locks otimistas |

Detalhes de cada fase em [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md#8-roadmap-em-fases).

---

## 🛠️ Stack

**Backend**

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin 2.x (JVM 21) |
| Framework | Spring Boot 3.4.x |
| Build | Gradle (Kotlin DSL) |
| Persistência | Spring Data JPA + PostgreSQL + Flyway |
| Cache | Caffeine |
| Segurança | Spring Security + JWT (jjwt) |
| Async | Kotlin Coroutines + Flow |
| Serialização | kotlinx.serialization |
| Docs API | springdoc-openapi (valida contrato) |
| Testes | JUnit 5, kotlin.test, MockK, kotlinx-coroutines-test |

**Frontend**

| Camada | Tecnologia |
|---|---|
| Build / dev server | Vite |
| Linguagem | TypeScript (strict) |
| UI | React 18 + Tailwind CSS |
| Roteamento | React Router v6 |
| Estado de servidor | TanStack Query v5 |
| Cliente HTTP | `fetch` + tipos gerados do OpenAPI |
| Mocks | MSW (Mock Service Worker) |
| Tipos gerados | `openapi-typescript` |
| Testes | Vitest + React Testing Library + Playwright (E2E) |

**Compartilhado**

| Item | Tecnologia |
|---|---|
| Contrato | OpenAPI 3.1 (`contract/openapi.yaml`) |
| Empacotamento | Docker multi-stage |

---

## 📜 Licença

MIT — ver [`LICENSE`](LICENSE).

---

*GoalFather · "An offer he can't refuse" para débitos técnicos em Java.* ⚽
