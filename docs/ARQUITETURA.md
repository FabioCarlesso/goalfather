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
// Disponibilidade como soma de tipos: a duração da lesão só existe DENTRO
// de Injured, então "lesionado sem duração" é irrepresentável (issue #54).
sealed interface Availability {
    data object Available : Availability
    data class Injured(val roundsOut: Int) : Availability
}

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
    val availability: Availability = Availability.Available,
) {
    val isStar: Boolean get() = overall >= 82
    val injured: Boolean get() = availability is Availability.Injured
}

// Postura da partida (issue #56). Os modificadores são PROPRIEDADES do valor
// do enum: `attackMod` escala a chance de gol do próprio time, `defenseMod` a
// do adversário. Sem `when` espalhado pela engine — o comportamento mora junto
// do valor, e uma postura nova não obriga a caçar branches pelo código.
enum class Posture(val attackMod: Double, val defenseMod: Double) {
    DEFENSIVE(0.82, 0.88), BALANCED(1.0, 1.0), OFFENSIVE(1.20, 1.12)
}

data class Tactics(val posture: Posture = Posture.BALANCED)

data class Lineup(
    val players: List<Player>,
    val formation: Formation,
    val tactics: Tactics = Tactics(),   // decisão da partida, salva com a escalação
) {
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
    data class Miss(override val minute: Int, val player: PlayerId, val home: Boolean) : MatchEvent
    data class Card(override val minute: Int, val player: PlayerId, val red: Boolean, val home: Boolean) : MatchEvent
    data class Injury(override val minute: Int, val player: PlayerId) : MatchEvent
    data class Save(override val minute: Int, val goalkeeperId: PlayerId?, val home: Boolean) : MatchEvent
    data class FullTime(
        override val minute: Int = 90,
        val homeGoals: Int,
        val awayGoals: Int,
        val stats: MatchStats,      // sumário derivado dos próprios eventos (issue #57)
    ) : MatchEvent
}

// consumo com when exaustivo — sem else, o compilador garante cobertura
fun describe(e: MatchEvent): String = when (e) {
    is MatchEvent.KickOff  -> "⚡ Bola rolando!"
    is MatchEvent.Goal     -> "⚽ GOL no minuto ${e.minute}!"
    is MatchEvent.Miss     -> "💨 Chute para fora"
    is MatchEvent.Card     -> if (e.red) "🟥 Vermelho!" else "🟨 Amarelo"
    is MatchEvent.Injury   -> "🚑 Lesão no minuto ${e.minute}"
    is MatchEvent.Save     -> "🧤 Defesa difícil"
    is MatchEvent.FullTime -> "🏁 Fim: ${e.homeGoals} × ${e.awayGoals}"
}
```

> **O valor prático do `sealed` apareceu na issue #57:** ao entrar a variante
> `Miss`, TODO `when` exaustivo do projeto parou de compilar de uma vez —
> engine, agregação de estatísticas e `PlayRoundService`. Nenhum ponto de
> consumo ficou para trás em silêncio, que é exatamente o que um `else`
> genérico (ou um `switch` com `default`) teria escondido.

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

**Tática entra como modificador, não como novo caminho de código (issue #56).** As constantes de probabilidade viraram uma BASE que cada partida escala:

```kotlin
// Peso de gol de um lado = quanto ELE ataca × quanto o adversário DEIXA atacar.
// É o produto que faz a postura do rival pesar: atacar contra um time fechado
// rende menos que contra um time aberto.
val homeGoalWeight = setup.home.attackFactor() * setup.away.defenseFactor()
val awayGoalWeight = setup.away.attackFactor() * setup.home.defenseFactor()

val goalP = (P_GOAL * (homeGoalWeight + awayGoalWeight) / 2).coerceIn(0.0, P_GOAL + P_SAVE)
val saveP = P_GOAL + P_SAVE - goalP   // o que sai do gol vira defesa, não some

// extension functions compondo postura + formação, cada uma com seus mods
fun Lineup.attackFactor(): Double = tactics.posture.attackMod * formation.attackMod
fun Lineup.defenseFactor(): Double = tactics.posture.defenseMod * formation.defenseMod
```

**Quem finaliza é sorteado por peso de posição (issue #57).** Antes, o autor do
gol saía de um sorteio uniforme sobre o elenco — o goleiro marcava tanto quanto
o centroavante. O peso é uma propriedade do próprio enum, então a regra fica em
um lugar só e o teste consegue verificar a distribuição:

```kotlin
enum class Position(val abbr: String, val scoringWeight: Double) {
    GK("GL", 0.0), CB("ZG", 1.0), MF("MC", 3.0), FW("AT", 6.0)
}

// roleta ponderada — consome UM nextDouble(), null quando ninguém pode finalizar
fun List<Player>.drawShooter(rng: Random): Player? { /* domain/rules/ScoringRules.kt */ }
```

**Estatísticas são projeção, não estado.** O `FullTime` carrega `MatchStats`
(finalizações, chutes no gol, defesas e cartões por time), calculado por
`Iterable<MatchEvent>.matchStats()` sobre os eventos que a própria partida
emitiu. Nada é contado em paralelo, então o sumário não tem como divergir do
feed — e a propriedade "recalcular a partir do stream dá o mesmo resultado" é
verificável em teste. Uma defesa conta para os dois lados: defesa de quem pegou,
finalização no gol de quem chutou.

Duas propriedades caem de graça dessa formulação: cartão e lesão mantêm a frequência de sempre (só gol e defesa trocam massa entre si), e o eixo neutro — `BALANCED` em 4-4-2, fatores 1.0 — reproduz byte a byte a engine de antes da tática, o que vira um teste de regressão em `MatchSimulatorTest`.

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
| ~~`POST`~~ | ~~`/api/clubs/{id}/matches?round=N`~~ | Removido do contrato (issue #82): partidas são jogadas por rodada (`POST /api/league/round/play`) |
| `GET` | `/ws/matches/{id}` | **WebSocket**: stream de eventos ao vivo |
| `GET` | `/api/market` | Jogadores disponíveis |
| `POST` | `/api/market/buy` | Contratar jogador |
| `POST` | `/api/market/sell` | Vender jogador |
| `POST` | `/api/clubs/{id}/stadium/expand` | Ampliar estádio |
| `POST` | `/api/clubs/{id}/medical` | Departamento médico: recupera stamina e encurta lesões — issue #54 |
| `POST` | `/api/clubs/{id}/training` | Foco de treino da semana (`ATAQUE`/`DEFESA`/`FISICO`/`DESCANSO`) — issue #58 |
| `PUT` | `/api/clubs/{id}/ticket-price` | Preço do ingresso (R$ 10–200, em centavos) — issue #59 |
| `GET` | `/api/clubs/{id}/board` | Painel da diretoria: meta da temporada, posição e confiança no técnico — issue #62 |
| `GET` | `/api/clubs/{id}/offers` | Propostas recebidas e enviadas pelo clube (só o dono) — issue #63 |
| `POST` | `/api/offers` | Proposta em dinheiro por jogador de outro técnico humano — issue #63 |
| `POST` | `/api/offers/{id}/accept` | Vendedor aceita: transferência atômica, rivais expiram — issue #63 |
| `POST` | `/api/offers/{id}/reject` | Vendedor recusa — issue #63 |
| `POST` | `/api/offers/{id}/withdraw` | Comprador retira — issue #63 |
| `GET` | `/api/league/standings` | Tabela de classificação |
| `GET` | `/api/league/cup?season=` | Chave da copa mata-mata (sem `season` = a mais recente) — issue #61 |
| `GET` | `/api/league/round/readiness` | Prontidão da rodada compartilhada (lobby) — issue #20 |
| `POST` | `/api/league/round/ready` | Técnico sinaliza "estou pronto" — issue #20 |
| `POST` | `/api/league/round/play` | Inicia a rodada (409 `ROUND_NOT_READY` se faltar técnico) |

DTOs separados das entidades de domínio (mapeamento explícito) — evita vazar o modelo interno na API e desacopla versionamento do contrato REST.

> **Isto foi débito real até a issue #56.** Os controllers devolviam os tipos de domínio direto, e o argumento para aceitar o atalho era que "o frontend gera os tipos do MESMO contrato, então o acoplamento já é implícito". Estava exatamente ao contrário: o frontend seguia o contrato, o backend seguia o domínio, e os dois divergiram em silêncio.
>
> Duas divergências viviam nessa fresta: `Lineup` saía como `{players, formation, tactics:{posture}}` (forma do domínio) onde o contrato dizia `{playerIds, formation, posture}`; e `Player.star`, sendo *computed property*, nunca era serializado — o campo existia no contrato, a UI o lia, e nunca chegava. Nenhuma das duas aparecia no CI, porque o E2E roda contra o MSW, que implementa o **contrato**, não o backend.
>
> Hoje os DTOs de resposta vivem em `adapter/in/web/dto/Responses.kt` (`ClubDto`/`LineupDto`/`PlayerDto`/`MarketEntryDto`/`TransferResultDto`), e `ResponsesTest` compara o **JSON literal** com o que o spec declara. Enums e sealed types do domínio cuja forma JSON já é a do contrato (`Position`, `Formation`, `Posture`, `Availability`) são reusados de propósito — duplicá-los criaria duas fontes de verdade para o mesmo conjunto fechado de valores.
>
> **Lição para o resto do projeto:** um contrato só é fonte de verdade se algo o comparar com as DUAS implementações. Mock que implementa o spec testa o spec, não o backend.

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
- [x] **4.6** Múltiplas divisões com promoção/rebaixamento — issue #47
  (clubes têm `division`; fixtures Berger e tabela por divisão; na virada de
  temporada os últimos N da divisão de cima trocam com os primeiros N da de
  baixo — regra pura em `domain/rules/PromotionRelegationRules.kt`. O
  contrato expõe `promotionSpots`/`relegationSpots` por tabela para a UI
  pintar as zonas sem duplicar a regra.)
- [ ] Mercado dinâmico — *futuro* (habilitado pelas divisões da 4.6)

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
  timeout que libera o início (não inicia sozinho — alguém ainda clica
  "Jogar rodada"; ver issue #86). Assim que o PRIMEIRO técnico sinaliza pronto começa a
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
    efeitos roda em `withContext(NonCancellable)`, e o `RoundWebSocketHandler`
    não cancela o stream quando o cliente sai: com a aba fechada a qualquer
    momento, a rodada termina no servidor sem pacing (antes ficava `InProgress`
    até alguém reconectar, e o cancelamento ainda era logado como `ERROR`). O que **não** está
    coberto é um **crash de processo** entre o claim e o fim dos efeitos: deixaria
    a rodada `Finished` com efeitos parciais e sem sucessora, e todo stream
    seguinte cairia no replay (a liga travaria). Recuperação (rodada `Finished`
    sem sucessora → reconciliar) fica como follow-up.
- [x] **Lock otimista no save de clube** (issue #89): o `save` recarregava a
  `ClubEntity` e copiava o domínio por cima, então o `@Version` comparava contra
  a versão recém-lida, não contra a que o use case leu — *lost update*
  silencioso (ex.: ampliar estádio × virada da rodada creditando bilheteria).
  Agora `Club.version` (um `Long` puro, sem JPA) viaja da leitura até o `save`,
  e o port devolve `ClubSaveResult` (`Saved` | `Conflict`). Detalhes na decisão
  "lock otimista do clube" abaixo.
- [x] **Transferências diretas entre técnicos** (proposta e aceite) — issue #63
  (a primeira mecânica social: o técnico A oferece dinheiro por um jogador do
  elenco do técnico B, e B aceita ou recusa. Sem contraoferta na v1.)

  **Máquina de estados como `sealed`.** O agregado novo `TransferOffer` carrega
  um `OfferStatus` `sealed` — `Pending`, `Accepted`, `Rejected`, `Withdrawn` e
  `Expired(reason)` —, e as transições (`accept()`, `reject()`, `withdraw()`,
  `expire()`, `afterRound()`) são funções puras com `when` exaustivo: só
  `Pending` tem saída, e transição a partir de estado terminal devolve `null`.
  O motivo da expiração (`DEADLINE` ou `PLAYER_TRANSFERRED`) só existe DENTRO de
  `Expired`. O prazo é um contador de rodadas (`roundsLeft`, 3 por padrão), e
  não "rodada de vencimento": o número da rodada recomeça a cada temporada.

  **Regras puras** em `domain/rules/TransferOfferRules.kt`: `offerBlocker`
  (valor mínimo, jogador no elenco do vendedor, vendedor humano, caixa do
  comprador, elenco cheio) e `squadViolationAfterSale` — o vendedor não fica
  com menos de 11 nem entrega o último goleiro. Rodam na criação (feedback
  imediato) e de novo no aceite, que é a checagem que vale.

  **Erros como valor em dois níveis.** `OfferResult` tem uma sub-hierarquia
  `sealed interface Rejection` com os erros de NEGÓCIO (vão no 200, contrato
  `TransferOfferResult`), separada de `NotOwner`/`ClubNotFound`/`OfferNotFound`
  (403/404). O controller faz `when` com `is Rejection` e segue exaustivo.

  **Aceite atômico.** `TransferOfferRepository.completeTransfer` grava vendedor
  e comprador (sob o compare-and-set de versão da issue #89), marca a proposta
  como aceita e expira as rivais pelo mesmo jogador — numa transação só, no bean
  não-suspend `TransferOfferTransaction`; falha vira `setRollbackOnly` + valor.
  Os clubes são travados **em ordem crescente de id e antes das propostas**:
  dois aceites pelo mesmo jogador disputam primeiro a linha do vendedor (o
  segundo perde com `Conflict`, relê e encontra a própria proposta expirada →
  `OfferExpired`), e A vendendo para B enquanto B vende para A não fazem
  deadlock. Toda gravação de proposta é `UPDATE ... WHERE status = 'PENDING'`:
  como os outros estados são terminais, o status já é a versão — sem
  `@Version` na tabela (migration V14). `TransferOfferConcurrencyIntegrationTest`
  cobre as duas corridas contra o H2.

  **Rodada.** O aceite é bloqueado com a rodada `InProgress` (`RoundInProgress`)
  — guarda de leitura; a consistência sob a corrida rodada × aceite continua
  vindo do lock otimista dos clubes. O `PlayRoundService` anda o prazo das
  pendentes uma vez por rodada encerrada, depois do claim da #46.

  **UI.** Painel "Propostas" na Dashboard (aceitar/recusar/retirar), badge no
  menu e página `/clubs/{id}` com o elenco do rival, a partir da Tabela.
  Notificação por polling (15s); WebSocket fica para a v2, junto de contraoferta,
  troca de jogadores e empréstimos.

  **Ajustes da revisão do PR #94.** O prazo das propostas passou para DEPOIS
  do agendamento da próxima rodada: é efeito secundário, e uma falha nele antes
  de `scheduleRound` deixaria a rodada `Finished` sem sucessora (o travamento
  de liga da #46). A varredura também expira (`PLAYER_TRANSFERRED`) a proposta
  cujo jogador já saiu do elenco do vendedor — vendido ao mercado ou aposentado
  —, regra pura em `TransferOffer.advance`; o aceite que encontra o jogador fora
  faz o mesmo na hora, em vez de deixar um "Aceitar" fadado a falhar. Proposta
  barrada por regra que pode mudar (caixa, elenco mínimo) segue pendente. A
  lista do clube traz TODAS as pendentes + as 50 resolvidas mais recentes
  (antes um `Top50` único escondia pendente antiga atrás do histórico). Uma
  pendente por comprador × jogador (`OfferAlreadyPending`), checagem de leitura
  — duas criações simultâneas ainda passam, sem corromper nada. Métrica
  `goalfather.transfer.offers{action,result}`.
- **Foco de estudo:** coroutines + concorrência, transações otimistas

### Fase 6 — Profundidade de gestão
- [x] **6.1** Fadiga, lesões com duração e departamento médico — issue #54
  (regra pura em `domain/rules/FitnessRules.kt`: titulares perdem 10–25 de
  stamina por rodada com piso em 40, reservas recuperam 12, e a `stamina`
  passa a escalar o `overall` efetivo abaixo de 70 — é o que dá sentido a
  rodar o elenco. A lesão deixou de ser um booleano eterno: virou o `sealed
  interface Availability` com `Injured(roundsOut)`, decrementado a cada
  rodada, e `SaveLineupService` recusa escalar lesionado com
  `LineupResult.InjuredPlayers`. `POST /api/clubs/{id}/medical` cobra
  R$ 30.000 e devolve +30 de stamina / −1 rodada de lesão, com caixa
  insuficiente modelado como valor (`MedicalResult.InsufficientFunds`), não
  exception. Migration V8 troca a coluna `injured` por `injured_for_rounds`.)
- [x] **6.2** Evolução e regressão de atributos por idade — issue #55
  (regra pura em `domain/rules/AgingRules.kt`, aplicada na virada de temporada
  dentro de `startNextSeasonClubs`: todo jogador ganha um ano, e a faixa etária
  da idade NOVA decide o sorteio — jovem até 23 (`-1..3`), auge de 24 a 29
  (`-1..1`), veterano dos 30 em diante (`-3..1`). O delta move `overall` e os
  quatro atributos juntos. Veterano de 36+ com `overall < 70` se aposenta e sai
  do elenco, liberando a folha; aos 41 a aposentadoria é compulsória — é ela que
  protege o invariante `age in 15..50` do `Player` de uma carreira infinita. A
  seed é `agingSeed(temporada, clube)`, com o mesmo empacotamento disjunto de
  `fitnessSeed` mais um salt, para que temporada 7 e rodada 7 não sorteiem a
  mesma sequência. **Reposição pela base:** quem se aposenta é substituído 1:1
  por um garoto de 17–19 anos na mesma posição, 8–18 pontos de `overall` abaixo
  do veterano e salário de R$ 3.000 — sem isso o elenco só encolhe e o clube da
  IA, que não compra ninguém, chegaria a zero jogador em poucas temporadas. O id
  do promovido sai de `youthPlayerId(clube, temporada, vaga)`, determinístico e
  numa faixa disjunta da do seed. As aposentadorias viajam no
  `RoundEvent.SeasonFinished` (`retirements`), então a UI conta a notícia. Quem
  está no mercado também envelhece — com seed própria — e sai da lista ao se
  aposentar; o aposentado é APAGADO do banco pelo novo port `PlayerRepository`,
  em vez de virar linha órfã com `club_id = null`.)
- [x] **6.3** Instruções táticas afetando a engine — issue #56
  (nova `Posture` — `DEFENSIVE`/`BALANCED`/`OFFENSIVE` — em
  `domain/model/Tactics.kt`, com os multiplicadores como propriedades do enum.
  `attackMod` escala a chance de gol do PRÓPRIO time, `defenseMod` a do
  adversário: ofensiva sobe as duas (jogo aberto faz e toma gol), defensiva
  baixa as duas cedendo menos do que deixa de criar — é esse desequilíbrio, e
  não o valor absoluto, que torna "fechar o time" racional contra adversário
  mais forte. A `Formation` ganhou os mesmos dois modificadores, com peso
  menor: a formação inclina, a postura decide. O `MatchSimulator` deriva as
  probabilidades por partida — o peso de gol de cada lado é `ataque dele ×
  defesa do rival`, e o que sai da chance de gol volta para a defesa, então
  cartão e lesão mantêm a frequência de sempre e o eixo neutro
  (EQUILIBRADA + 4-4-2) reproduz EXATAMENTE a engine anterior. Determinismo
  preservado: mesma seed + mesma tática = mesmos eventos.)

  **Por que a tática mora na `Lineup` e não no `Club`.** É a mesma decisão —
  quem joga, em que desenho, com que postura — e sai numa gravação só do
  `SaveLineupService`. Como a escalação já é persistida como JSON na coluna
  `clubs.lineup_json`, a postura viaja junto: nenhuma migração Flyway, e o
  estado fica completo ANTES da rodada começar, que é o que a issue #46 exige
  para réplicas simularem com os mesmos inputs. Escalação gravada antes da
  tática existir desserializa no default (`BALANCED`), sem backfill —
  coberto por `LineupSerializationTest`. O clube da IA, que nunca escala,
  cai no mesmo default.
- [x] **6.4** Engine mais rica e fiel ao protótipo — issue #57
  (artilheiro ponderado por `Position.scoringWeight` — FW 6, MF 3, CB 1, GK 0 —
  sorteado em `domain/rules/ScoringRules.kt`; novo evento `Miss` (chute para
  fora) e `Save` identificando o goleiro que defendeu; `Card` ganhou `home`
  para as estatísticas separarem os times; e o `FullTime` passou a carregar
  `MatchStats`, projeção dos próprios eventos via `Iterable<MatchEvent>.matchStats()`.
  A fatia de probabilidade do `Miss` saiu da do `Save` — cartão e lesão
  mantiveram a frequência de sempre. **Seeds antigas produzem partidas
  diferentes**: o sorteio ponderado consome o RNG de outra forma; nenhum teste
  dependia de placares fixos, só de propriedades, então o ajuste foi só de
  expectativas de posição/autoria.)
- [x] **6.5** Treino semanal com foco escolhido pelo técnico — issue #58
  (regra pura em `domain/rules/TrainingRules.kt`, aplicada na virada da rodada
  dentro de `persistRoundEffects`: o técnico escolhe um foco por semana —
  `ATAQUE`, `DEFESA`, `FISICO` ou `DESCANSO` — e o `enum class TrainingFocus`
  carrega os efeitos como propriedades do valor, no mesmo idioma de `Posture` e
  `AgeBand`. Foco técnico dá a cada jogador APTO uma chance de +1 no atributo
  correspondente — 25% para quem está na faixa `YOUNG`, 12% no auge, 5% no
  veterano, via a *extension property* `AgeBand.trainingUpgradeChance`, que
  reusa a faixa etária da issue #55 sem acoplar o enum dela a constantes de
  treino. O ganho move o atributo E o `overall`, porque é o `overall` que a
  engine lê: um +1 só no `shooting` seria decorativo, e o critério da issue é
  que o efeito chegue à partida SEM mexer na engine. Quem está lesionado não
  treina — a semana dele é de recuperação —, e o contador de lesão continua
  sendo movido só por `applyRoundFitness`, para a rodada não descontar duas
  vezes.

  **Calibragem, nas duas pontas** (a segunda saiu da review do PR #74): a
  recuperação de todo foco fica no intervalo `1..9` — `DESCANSO` devolve 8,
  `FISICO` 6, `ATAQUE`/`DEFESA` 3. No teto, nada alcança o desgaste MÍNIMO de
  um titular (`STARTER_STAMINA_LOSS.first`, 10): se uma semana de descanso
  zerasse o cansaço de quem jogou, a issue #54 deixaria de existir junto com o
  sentido de rodar o elenco e de pagar o departamento médico. No piso, nada
  recupera ZERO — com 0 (como os focos técnicos nasceram) o titular ficava
  preso exatamente em `STAMINA_MATCH_FLOOR` rodada após rodada enquanto todo
  clube da IA, sempre em `DESCANSO`, se estabilizava em 48: escolher `ATAQUE`
  significava jogar ~17% mais fraco em `effectiveOverall` pelo resto da
  temporada, o que é autossabotagem e não trade-off. `TrainingRulesTest` guarda
  o intervalo, não os números.

  O foco é estado do **clube** (migration V9, coluna `training_focus`), não da
  `Lineup`: vale ENTRE rodadas e para o elenco inteiro, enquanto a escalação é
  a decisão da partida. Fica valendo até o técnico trocar; quem nunca escolheu
  — e todo clube da IA — treina em `DESCANSO`. O treino roda DEPOIS do
  desgaste da rodada (a semana sucede a partida) e dentro do bloco que só o
  vencedor do claim da issue #46 executa, então é aplicado exatamente uma vez
  por rodada: o replay de uma rodada já encerrada não treina de novo. O
  extrato (`TrainingReport` com os `TrainingEvent` de quem evoluiu e de quem
  se machucou) viaja no `RoundEvent.RoundFinished`, como as finanças.
- [x] **6.6** Preço de ingresso definido pelo técnico, com demanda elástica — issue #59
  (a bilheteria era passiva: `FinanceRules` fixava o ingresso em R$ 50 e a
  ocupação dependia só da força do mandante. Agora o técnico define o preço
  — faixa `TICKET_PRICE_RANGE`, R$ 10 a R$ 200 — e a ocupação passa a ser
  `ocupação-base(força) × fator(preço / preço justo)`, com teto em 100%:
  o estádio não estica. O preço justo NÃO é constante: vai de R$ 40 (time
  fraco) a R$ 60 (time forte), então evoluir o elenco aumenta o que a torcida
  aceita pagar — sem isso o ingresso ótimo seria o mesmo número para todo
  mundo e a decisão viraria algo a decorar uma vez.)

  **Por que a cauda é hiperbólica.** Acima do justo o fator é
  `1 / (1 + k·(r−1)²)` com `k = 0.5`, e não um decaimento linear. Linear
  chegaria a ZERO dentro da faixa praticável, e dali para cima uma faixa
  inteira de preços renderia exatamente R$ 0 — receita plana onde deveria
  haver escolha. Com a hipérbole sempre sobra a torcida fiel, e a receita
  `público × preço` tem máximo único em `r = √(1 + 1/k) ≈ 1.73` vezes o preço
  justo (~R$ 69 para o time mais fraco, ~R$ 104 para o mais forte). Abaixo do
  justo o fator é linear e sobe até 1.5 — quem já lota no preço justo não
  ganha nada baratear, e é o teto de 100% que faz a receita voltar a subir
  depois de um certo desconto. `FinanceRulesTest` varre a faixa inteira e
  guarda as duas propriedades — monotonicidade (preço maior nunca aumenta
  ocupação) e máximo interior —, não os números.

  O preço é estado do **clube** (migration V10, coluna `ticket_price_cents`,
  default = os R$ 50 de antes para não mexer em save existente), como o foco
  de treino da 6.5. Fora da faixa, o comando devolve
  `TicketPriceResult.PriceOutOfRange` — VALOR, não exception: o técnico
  digitando um número fora do permitido é entrada normal de usuário, não um
  acidente. Por isso também não há `require` de faixa no `Club`: faixa é
  regra de balanceamento, não invariante estrutural do agregado, e um
  `require` derrubaria a leitura de uma linha antiga se a faixa fosse
  reapertada depois (o mapper acomoda com `coerceIn`). O endpoint é `PUT`
  `/api/clubs/{id}/ticket-price`, e não `POST` como os comandos vizinhos,
  porque é substituição idempotente de um ajuste — mandar o mesmo preço duas
  vezes deixa o mundo igual. `RoundFinance` passou a carregar `ticketPrice` e
  `attendance` além da receita: com o preço sob controle do técnico, só o
  total não diz se a rodada rendeu por estádio cheio ou por ingresso caro.
  **A engine de partida não mudou** — bilheteria é regra financeira de rodada,
  aplicada em `computeFinances`.
- [x] **6.7** Histórico de carreira do técnico e da liga — issue #60
  (a virada de temporada declarava um campeão e apagava tudo: tabela zerada,
  `goals` de cada jogador resetado, nenhuma memória. Agora ela grava antes um
  `SeasonRecord` — campeão, artilheiro da liga e a classificação final de
  todas as divisões, com o aproveitamento de cada clube —, montado pela regra
  pura `seasonRecordOf` em `domain/rules/SeasonHistoryRules.kt` e servido por
  `GET /api/league/history`, `/history/{season}` e `/history/club/{clubId}`.)

  **A ordem é a regra.** O snapshot é a PRIMEIRA coisa que `startNextSeason`
  faz, antes de `startNextSeasonClubs` zerar `goals`/`yellowCards`: montar o
  record depois daria um artilheiro sempre nulo. Os clubes passaram a ser
  lidos UMA vez no início da virada e atravessam as duas etapas — a leitura
  acontece depois de `persistRoundEffects`, então o caixa e os gols da última
  rodada já estão no estado que a história registra. `PlayRoundServiceTest`
  cobre a ordem plantando um artilheiro e exigindo que ele apareça no record
  *e* que o elenco salvo saia zerado. O artilheiro sai da liga inteira de
  graça: `persistRoundEffects` sempre acumulou `goals` para TODOS os clubes,
  IA incluída — era o gap que a issue pedia para verificar.

  **Append-only, no tipo e na tabela.** `SeasonRecord` é o único agregado do
  domínio que nunca sofre `copy()` — é história. O port
  `SeasonHistoryRepository` reflete isso: só tem `append`, sem `save` nem
  `delete`. A migration V11 dá PK a `season`, então duas réplicas disputando
  a virada (issue #46) não gravam dois campeões: o `existsById` resolve o
  retardatário comum e a colisão de PK resolve a corrida real, já que
  `existsById` + gravação não é atômico. Na prática o claim da rodada já
  garante uma execução só; a unicidade é a rede de segurança.

  **Duas armadilhas que a rede de segurança tinha (achado da review do PR
  #76).** A primeira: o `save` do Spring Data escolhe entre `persist` e
  `merge` pelo `isNew()`, e o default olha para o id — `season` é atribuído
  por nós e nunca é nulo, então a entidade parecia sempre existente e todo
  `save` virava `merge` (SELECT + UPDATE). O resultado é que a PK JAMAIS era
  violada: a segunda gravação sobrescrevia a primeira em silêncio e o `catch`
  de `DataIntegrityViolationException` era código morto — exatamente o oposto
  de append-only. Por isso `SeasonHistoryEntity` implementa `Persistable` com
  `isNew() = true`, o que é honesto para um agregado cuja única escrita é a de
  criação. A segunda: o `saveAndFlush` em vez de `save`, para o INSERT sair
  DENTRO do `try` e não no commit, onde a violação subiria como erro em vez
  de virar `false`. `SeasonHistoryIntegrationTest` guarda a primeira indo
  direto ao JPA (o caminho que duas réplicas tomam quando ambas passam pelo
  `existsById`) e exigindo a exceção; `SeasonHistoryPersistenceAdapterTest`
  guarda a tradução dela em `false`.

  **Por que `SeasonStanding` embrulha `StandingRow` em vez de repetir seus
  campos.** Copiar as dez colunas criaria dois lugares para consertar quando
  o critério de pontuação mudar. O que a história ACRESCENTA é a divisão (a
  linha sozinha não diz em que tier aquele 1º lugar valeu) e o
  `pointsPercentage` — gravado, não derivado na leitura, justamente porque é
  história: se a liga passar a dar 2 pontos por vitória, as temporadas antigas
  continuam contando a verdade da época delas.

  **`ClubCareer` é projeção, não agregado.** Temporadas disputadas, títulos e
  melhor campanha saem de `careerOf(clubId, records)` a cada leitura, em vez
  de um contador persistido que pode divergir do histórico que resume. O
  critério de "melhor" mora no backend (divisão pesa mais que posição, que
  pesa mais que pontos) para o frontend não reimplementá-lo. Clube sem
  temporada encerrada devolve 404 — quem nunca terminou um campeonato não tem
  carreira, e isso é diferente de uma carreira zerada.
- [x] **6.8** Copa mata-mata paralela ao campeonato — issue #61
  (copa da liga com os mesmos clubes, em jogo único. O sorteio é a regra pura
  `drawCup` em `domain/rules/CupRules.kt`, com `Random(cupDrawSeed(temporada))`:
  os clubes entram numa chave com a menor potência de 2 que os comporta, e as
  vagas que sobram viram folgas — 12 clubes dão oitavas com 4 folgas. As folgas
  ocupam primeiro os confrontos de índice par, porque os vencedores de `2k` e
  `2k+1` se cruzam na fase seguinte: assim quem folga estreia contra quem venceu
  um jogo, em vez de duas folgas se encontrarem. Não há novo sorteio por fase — o
  caminho até a final sai inteiro do sorteio inicial, como numa copa de verdade.
  O agregado é `Cup` → `CupStage` (fase) → `CupTie`, e o `CupTie` é `sealed`:
  `Bye` não tem adversário nem placar, `Fixture` tem; "folga com placar" é
  irrepresentável, a mesma lição de `Availability`. O `TieResult` recusa no
  `init` um empate sem pênaltis — invariante estrutural do mata-mata, não regra
  de balanceamento.)

  **Pênaltis na engine.** `MatchSetup` ganhou `knockout`; em jogo de copa
  empatado no minuto 90 a engine chama `penaltyShootout` (`PenaltyRules.kt`) com
  o MESMO `Random` da partida e emite `MatchEvent.PenaltyShootout` antes do
  `FullTime` (que segue sendo o último evento e segue com o placar dos 90'). Como
  a disputa consome o RNG depois do tempo normal, um jogo de copa é byte a byte o
  mesmo jogo de liga até o apito — e mesma seed dá a mesma disputa, inclusive no
  drill-down `/ws/matches/{id}` e no replay. Série de 5 alternada, encerrada
  quando um lado não alcança o outro, depois morte súbita; a conversão usa o
  `effectiveOverall` de batedor e goleiro, então a fadiga de um calendário
  apertado (issue #54) também erra pênalti.

  **Calendário: data × rodada de liga.** A copa não ganhou um fluxo próprio: a
  `Round` virou uma DATA do calendário, com `stage: RoundStage` — `sealed`
  `League(matchday)` / `Cup(phase)`. É o mesmo `Round.number` de sempre (PK,
  rota do WS, chave da prontidão), então prontidão (#20), escape hatch (#45) e o
  claim `finishRound` (#46) valem para a copa sem uma linha nova — o ponto de
  atenção multiplayer da issue. A sequência sai da regra pura `seasonCalendar`:
  a fase `i` entra logo depois da rodada de liga `⌊i·L/(C+1)⌋`, o que dá "a cada
  3 rodadas, 1 fase" com 15 rodadas e 4 fases, e uma fase depois de cada uma das
  4 primeiras rodadas no seed de 5. A data 1 é sempre de liga e a final cai antes
  da última rodada, então o campeão da copa chega à virada. O calendário NÃO é
  persistido: é recalculado dos clubes e da copa a cada fim de data. Sem copa ele
  é só a liga e `matchday == number`, exatamente o comportamento anterior — é
  também o que acontece com um save de antes da issue, que só ganha copa na
  próxima virada (migration V12: tabela `cups` + `rounds.stage_json`, nulo lido
  como liga). Os pontos que dependiam de "rodada" passaram por um `when` sobre o
  `sealed`: a tabela ignora data de copa e conta pela `matchday`; o Berger gira
  pela `matchday` e o `matchId` usa a data; a folha (`isSalaryDate`) segue as
  rodadas de liga, então a cadência de salário da temporada não mudou.

  **Premiação e virada.** Quem vence um confronto leva `KnockoutPhase.prizeCents`
  da fase (folga não premia). O prêmio entra no `RoundFinance.cupPrize` e no caixa
  pela mesma conta da bilheteria (`RoundFinance.cashDelta()` em `FinanceRules`),
  dentro do bloco que só o vencedor do claim executa — reaplicar a rodada não
  premia duas vezes. Na virada, o campeão da copa vai para o `SeasonFinished`
  (`cupChampion`) e para o `SeasonRecord` da issue #60, a carreira ganha
  `cupTitles`, e a copa da temporada nova é sorteada com os clubes já nas
  divisões novas. Liga com menos de 4 clubes não tem copa (`canHoldCup`).

  **Achado de infraestrutura.** `ResponseEntity<Any>` faz o Spring procurar o
  serializer do kotlinx pelo tipo DECLARADO (`Object`), não achar, e cair no
  Jackson — que ignora `@JsonClassDiscriminator`. Para tipos planos passava
  despercebido; para o `RoundStage` e o `CupTie` o `"type"` sumiria e o `oneOf`
  do contrato ficaria impossível de desambiguar. `/round/current` e `/cup`
  passaram a serializar com o `Json` do kotlinx explicitamente (`Json.okJson`,
  com `reified`), e `LeagueCupMvcTest` trava o discriminador no JSON cru. A
  review do PR #78 achou o mesmo buraco, ANTERIOR à copa, nos outros corpos com
  `sealed`: o `ClubDto` (o `availability` de cada jogador saía `{}`, então a UI
  via todo lesionado como apto) e o `TransferResultDto` de compra/venda (sem
  `type`, a UI não reconhecia o `Success`). Todos os corpos de sucesso com
  `sealed` passaram por `okJson`; `SealedPayloadMvcTest` cobre clube, comando
  que devolve o clube e venda. Para um `sealed` no topo o `reified` importa: o
  tipo inferido é a interface, então sai o serializer polimórfico, que escreve
  o `type`.

  **Rodada corrente depois da virada (achado da mesma review, anterior à
  copa).** `findLatest` ordenava só por `number`, mas a PK de `rounds` é só
  `number` e a virada reescreve apenas a linha 1: as datas 2..N da temporada
  encerrada continuam na tabela, `Finished`, e a maior delas virava a "atual"
  — `/round/current` mostrava a rodada velha e `/round/play` respondia 409,
  travando a liga depois da 1ª temporada contra o backend real (o E2E roda no
  MSW, que guarda a rodada em memória). Agora a ordenação é `season DESC,
  number DESC`; `CurrentRoundIntegrationTest` reproduz a virada contra o H2.
- [x] **6.9** Metas da diretoria: objetivo de temporada, bônus e demissão — issue #62
  (o jogo não tinha condição de derrota. Agora, no início de cada temporada, a
  diretoria fixa uma meta de posição para cada clube — regra pura
  `boardExpectations` em `domain/rules/BoardRules.kt`, determinística dada a
  composição da liga: cada clube é ranqueado pela força do ELENCO
  (`squadRating`, soma dos 11 melhores ÷ 11, sem fadiga nem escalação) contra
  os da própria divisão. Os ~15% mais fortes (3 numa liga de 20, 1 numa de 6)
  miram o título, a metade de cima mira a metade de cima e o resto, o meio da
  tabela. `BoardObjective` carrega o bônus como propriedade do valor, no idioma
  de `TrainingFocus`.)

  **Confiança por rodada.** Cada rodada de LIGA move a confiança (0..100) pela
  distância posição × meta, dentro da MESMA gravação por clube dos demais
  efeitos (`persistRoundEffects`) — por isso a tabela da rodada passou a ser
  calculada antes dos efeitos. Sobe devagar e cai rápido, com teto por rodada
  para uma rodada só não decidir a temporada. Data de copa não mexe na tabela
  e, pelo mesmo `when` sobre o `sealed` `RoundStage`, não mexe na confiança.
  Só as divisões que JOGARAM a data são avaliadas: com divisões de tamanhos
  diferentes, a menor folga no fim do calendário, e reavaliar a tabela parada
  dela empurraria a confiança (até a demissão) sem jogo. Quem assume um clube
  no meio da temporada (claim) herda a meta, mas a confiança volta ao valor
  inicial: o técnico novo não responde pelo que veio antes dele.

  **Veredito como valor.** Na virada, `BoardVerdict` é `sealed`: `TargetMet`
  (bônus do objetivo no caixa, como delta), `TargetMissed` (fica, sem bônus)
  ou `Fired` — meta perdida com a confiança em `FIRING_CONFIDENCE` ou abaixo.
  Usar a confiança, e não só a posição final, separa "afundou a temporada
  inteira" de "tropeçou na última rodada com a diretoria satisfeita". O
  balanço de todos os clubes viaja no `SeasonFinished` (`boardReviews`), e a
  meta da temporada nova é fixada DEPOIS do envelhecimento e da troca de
  divisão.

  **Demissão = claim ao contrário.** `ClubClaimRepository.release` devolve o
  clube ao pool da IA e tira o clube do usuário na MESMA transação, registrando
  a `Dismissal` (migration V13: colunas `board_*` em `clubs` e
  `users.dismissal_json`). O UPDATE do clube é condicional ao DONO e incrementa
  a versão, então não disputa lock com ninguém e invalida um `save` baseado na
  leitura antiga. A `Dismissal` só é gravada se o usuário ainda aponta para o
  clube (`BoardReview.toDismissal()` monta o registro no domínio). Clube da IA
  "demitido" não tem efeito. Na v1 isso é o game over: o `AuthUser` passa a
  carregar `dismissal` — a `RoundPage` rebusca `/me` sozinha quando chega o
  `Fired` do próprio clube —, a UI mostra a tela de demissão com o histórico do
  clube (#60) e o técnico recomeça escolhendo outro clube — o claim apaga a
  demissão. No multiplayer, o mesmo `release` já
  devolve o clube do humano demitido à IA.

  **Saves anteriores à issue.** Clube sem meta (colunas nulas) recebe a meta na
  boot (`DataInitializer.backfillBoards`, pela mesma regra e uma vez só); a
  primeira rodada e o `GET /api/clubs/{id}/board` ainda derivam a meta como rede
  de segurança, se o backfill perder uma corrida de lock. Liga nova já nasce com
  as metas no seed (`DataInitializer`). O `BoardStatus` traz `onTarget` pronto,
  para a UI não recomparar posições. A engine
  de partida não mudou.
- [ ] Mercado dinâmico / variação de preços — *futuro*

##### Por que `AgingOutcome` e não `Player?`

A issue sugeria `fun Player.ageOneSeason(rng: Random): Player?`, com `null` para
o aposentado. Funciona, mas o `null` só diz "sumiu": não distingue quem evoluiu
de quem regrediu, e obriga cada caller a recalcular a diferença de `overall`
para contar a história. O `sealed interface AgingOutcome`
(`Evolved`/`Steady`/`Regressed`/`Retired`) devolve o desfecho **já
interpretado**, o `when` que separa quem fica de quem sai é exaustivo sem
`else`, e um desfecho novo (empréstimo, promoção da base) vira erro de compilação
em cada ponto de uso em vez de um `null` mal tratado. Mesma lição de
`Availability` na 6.1: quando o domínio tem N desfechos, o tipo diz quais são.

##### Por que a base repõe o aposentado (e por que as idades do seed foram espalhadas)

A primeira versão deixava o elenco apenas encolher. Simulando a regra sobre um
elenco da IA — 11 jogadores, **todos com 25 anos**, como o seed criava —, o
resultado era brusco: como a idade era uniforme, os onze cruzavam a barreira dos
36 na MESMA virada e o clube ia de 11 jogadores a zero por volta da temporada
11 (com `strength` 60; até os de 80 zeravam na 16ª). O jogo não quebrava — o
`MatchSimulator` ignora elenco vazio —, mas a liga perdia sentido, e o técnico
humano travava antes disso: com menos de 11 jogadores, `SaveLineupService`
devolve `IncompleteLineup` para sempre.

Duas correções, ambas baratas:

1. **Promoção da base 1:1** na própria virada — o elenco nunca encolhe, e a
   renovação vira parte do jogo (o garoto entra pior, mas na faixa que mais
   evolui);
2. **Idades espalhadas no seed** da IA (21–32 em vez de 25 para todos), o que
   troca a aposentadoria em bloco por uma ou duas saídas por temporada.

O teste `vinte temporadas seguidas nao esvaziam o elenco` (e o gêmeo no mock) é
a regressão disso.

*Follow-up que segue aberto:* a IA não compra nem vende — a base repõe o número,
não a qualidade, então um elenco de IA tende a se estabilizar num nível mais
baixo ao longo de muitas temporadas. Fecha junto do mercado dinâmico.

##### Por que `Availability` e não `injuredForRounds: Int`

O par `injured: Boolean` + duração admitiria estados sem sentido —
`injured = false` com 3 rodadas de afastamento. Um `Int` sozinho resolve isso,
mas ainda deixa o "0 = apto" como convenção implícita que todo caller precisa
lembrar. Com o `sealed interface`, a duração só é representável **dentro** de
`Injured`, o `when` sobre as variantes é exaustivo sem `else`, e o compilador
avisa em cada ponto de uso quando uma variante nova aparecer (ex.: suspensão
por cartão). É o exercício de "torne estados inválidos irrepresentáveis" que a
issue pedia. O `Int` continua existindo — mas só na coluna do banco, onde não
há soma de tipos, reconstruído no `PlayerMapper`.

##### Nota: escalação salva vs. estado do jogador

`Club.lineup` é persistido como um `Lineup` serializado, ou seja, uma **foto**
dos jogadores no momento em que o técnico escalou. Essa foto envelhece a cada
rodada. Antes da issue #54 isso era só cosmético (gols/cartões desatualizados
no JSON); com fadiga e lesão passou a ser bug de regra. Por isso
`Club.startingLineup()` **revalida a escalação salva no apito inicial**, em
duas frentes:

1. **Reidrata os titulares pelo id a partir do `squad`**, única fonte de verdade
   do estado do jogador — sem isso o time entraria em campo com a stamina do dia
   da escalação e a fadiga nunca chegaria à força do time.
2. **Descarta lesionados e completa com reservas aptos.** `SaveLineupService`
   recusa *salvar* escalação com lesionado, mas quem se machuca DEPOIS seguiria
   titular na rodada seguinte — o guard de salvamento não alcança a escalação já
   persistida. Sem reservas aptos o time entra desfalcado, com menos de 11, que
   é o resultado correto para um elenco dizimado.

A substituição é simples e determinística (ordem do elenco), não uma
auto-escalação inteligente, e não tenta casar posição — validação posicional
segue fora do domínio, como no `SaveLineupService`.

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

#### Decisão: lock otimista do clube — versão lida + compare-and-set (issue #89)

Todo comando sobre o clube é um *read-modify-write* em duas transações (o
`findById` do use case e, depois, o `save`). Para o lock proteger alguma coisa,
o `save` precisa comparar com a versão **da leitura do use case**:

- **`Club.version`** é dado puro do agregado (regra de ouro: nada de `@Version`
  no domínio). O mapper a sobe da `ClubEntity` e cada `copy()` a carrega até o
  `save`.
- **Compare-and-set explícito** na `ClubSaveTransaction` (bean não-suspend, como
  os claims): `UPDATE clubs SET version = version + 1 WHERE id = ? AND version = ?`.
  Zero linhas = alguém gravou depois da leitura → `ClubSaveResult.Conflict`, sem
  gravar clube nem elenco. Por que não só o `@Version`: o Hibernate só incrementa
  a versão quando a LINHA do clube muda, e o desgaste/treino da rodada mexe só no
  elenco — duas escritas assim não se enxergariam. O UPDATE também segura o lock
  de linha até o commit, então duas gravações sobrepostas se serializam ali.
  `@Version` continua como rede de segurança; qualquer `ConcurrencyFailureException`
  vira o mesmo `Conflict` no adapter — nenhum controller vê exception de lock.
- **Quem reaplica e quem devolve conflito:**
  - *Ajuste de uma coluna* (preço do ingresso, foco de treino): nem passa pelo
    read-modify-write. `updateTicketPrice`/`updateTrainingFocus` são UPDATEs
    atômicos só daquela coluna, que também sobem a versão. **Nunca conflitam**,
    não pisam nas outras colunas, e uma escrita concorrente baseada em leitura
    anterior (ampliar, comprar) ainda recebe `Conflict`. Na revisão do PR #92,
    a versão com retry ainda devolvia 409 em ~4% das requisições sob 4
    escritores contínuos no mesmo clube.
  - *Escalação*: substitui o ajuste, mas é validada contra o elenco (lesões,
    vendas). Usa retry automático (`retryOnConflict`, até 4 tentativas com
    backoff exponencial + *jitter*, para o perdedor não recolidir na hora com o
    mesmo escritor): relê, **revalida** e reaplica.
  - *Efeito acumulado sobre o caixa* (ampliar estádio, departamento médico,
    compra, venda): devolve `Conflict` → 409 `CLUB_WRITE_CONFLICT` (ou a
    variante `Conflict` do `TransferResult` no mercado). O técnico decidiu com o
    caixa que viu; reaplicar em silêncio decidiria por ele. Na compra, o
    jogador já saiu do mercado no claim (#21), então o conflito o **devolve ao
    mercado** (compensação); na venda o clube é gravado antes do anúncio, então
    basta não anunciar.
  - *Virada da rodada*: não tem a quem devolver 409. `saveReapplying` relê o
    clube e recalcula os efeitos (caixa como **delta**, elenco pelos RNGs
    semeados por rodada + clube, logo determinístico), até 5 tentativas com o
    mesmo backoff.
    Esgotar é contenção patológica — único caso que vira exception.
- **Pessimista (preterido):** `SELECT ... FOR UPDATE` serializaria tudo, mas a
  leitura do use case acontece numa transação anterior à do `save`, então o
  lock teria de atravessar o use case inteiro (ports `suspend` por agregado) —
  e mexer no fluxo da rodada (#46), fora do escopo.

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
| Docs API | Swagger UI (webjar) servindo o `contract/openapi.yaml` em `/swagger-ui.html` — sem springdoc: o contrato é escrito à mão e é a fonte de verdade |
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
1. **`start.spring.io`** → Gradle Kotlin DSL, JVM 21, deps: Web, Data JPA, PostgreSQL Driver, Validation, Actuator. Adicione coroutines, MockK e o webjar do Swagger UI manualmente (o contrato é escrito à mão — sem springdoc gerando spec dos controllers).
2. Crie **primeiro o módulo `domain`** e escreva a engine + testes **antes** de qualquer controller. Isso força a separação e dá retorno rápido de aprendizado.
3. Só depois suba a camada Spring ao redor do domínio já testado.

**Frontend (em paralelo a partir do passo 2 do backend):**
1. Escrever `contract/openapi.yaml` com os 9 endpoints (não precisa de implementação).
2. `npm create vite@latest frontend -- --template react-ts` e instalar Tailwind, TanStack Query, MSW, React Router, `openapi-typescript`.
3. Construir todas as telas contra mocks MSW; trocar mock por endpoint real conforme o backend libera cada controller.

Detalhes do frontend em [`FRONTEND.md`](FRONTEND.md).

---

*Documento de planejamento — GoalFather · v1.0*
