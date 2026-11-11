# Dados do mundo (pack `data/world/`)

> Issue #126 · épico #148 (Fase 8, onda 2). Os dados reais vêm na #127 e o seed passa a usar o pack na #131.

O mundo do jogo (países, competições, clubes, elencos, estádios, datas FIFA e janelas de transferência) é **dado**, não código. Ele fica no repositório, em `data/world/`, organizado **como se fosse uma API externa**: cada arquivo é a resposta de um "endpoint". O backend lê esse pack por um port (`WorldDataProvider`) e o traduz para o domínio por uma **camada anticorrupção**. Trocar o arquivo por um pack fora do git ou por uma API HTTP de verdade é escrever outro adapter. O domínio não muda.

## Layout

```
data/world/v1/
├── countries.json                    GET /countries
├── competitions.json                 GET /competitions
├── competitions/{id}/clubs.json      GET /competitions/{id}/clubs
├── clubs/{id}/squad.json             GET /clubs/{id}/squad
└── calendar/{year}.json              GET /calendar/{year}
```

`v1` é a versão **do layout**. Os ids (`bra-serie-a`, `bra-flamengo`, `agustin-rossi`) são estáveis entre versões do pack e só podem ter minúsculas, dígitos e hífen (`[a-z0-9][a-z0-9-]*`), porque viram parte do caminho. Um id fora do padrão é recusado antes de qualquer leitura.

## Envelope

Todo arquivo tem o mesmo envelope:

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-11-11T00:00:00Z",
  "source": "Wikidata (CC0), consulta de 2026-11-10",
  "data": ...
}
```

- `schemaVersion`: versão do formato de `data`. O loader lê **só o cabeçalho primeiro**. Se a versão não for suportada (hoje só `1`), o erro é `UnsupportedSchemaVersion`, e não um "campo X faltando" que esconderia a causa real.
- `generatedAt` e `source`: proveniência, para auditoria. Não são interpretados.
- **Campos desconhecidos são ignorados** (`ignoreUnknownKeys = true`). Um pack com campos novos continua sendo lido por código antigo. Campos opcionais têm default, então o contrário também vale.

## Schema (`schemaVersion: 1`)

### `countries.json` → lista

| Campo | Tipo | Notas |
|---|---|---|
| `code` | string | Trigrama FIFA (`BRA`, `ENG`, `GER`), 3 letras maiúsculas (ver `CountryCode`). |
| `name` | string | Nome em português. |
| `confederation` | string | `CONMEBOL`, `UEFA`, `CONCACAF`, `CAF`, `AFC` ou `OFC`. |
| `isoAlpha2` | string | Para a bandeira (`BR`, `GB-ENG`). |

Todo código de país usado em outro arquivo (país de clube ou competição, nacionalidade, federação de janela) **tem que estar aqui**. Se não estiver, o erro é `UnknownCountry`.

### `competitions.json` → lista

| Campo | Tipo | Notas |
|---|---|---|
| `id` | string | `bra-serie-a`, `conmebol-libertadores`. |
| `name` | string | |
| `country` / `confederation` | string | **Exatamente um**: país (liga/copa nacional) ou confederação (continental). |
| `tier` | int? | Divisão (1 = elite). Só em ligas. |
| `strength` | int? | 1..99, "overall típico de um titular mediano". Calibra o gerador de atributos. Copas e continentais não têm. |
| `format` | objeto | Discriminado por `type` (abaixo). |
| `tiebreakers` | string[]? | Valores de `Tiebreaker`; o primeiro é sempre `POINTS`. Ausente = default do jogo. |

Formatos (espelham `CompetitionFormat`, issue #124):

```json
{ "type": "roundRobin", "legs": 2 }
{ "type": "knockout", "stages": [{ "name": "Final", "teams": 2, "legs": 1, "newEntrants": 2 }] }
{ "type": "groupsThenKnockout", "groups": 8, "perGroup": 4, "qualifiersPerGroup": 2, "groupLegs": 2, "knockout": [...] }
{ "type": "swissLeague", "teams": 36, "matchesPerTeam": 8, "directQualifiers": 8, "playoffFrom": 9, "playoffTo": 24 }
```

As regras de consistência (a conta de vencedores + entrantes fechando fase a fase) são as dos `init` do domínio. O mapper não as duplica: só converte a violação em `InvalidValue`.

### `competitions/{id}/clubs.json` → lista

```json
{ "id": "bra-flamengo", "name": "Flamengo", "country": "BRA", "reputation": 90,
  "stadium": { "name": "Maracanã", "capacity": 78838 } }
```

`reputation` (1..100, 50 = mediano na liga) é **decisão nossa** a partir de fatos públicos. Não é rating de terceiros.

Um item **só com `id`** é uma *referência* a um clube definido por completo em outra competição. A Copa do Brasil, por exemplo, cita os clubes da Série A sem repetir os dados, como uma API que embute só o id de um recurso relacionado. Os erros possíveis:

- uma referência que nenhuma competição define gera `UnknownClubReference`;
- o mesmo clube definido duas vezes com dados diferentes gera `ConflictingClubDefinition`.

### `clubs/{id}/squad.json` → objeto

```json
{ "players": [
  { "id": "agustin-rossi", "name": "Agustín Rossi", "birthDate": "1995-08-21",
    "nationality": "ARG", "secondNationality": null, "position": "GK", "displayName": null }
] }
```

- `position`: `GK`, `DF`, `MF`, `FW` (vocabulário do Wikidata). `CB`, `GOALKEEPER` e afins também são aceitos. O mapper traduz `DF` para o `CB` do jogo, e é aqui que a anticorrupção aparece de forma mais visível.
- `displayName` (opcional): nome exibido no lugar do real (pedido de remoção, homônimo), **sem mudar o `id`**. Saves e históricos continuam apontando para o mesmo jogador.
- Erros possíveis: elenco sem goleiro gera `SquadWithoutGoalkeeper`; o mesmo `id` de jogador em dois elencos gera `DuplicatePlayer`.

### `calendar/{year}.json` → objeto

```json
{ "year": 2026,
  "fifaWindows": [{ "start": "2026-03-23", "end": "2026-03-31" }],
  "transferWindows": [{ "country": "BRA", "start": "2026-01-05", "end": "2026-03-03" }] }
```

As datas FIFA viram `FifaWindow` (o tipo compartilhado com o calendário semanal da #125). As janelas de transferência, por federação (o país da federação: `BRA` = CBF), viram `TransferWindow`. O comportamento de abrir e fechar o mercado é da #137. O `year` tem que bater com o nome do arquivo. No adapter de arquivo, os anos disponíveis são os arquivos presentes em `calendar/`.

## Política de direitos

| Categoria | No pack? | Origem |
|---|---|---|
| Nomes de países, competições, clubes, estádios e capacidade | **Sim, real** | Fatos públicos (Wikidata, CC0, na #127). |
| Nome, data de nascimento, nacionalidade e posição do jogador | **Sim, real** | Fatos públicos. `displayName` permite substituir o nome. |
| Overall, pace, shooting, passing, defending | **Não, simulado** | `AttributeGenerator` (regra nossa). |
| Salário, valor de mercado, contrato, caixa do clube | **Não, simulado** | Regras do jogo (#131, #135, #136). |
| Escudos, uniformes, fotos, logos de competição | **Nunca** | Marcas registradas. A UI usa iniciais e cores neutras. |

**Não copiar** ratings do EA FC/sofifa nem valores do Transfermarkt. Se um pack trouxer `overall` ou `marketValue`, o `ignoreUnknownKeys` descarta: os DTOs nem têm esses campos (a fixture de teste prova isso).

### Atributos simulados (`domain/rules/AttributeGenerator.kt`)

Função pura com `Random` injetado: a mesma seed produz os mesmos atributos.

- **Overall esperado** = `strength` da liga do clube + reputação (`(rep − 50) / 5`, de −9 a +10) + curva de idade. A curva sobe até ~24 anos, tem platô até 30 (33 para goleiro) e depois cai. Somam-se um ruído "triangular" (soma de dois dados, −6..+6) e um corte em 35..94.
- **Detalhados** = overall + perfil da posição (atacante +8 de finalização e −22 de defesa; zagueiro +8 de defesa etc.) + ruído de ±3, cortados em 1..99.
- A força da liga de um clube é a da liga mais forte em que ele aparece (`WorldPack.leagueStrengthOf`). Copas não contam.
- O gerador consome o `Random` a cada chamada. Para que um jogador não dependa da ordem dos outros, quem chama pode dar um `Random` por jogador (por exemplo, com a seed derivada do id). Essa decisão fica para o seed da #131.

## Validação: erro como valor

`WorldDataProvider.load()` nunca lança exceção por causa de dado ruim. Ele devolve `PackLoadResult`:

- `Loaded(pack: WorldPack)`;
- `Invalid(errors: List<PackLoadError>)`: **todos** os problemas encontrados, não só o primeiro. Os tipos de erro são `UnsupportedSchemaVersion`, `MissingResource`, `MalformedResource`, `InvalidValue`, `UnknownCountry`, `UnknownClubReference`, `ConflictingClubDefinition`, `SquadWithoutGoalkeeper` e `DuplicatePlayer`.

Os erros citam o **recurso lógico** (`clubs/bra-flamengo/squad.json`), o mesmo nome que seria a URL numa API, e não um caminho de disco.

## Arquitetura e como trocar o adapter

```
application/port/out/WorldDataProvider.kt   port (suspend) + PackLoadResult/PackLoadError
domain/model/world/WorldPack.kt             o mundo como o domínio o vê (só fatos reais)
domain/rules/AttributeGenerator.kt          atributos simulados
adapter/out/worlddata/
├── dto/WorldPackDtos.kt                    forma EXTERNA (envelope, DTOs) — só aqui
├── PackMapper.kt                           DTO → domínio + acumulação de erros
├── WorldPackLoader.kt                      percorre os recursos, checa versão e referências
└── FileWorldDataProvider.kt                "ler um recurso" = ler do disco
config/WorldDataConfig.kt                   bean + property app.world-data.path
```

O `WorldPackLoader` recebe `read: suspend (resource: String) -> String?`. O adapter de arquivo passa "ler do disco". Por isso:

- **Pack fora do git:** basta apontar `WORLD_DATA_PATH` (property `app.world-data.path`, default `../data/world/v1`, relativo a `backend/` no `bootRun`) para outro diretório.
- **API HTTP com o mesmo formato:** crie um `HttpWorldDataProvider : WorldDataProvider` que passa um `read` fazendo `GET {baseUrl}/{resource}` (404 → `null`) e uma lista de anos de calendário. Depois, troque o `@Bean` em `WorldDataConfig`, por property ou profile. Loader, mapper, domínio e testes de validação são reaproveitados.
- **API com formato diferente:** escreva DTOs e um mapper próprios desse adapter, produzindo o mesmo `WorldPack`. O domínio continua sem saber da troca.

O `ArchitectureBoundaryTest` garante que nem `domain/` nem `application/` importam nada de `adapter` (os DTOs do provedor incluídos).

## Estado atual e próximos passos

- O bean `WorldDataProvider` está registrado, mas **o seed do jogo ainda é o `InitialLeague`**. A troca é da #131. A carga é preguiçosa: sem o pack no disco (por exemplo, na imagem Docker, que não copia `data/`), a aplicação sobe normalmente, e só o `load()` devolve `MissingResource`.
- `data/world/v1/` tem hoje um **exemplo mínimo** (Série A com dois clubes e elencos parciais, montado à mão). O pack real vem do script de importação do Wikidata (#127). O teste `FileWorldDataProviderTest` carrega esse pack em todo build, então ele nunca fica inválido sem que o CI perceba.
- O catálogo `Countries` em `domain/model/Country.kt` continua sendo o interino da #123, usado pela persistência. A substituição dele pelos países do pack acontece com o seed (#131).
