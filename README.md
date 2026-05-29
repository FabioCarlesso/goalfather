# ⚽ GoalFather (GF)

> *"I'm gonna make him a transfer he can't refuse."*

Manager de futebol estilo Elifoot. Backend em **Kotlin + Spring Boot**, frontend web em React. Projeto pessoal para estudar **Kotlin no backend** partindo de uma base sólida de Java/Spring Boot.

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
├── .gitignore
├── docs/
│   └── ARQUITETURA.md     ← plano de arquitetura completo (LEIA PRIMEIRO)
├── prototype/
│   ├── goalfather-web.jsx ← protótipo React jogável (referência de UX/regras)
│   └── README.md          ← como rodar o protótipo
└── backend/               ← código Kotlin/Spring Boot (a ser criado)
```

---

## 🚀 Começando o desenvolvimento

### 1. Leia a arquitetura
Comece por [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md). Ele define camadas, modelo de domínio, engine, roadmap em 5 fases e a stack.

### 2. Explore o protótipo
O [protótipo web](prototype/) é a **fonte de verdade das regras de jogo e da UX**: formações, atributos de jogador, cálculo de força, eventos de partida, fluxo de mercado e tabela. Use-o como especificação executável ao construir o backend.

### 3. Construa o backend (Fase 1 primeiro)
A regra de ouro: **escreva o domínio puro e seus testes ANTES de qualquer controller Spring.** Isso força a separação de camadas e dá retorno rápido de aprendizado.

```bash
# scaffold inicial (a fazer)
cd backend
./gradlew build
./gradlew test       # a engine deve ser testável SEM subir contexto Spring
./gradlew bootRun
```

---

## 🗺️ Roadmap

| Fase | Entrega | Foco de estudo |
|---|---|---|
| **1** | Domínio puro + engine + testes (sem Spring) | data/sealed classes, enums, null-safety |
| **2** | Use cases + persistência (JPA, Flyway, Caffeine) | coroutines, `Result`/sealed para erros |
| **3** | API REST + WebSocket + integração com React | `Flow` → WebSocket, serialização |
| **4** | DSL de seed + temporadas + suíte de testes ampla | `@DslMarker`, builders |
| **5** | Multiplayer (auth, liga compartilhada, concorrência) | `Mutex`, locks otimistas |

Detalhes de cada fase em [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md#8-roadmap-em-fases).

---

## 🛠️ Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin 2.x (JVM 21) |
| Framework | Spring Boot 3.4.x |
| Build | Gradle (Kotlin DSL) |
| Persistência | Spring Data JPA + PostgreSQL + Flyway |
| Cache | Caffeine |
| Async | Kotlin Coroutines + Flow |
| Serialização | kotlinx.serialization |
| Docs API | springdoc-openapi |
| Testes | JUnit 5, kotlin.test, MockK, kotlinx-coroutines-test |
| Frontend | React |
| Empacotamento | Docker multi-stage |

---

## 📜 Licença

Projeto pessoal de estudo. Sem licença pública por enquanto.

---

*GoalFather · "An offer he can't refuse" para débitos técnicos em Java.* ⚽
