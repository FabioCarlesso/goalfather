# ⚽ GoalFather — Plano de Arquitetura

> Manager de futebol estilo Elifoot. Backend em **Kotlin + Spring Boot**, frontend em **React + TypeScript** (Vite).
> Projeto pessoal de Fabio Carlesso para estudar Kotlin no backend. Sigla: **GF**.

**Stack escolhida:** Spring Boot + Kotlin · Frontend React/TS mock-first · Single-player agora, multiplayer depois
**Prioridades de aprendizado:** (1) Idiomas Kotlin · (2) Arquitetura limpa/DDD · (3) Engine de simulação · (4) DevOps

> **Frontend:** o plano detalhado vive em [`FRONTEND.md`](FRONTEND.md). Estratégia mock-first com OpenAPI como contrato compartilhado — frontend evolui contra MSW enquanto o backend Kotlin é construído.

---

## 1. Visão geral

GoalFather é um jogo de gerenciamento de futebol onde o usuário acumula os papéis de técnico e presidente de um clube: escala o time, define táticas, compra e vende jogadores, administra finanças e estádio, e disputa um campeonato simulado rodada a rodada.

A arquitetura é desenhada para **começar single-player** (1 técnico vs. IA), mas com as fronteiras de domínio já preparadas para multiplayer (várias pessoas na mesma liga) sem reescrita estrutural — apenas adicionando contexto de identidade/sessão e concorrência.

### Princípio norteador

O coração do sistema é o **domínio de simulação**, e ele deve ser independente de framework. Spring Boot, JPA e REST são detalhes de infraestrutura na borda. Essa separação é exatamente o que torna o projeto bom para estudar arquitetura limpa — e o que permite que a engine seja testada sem subir contexto Spring.

---

## 2. Por que Kotlin brilha neste domínio

Um manager de futebol é um caso de uso quase ideal para exercitar os recursos que diferenciam Kotlin de Java. Abaixo, o mapeamento entre conceito de domínio e idioma da linguagem:

| Conceito do jogo | Recurso Kotlin | Por quê |
|---|---|---|
| Atributos imutáveis de jogador | `data class` + `val` | Igualdade estrutural e `copy()` de graça |
| Eventos de partida (gol, cartão, lesão) | `sealed class` / `sealed interface` | `when` exaustivo sem `else`, modelagem fechada |
| Posições, formações, status | `enum class` com propriedades | Comportamento junto do valor |
| Resultado de operações (compra, escalação) | `sealed class Result` ou `kotlin.Result` | Erros como valores, sem exceptions de controle de fluxo |
| Simulação assíncrona de várias partidas | `coroutines` + `Flow` | Concorrência estruturada, streaming de eventos |
| Configuração de squad / liga | type-safe builders (**DSL**) | Construção legível e validada de cenários |
| Ausência de jogador / valor opcional | null-safety (`?`, `?:`, `?.let`) | Elimina classe inteira de NPEs |
| Cálculos de força/atributo | extension functions | `lineup.teamStrength()` lê como linguagem natural |

> **Nota de transição (vindo de Java/Spring):** você vai reconhecer 80% imediatamente. Os 20% que valem estudo são: `sealed` + `when` exaustivo, coroutines vs. `@Async`/`CompletableFuture`, e DSLs com lambdas-with-receiver. Foque tempo de estudo aí.

---

## 3. Arquitetura em camadas (Clean / Hexagonal)

```
┌─────────────────────────────────────────────────────────────┐
│  ADAPTERS (infra de entrada/saída)                            │
│  ├─ web/        REST controllers, DTOs, WebSocket             │
│  ├─ persistence/  JPA entities, repositórios Spring Data      │
│  └─ config/     Spring beans, segurança, OpenAPI              │
├─────────────────────────────────────────────────────────────┤
│  APPLICATION (casos de uso / orquestração)                    │
│  ├─ services/   PlayMatchUseCase, BuyPlayerUseCase, ...        │
│  └─ ports/      interfaces (ClubRepository, MatchEngine)       │
├─────────────────────────────────────────────────────────────┤
│  DOMAIN (núcleo puro — ZERO Spring, ZERO JPA)                 │
│  ├─ model/      Club, Player, Lineup, Formation, League        │
│  ├─ event/      MatchEvent (sealed), TransferEvent             │
│  ├─ engine/     MatchSimulator, StrengthCalculator             │
│  └─ rules/      regras de negócio puras e testáveis            │
└─────────────────────────────────────────────────────────────┘
```

**Regra de dependência:** as setas apontam sempre para dentro. `domain` não conhece `application`; `application` não conhece `adapters`. A inversão acontece via *ports* (interfaces declaradas em `application`, implementadas em `adapters`).

### Estrutura de pacotes sugerida

```
com.carlesso.goalfather
├── domain
│   ├── model         // Club, Player, Lineup, Formation, Stadium, League, Standing
│   ├── event         // sealed MatchEvent, sealed TransferResult
│   ├── engine        // MatchSimulator, StrengthCalculator (puro)
│   └── rules         // TransferRules, LineupRules
├── application
│   ├── port
│   │   ├── in         // PlayMatchUseCase, ManageSquadUseCase (interfaces)
│   │   └── out        // ClubRepository, LeagueRepository (interfaces)
│   └── service        // implementações dos use cases
├── adapter
│   ├── in.web         // controllers, DTO, mappers, WebSocket handlers
│   └── out.persistence // *Entity (JPA), *JpaRepository, *PersistenceAdapter
└── config             // SpringConfig, SecurityConfig, OpenApiConfig
```

---

## 4. Modelo de domínio (Kotlin)

Esboço das principais classes. Tudo imutável por padrão; mutações produzem novas instâncias via `copy()`.

```kotlin
// ---- Value types ----
enum class Position(val abbr: String) { GK("GL"), CB("ZG"), MF("MC"), FW("AT") }

enum class Formation(val slots: List<Position>) {
    F_4_4_2(listOf(GK, CB, CB, CB, CB, MF, MF, MF, MF, FW, FW)),
    F_4_3_3(listOf(GK, CB, CB, CB, CB, MF, MF, MF, FW, FW, FW)),
    F_3_5_2(listOf(GK, CB, CB, CB, MF, MF, MF, MF, MF, FW, FW));
}

@JvmInline
value class PlayerId(val value: Long)          // value class: zero overhead

// ---- Entities ----
data class Player(
    val id: PlayerId,
    val name: String,
    val position: Position,
    val overall: Int,
    val pace: Int, val shooting: Int, val passing: Int, val defending: Int,
    val stamina: Int = 100,
    val salary: Int,
    val age: Int,
    val goals: Int = 0,
    val injured: Boolean = false,
) {
    val isStar: Boolean get() = overall >= 82
}

data class Lineup(val players: List<Player>, val formation: Formation) {
    init { require(players.size <= 11) { "Escalação não pode ter mais que 11 jogadores" } }
    val isComplete: Boolean get() = players.size == 11
}

data class Club(
    val id: Long,
    val name: String,
    val cash: Long,
    val stadiumCapacity: Int,
    val squad: List<Player>,
    val ownerId: UserId? = null,   // null = controlado pela IA (preparado p/ multiplayer)
)
```

### Eventos de partida como `sealed` (o destaque Kotlin)

```kotlin
sealed interface MatchEvent {
    val minute: Int

    data class KickOff(override val minute: Int = 0) : MatchEvent
    data class Goal(override val minute: Int, val scorer: PlayerId, val home: Boolean) : MatchEvent
    data class Card(override val minute: Int, val player: PlayerId, val red: Boolean) : MatchEvent
    data class Injury(override val minute: Int, val player: PlayerId) : MatchEvent
    data class Save(override val minute: Int) : MatchEvent
    data class FullTime(override val minute: Int = 90, val homeGoals: Int, val awayGoals: Int) : MatchEvent
}

// consumo com when exaustivo — sem else, o compilador garante cobertura
fun describe(e: MatchEvent): String = when (e) {
    is MatchEvent.KickOff  -> "⚡ Bola rolando!"
    is MatchEvent.Goal     -> "⚽ GOL no minuto ${e.minute}!"
    is MatchEvent.Card     -> if (e.red) "🟥 Vermelho!" else "🟨 Amarelo"
    is MatchEvent.Injury   -> "🚑 Lesão no minuto ${e.minute}"
    is MatchEvent.Save     -> "🧤 Defesa difícil"
    is MatchEvent.FullTime -> "🏁 Fim: ${e.homeGoals} × ${e.awayGoals}"
}
```

### Resultados de operação sem exceptions

```kotlin
sealed interface TransferResult {
    data class Success(val club: Club, val player: Player) : TransferResult
    data object InsufficientFunds : TransferResult
    data object SquadFull : TransferResult
}
```

---

## 5. Engine de simulação

A engine é uma função pura: recebe duas escalações + uma semente de aleatoriedade e devolve um fluxo de eventos. Nada de I/O, nada de Spring — 100% testável com `runTest`.

```kotlin
class MatchSimulator(private val rng: Random = Random.Default) {

    fun simulate(home: Lineup, away: Lineup): Flow<MatchEvent> = flow {
        emit(MatchEvent.KickOff())
        var hg = 0; var ag = 0
        val homeStr = home.teamStrength()    // extension function
        val awayStr = away.teamStrength()

        for (minute in 1..90) {
            if (rng.nextDouble() < CHANCE_RATE) {
                val event = resolveChance(minute, homeStr, awayStr, home, away)
                if (event is MatchEvent.Goal) { if (event.home) hg++ else ag++ }
                emit(event)
            }
            if (minute == 45) emit(/* meio-tempo */ MatchEvent.Save(45))
            delay(STREAM_DELAY) // p/ streaming em tempo real via WebSocket; remova em testes
        }
        emit(MatchEvent.FullTime(homeGoals = hg, awayGoals = ag))
    }

    companion object {
        const val CHANCE_RATE = 0.12
        val STREAM_DELAY = 280.milliseconds
    }
}

// extension function — lê como domínio
fun Lineup.teamStrength(): Double =
    if (players.isEmpty()) 60.0 else players.sumOf { it.overall } / players.size.toDouble()
```

> **Conceitos exercitados aqui:** `Flow` (cold stream), coroutines (`delay`, `suspend`), extension functions, `companion object`, e injeção de `Random` para testes determinísticos (passe uma seed fixa).

### DSL para montar cenários de teste/seed

```kotlin
// type-safe builder — lambda-with-receiver
val time = club("Meu Time") {
    cash = 800_000
    stadium = 15_000
    player("Renato Silva")   { position = FW; overall = 88; salary = 55_000 }
    player("Felipe Costa")   { position = MF; overall = 80; salary = 28_000 }
    player("Marcos Figueiredo") { position = GK; overall = 78; salary = 25_000 }
}
```

Implementar essa DSL é um dos exercícios Kotlin mais ricos do projeto (escopo via `@DslMarker`, builders mutáveis que produzem objetos imutáveis).

---

## 6. Camada de aplicação (use cases)

Cada caso de uso é uma classe com responsabilidade única, dependendo apenas de *ports* (interfaces).

```kotlin
interface PlayMatchUseCase {
    suspend fun execute(clubId: Long, round: Int): MatchSummary
}

class PlayMatchService(
    private val clubRepo: ClubRepository,       // port out
    private val leagueRepo: LeagueRepository,    // port out
    private val simulator: MatchSimulator,       // domínio
) : PlayMatchUseCase {

    override suspend fun execute(clubId: Long, round: Int): MatchSummary {
        val club = clubRepo.findById(clubId) ?: error("Clube não encontrado")
        val opponent = leagueRepo.opponentFor(clubId, round)
        val events = simulator.simulate(club.startingLineup(), opponent.lineup()).toList()
        // aplica resultado: gols, stamina, finanças, tabela...
        return MatchSummary.from(events)
    }
}
```

---

## 7. Adapters

### Web (REST + WebSocket)

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/clubs/{id}` | Estado do clube |
| `POST` | `/api/clubs/{id}/lineup` | Salvar escalação e formação |
| `POST` | `/api/clubs/{id}/matches?round=N` | Jogar partida (retorna resumo) |
| `GET` | `/ws/matches/{id}` | **WebSocket**: stream de eventos ao vivo |
| `GET` | `/api/market` | Jogadores disponíveis |
| `POST` | `/api/market/buy` | Contratar jogador |
| `POST` | `/api/market/sell` | Vender jogador |
| `POST` | `/api/clubs/{id}/stadium/expand` | Ampliar estádio |
| `GET` | `/api/league/standings` | Tabela de classificação |
| `GET` | `/api/league/round/readiness` | Prontidão da rodada compartilhada (lobby) — issue #20 |
| `POST` | `/api/league/round/ready` | Técnico sinaliza "estou pronto" — issue #20 |
| `POST` | `/api/league/round/play` | Inicia a rodada (409 `ROUND_NOT_READY` se faltar técnico) |

DTOs separados das entidades de domínio (mapeamento explícito) — evita vazar o modelo interno na API e desacopla versionamento do contrato REST.

### Persistência

- **Banco:** PostgreSQL
- **ORM:** Spring Data JPA (entidades `@Entity` distintas das `data class` de domínio; `PersistenceAdapter` faz a tradução)
- **Cache:** Caffeine para tabela de classificação e lista de mercado (você já usou no projeto Cartola — mesma abordagem)
- **Migrations:** Flyway

> Padrão recomendado: **não** anote as `data class` de domínio com `@Entity`. Mantê-las puras custa um mapper a mais, mas preserva a regra de dependência e deixa o domínio testável sem JPA no classpath.

---

## 8. Roadmap em fases

### Fase 1 — Fundação do domínio (sem Spring) ✅
- [x] Setup do projeto Gradle Kotlin DSL (`build.gradle.kts`)
- [x] Modelo de domínio: `Player`, `Club`, `Lineup`, `Formation`, `League`
- [x] `MatchEvent` sealed + `StrengthCalculator`
- [x] `MatchSimulator` com `Random` injetável (passou para parâmetro de chamada)
- [x] Testes unitários com seed fixa (JUnit 5 + `kotlin.test`)
- **Foco de estudo:** data/sealed classes, enums com propriedades, null-safety

### Fase 2 — Casos de uso + persistência ✅
- [x] Ports (in/out) e use cases (`PlayRound`, `BuyPlayer`, `SellPlayer`, `SaveLineup`)
- [x] JPA entities + PersistenceAdapters + Flyway (V1__init.sql)
- [x] Caffeine cache na tabela/mercado — feito na Fase 4 (issue #13)
- **Foco de estudo:** coroutines em use cases, `sealed Result` para erros

### Fase 3 — API REST + frontend (mock-first) ✅
- [x] Definir `contract/openapi.yaml` cobrindo os 9 endpoints (compartilhado com frontend)
- [x] Controllers REST + DTOs de request (responses reusam domínio @Serializable)
- [x] kotlinx.serialization com `@JsonClassDiscriminator("type")` para todos os `sealed` (MatchEvent, RoundEvent, TransferResult, LineupResult)
- [x] WebSocket `/ws/round/{n}` multiplexado para rodada ao vivo (stream de `Flow<RoundEvent>` → cliente)
- [x] Frontend Vite+TS+MSW consumindo o mesmo contrato (ver [`FRONTEND.md`](FRONTEND.md))
- [x] Swap incremental: cada endpoint pronto desliga seu handler MSW; **E2E Playwright `npm run e2e:real` passa contra o backend Spring**
- **Foco de estudo:** `Flow` → WebSocket, serialização (kotlinx.serialization), contrato OpenAPI bidirecional

#### Fase 3.5 / 3.6 — jogabilidade e polimento ✅
- [x] **3.5.1** Geração automática da próxima rodada (Berger) após `RoundFinished` — joga N rodadas em sequência
- [x] **3.5.2** Estatísticas de jogador persistidas entre partidas (gols, cartões, lesão)
- [x] **3.5.3** H2 file-based (`./data/`) — estado sobrevive a reinícios; testes seguem in-memory
- [x] **3.5.4** Bilheteria do mandante + folha salarial aplicadas ao caixa (`RoundFinance` no `RoundFinished`)
- [x] **3.6.1** UI de ampliação de estádio na Dashboard
- [x] **3.6.2** Tratamento global de erros via toast (react-hot-toast)
- [x] **3.6.3** Nome do jogador no feed da partida (lookup do elenco do usuário)
- [x] **3.6.4** Tela de onboarding/primeiro acesso (`/welcome` + flag localStorage)
- [x] **3.6.5** Drill-down de partidas via WebSocket `/ws/matches/{id}`

### Fase 4 — DSL + polimento single-player ✅
- [x] **4.1** DSL de seed de ligas/clubes (`@DslMarker`) — issue #10
- [x] **4.2** Fim de temporada com campeão + virada automática de temporada — issue #11
- [x] **4.3** Cobertura E2E ampliada (mercado, escalação) — issue #12
- [x] **4.4** Caffeine cache em standings/mercado — issue #13
- [ ] Promoção/rebaixamento, mercado dinâmico — *futuro* (depende de divisões)

### Fase 4.5 — DevOps / empacotamento ✅
- [x] **4.5.1** Dockerfile multi-stage do backend — issue #14
- [x] **4.5.2** docker-compose (Postgres + backend + frontend nginx) — issue #15
- [x] **4.5.3** Profile prod PostgreSQL — migrations portáveis + runbook — issue #16
- [x] **4.5.4** GitHub Actions CI (backend + frontend unit + e2e mock) — issue #17

### Fase 5 — Multiplayer (futuro)
- [x] `UserId` / autenticação (Spring Security + JWT) — issues #18/#19
- [x] Liga compartilhada, concorrência de mercado (lock otimista) — issue #21
  (`@Version` em `market_entries`; o `claim` da entrada é o passo decisivo da
  compra, feito ANTES de mexer no clube, então o perdedor da corrida recebe
  `PlayerNotAvailable` sem debitar caixa. Decisão **otimista vs. pessimista**
  abaixo.)
- [x] Sincronização de rodadas entre técnicos humanos — issue #20
  (`round_readiness` + gate "todos prontos → joga"; a simulação não roda 2× sob
  WS concorrentes graças ao claim de rodada da issue #46, abaixo)
- [x] **Escape hatch para técnico ausente** (follow-up #20 → issue #45):
  timeout auto-start. Assim que o PRIMEIRO técnico sinaliza pronto começa a
  correr `app.round-readiness.timeout` (default 2min); ao expirar, o gate
  `start()` passa de "todos prontos" para "todos prontos OU timeout", e a
  rodada joga com os ausentes usando a última escalação salva
  (`club.startingLineup()`, sem "escalar por eles"). Tempo entra via `Clock`
  injetável — testável sem `sleep`. `ReadinessStatus` ganhou
  `secondsRemaining`/`timedOut` para o countdown na UI. (Expulsão permanente /
  bot persistente segue fora de escopo.)
- [x] **Atomicidade multi-instância** (follow-up #20 → issue #46): os `Mutex`
  in-JVM de readiness/finish saíram. `RoundEntity` ganhou `@Version` (migração
  V6) e a `LeagueRepository` expõe duas transições atômicas — `startRound`
  (`Scheduled → InProgress`) e `finishRound` (`→ Finished`, gravando os
  placares). Ambas rodam no bean **não-suspend** `RoundTransition`, e a
  `OptimisticLockingFailureException` do commit perdedor vira `false` no adapter
  (contado na métrica `goalfather.round.claim.conflicts`). `finishRound` é o
  **ponto de serialização** do encerramento: só quem recebe `true` aplica caixa,
  estatísticas, tabela e próxima rodada; o perdedor faz apenas replay dos
  eventos. Vale entre JVMs, então 2+ réplicas contra o mesmo Postgres não dobram
  efeitos. O guard de `finishRound` compara **status E `season`**: a PK de
  `rounds` é só `number`, e a virada de temporada reescreve a rodada 1, então um
  claim atrasado da temporada anterior é rejeitado (senão sobrescreveria a
  temporada nova).
  - *Simulação em dobro (consciente):* cada conexão WS simula a rodada inteira,
    logo 2 nós/2 abas simulam em paralelo. A seed é `Random(matchId)`
    (determinística), então os eventos coincidem e **só os efeitos** são
    serializados pelo claim. Diverge apenas num replay tardio, após lesões
    mudarem `startingLineup()` — cosmético, não corrompe dados.
  - *Leitura suja (janela curta):* enquanto o vencedor grava os efeitos
    (`persistRoundEffects` faz 1 `save` por clube, cada um em sua transação,
    ~dezenas de ms; centenas na virada de temporada), um leitor concorrente vê a
    tabela sem os pontos desta rodada. Uma reconexão posterior já traz a
    definitiva. Fechar de vez exigiria efeitos + claim na MESMA transação — hoje
    inviável com os ports `suspend` por agregado.
  - *Durabilidade — resíduo em aberto:* a ordem é claim (marca `Finished`) →
    efeitos, ou seja *at-most-once* (nunca dobra; podia dobrar antes). O bloco de
    efeitos roda em `withContext(NonCancellable)`, então **fechar a aba do WS**
    logo após o FullTime não rasga a finalização no meio. O que **não** está
    coberto é um **crash de processo** entre o claim e o fim dos efeitos: deixaria
    a rodada `Finished` com efeitos parciais e sem sucessora, e todo stream
    seguinte cairia no replay (a liga travaria). Recuperação (rodada `Finished`
    sem sucessora → reconciliar) fica como follow-up.
- **Foco de estudo:** coroutines + concorrência, transações otimistas

#### Decisão: lock otimista vs. pessimista no mercado (issue #21)

Dois compradores podem disputar o mesmo jogador. Escolhemos **lock otimista**
(`@Version`), mesma estratégia do claim de clube (#19):

- **Otimista (escolhido):** nenhuma trava no banco no caminho feliz; o conflito
  só "custa" quando há corrida real (raro). O `DELETE ... WHERE player_id = ?
  AND version = ?` afeta 0 linhas para o segundo a commitar → `OptimisticLock...`,
  traduzido em `PlayerNotAvailable` no `MarketPersistenceAdapter`. **Mais
  escalável** (sem locks segurados), idiomático em JPA e coerente com o resto do
  projeto.
- **Pessimista (alternativa):** `SELECT ... FOR UPDATE`
  (`@Lock(PESSIMISTIC_WRITE)`) serializa os compradores no banco. **Mais simples**
  de raciocinar (sem retry/tradução de exceção), mas segura locks e não escala
  bem sob contenção alta. Preterido.

A unidade transacional vive num bean **não-suspend** (`MarketClaimTransaction`),
porque `@Transactional` sobre função `suspend` é frágil — o `withContext` troca
de thread e a transação é thread-bound (vide nota no claim de clube).

---

## 9. Stack consolidada

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin 2.x (JVM 21) |
| Framework | Spring Boot 3.4.x |
| Build | Gradle (Kotlin DSL) |
| Persistência | Spring Data JPA + PostgreSQL |
| Migrations | Flyway |
| Cache | Caffeine |
| Async/Stream | Kotlin Coroutines + Flow |
| Serialização | kotlinx.serialization |
| Docs API | springdoc-openapi (Swagger) |
| Testes (backend) | JUnit 5, kotlin.test, MockK, `kotlinx-coroutines-test` |
| Frontend | Vite + React 18 + TypeScript (strict) |
| Estilo | Tailwind CSS |
| Estado de servidor (FE) | TanStack Query v5 |
| Mocks (FE) | MSW (Mock Service Worker) |
| Contrato API | OpenAPI 3.1 em `contract/openapi.yaml` (compartilhado FE ↔ BE) |
| Tipos gerados (FE) | `openapi-typescript` → `.d.ts` |
| Testes (FE) | Vitest, React Testing Library, Playwright (E2E) |
| Empacotamento | Docker multi-stage (como no Cartola) |

> **MockK** em vez de Mockito: feito para Kotlin, lida com `final` por padrão e tem sintaxe idiomática. Vale o tempo de aprender.

---

## 10. Primeiros passos práticos

**Backend (em série):**
1. **`start.spring.io`** → Gradle Kotlin DSL, JVM 21, deps: Web, Data JPA, PostgreSQL Driver, Validation, Actuator. Adicione coroutines, MockK e springdoc manualmente.
2. Crie **primeiro o módulo `domain`** e escreva a engine + testes **antes** de qualquer controller. Isso força a separação e dá retorno rápido de aprendizado.
3. Só depois suba a camada Spring ao redor do domínio já testado.

**Frontend (em paralelo a partir do passo 2 do backend):**
1. Escrever `contract/openapi.yaml` com os 9 endpoints (não precisa de implementação).
2. `npm create vite@latest frontend -- --template react-ts` e instalar Tailwind, TanStack Query, MSW, React Router, `openapi-typescript`.
3. Construir todas as telas contra mocks MSW; trocar mock por endpoint real conforme o backend libera cada controller.

Detalhes do frontend em [`FRONTEND.md`](FRONTEND.md).

---

*Documento de planejamento — GoalFather · v1.0*
