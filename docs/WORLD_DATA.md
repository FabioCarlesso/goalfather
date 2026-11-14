# Dados do mundo (pack `data/world/`)

> Issues #126 (provedor) e #127 (importação do Wikidata) · épico #148 (Fase 8). O seed passa a usar o pack na #131.

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
  "generatedAt": "2026-11-13T15:26:40Z",
  "source": "Wikidata (CC0), consulta de 2026-11-13; curadoria em data/world/*.yaml",
  "generator": "world-import 1.0.0",
  "data": ...
}
```

- `schemaVersion`: versão do formato de `data`. O loader lê **só o cabeçalho primeiro**. Se a versão não for suportada (hoje só `1`), o erro é `UnsupportedSchemaVersion`, e não um "campo X faltando" que esconderia a causa real.
- `generatedAt`, `source` e `generator` (opcional): proveniência, para auditoria. Não são interpretados. No pack importado, `generatedAt` é a hora da consulta mais recente ao Wikidata (vem do cache, não do relógio), e `generator` é a versão do importador.
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
- `generated` (opcional): `true` quando o jogador **não é real** e foi gerado pelo importador para completar o elenco (ver abaixo). Proveniência: o domínio não lê.
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

## Importação do Wikidata (`tools/world-import/`, issue #127)

O conteúdo de `data/world/v1/` (menos `calendar/`) é **gerado** por um script reprodutível. Não edite os JSON à mão: a próxima importação apaga a edição. Corrija a curadoria e rode de novo.

```bash
cd backend
./gradlew worldImport                     # usa o cache; consulta o Wikidata só o que faltar ou mudou
./gradlew worldImport --args=--offline    # só o cache: reprodutível, sem rede
./gradlew worldImport --args=--refresh    # ignora o cache e consulta tudo de novo
```

O importador é um **source set à parte** do Gradle do backend (`worldImport`, código em `tools/world-import/src/`). Ele enxerga as classes do backend (reusa os DTOs do pack, o gerador de nomes da #88 e o `FileWorldDataProvider`), mas não entra no `bootJar`. Os testes dele rodam junto com o `./gradlew test`.

### Entradas

| Arquivo | O quê |
|---|---|
| `data/world/competitions.yaml` | Parâmetros da importação; as competições (formato, desempate, divisão e força, que são regras do jogo); e, por competição, os clubes da temporada com id do pack, nome de exibição e QID do Wikidata. |
| `data/world/club-reputation.yaml` | Reputação 1..100 de cada clube (tabela curada, decisão nossa). Clube sem reputação faz a importação falhar. |
| `data/world/overrides.yaml` | Correções aplicadas **depois** da importação: confederação de país, estádio/capacidade de clube, jogador que mudou de clube (`club`), saiu (`exclude`), posição, nome ou `displayName`. Jogadores são identificados pelo QID. |
| Wikidata (SPARQL) | Rótulos dos clubes, estádio (`P115`) e capacidade (`P1083`), seleções (trigrama FIFA `P3441`, confederação, nome em português, ISO) e jogadores (`P54` clube, `P1532` país esportivo, `P27` cidadania, `P569` nascimento, `P413` posição). |

**Por que a lista de clubes é curada:** os itens de temporada do Wikidata quase nunca têm os participantes (`P1923`), e o `P118` (liga) dos clubes mistura temporadas antigas, jogadores e até partidas. A lista de cada competição foi conferida no artigo da temporada na Wikipédia (campo `reference`) e amarrada ao QID do clube. Temporadas: 2026 no Brasil e na Libertadores (só a **fase de grupos**, 32 clubes), 2026/27 na Europa.

### Regras da importação

- **Vínculo atual** (`Queries.players`). O Wikidata guarda muito vínculo antigo sem data de fim. Por isso conta como atual o `P54` que (1) não tem data de fim nem rank depreciado; (2) **tem** data de início, a partir de `minMembershipStart` (2019-01-01); (3) não tem outro vínculo de clube com início posterior (seleções não contam: têm trigrama FIFA, clubes não); (4) é de um humano vivo, nascido a partir de `minBirthDate` (1984), e não do sexo feminino. Se o jogador sobra em dois clubes do pack, vale o vínculo mais recente.
- **Nacionalidade**: o país esportivo (`P1532`) primeiro, a cidadania (`P27`) como reserva; a segunda nacionalidade é qualquer outra das duas. Um país do Wikidata vira trigrama pela seleção que o tem como `P1532` (Inglaterra → `ENG`). O Reino Unido aponta para quatro seleções, então é ambíguo e não vira país.
- **Posição**: as posições do Wikidata descendem de quatro raízes (goleiro, defensor, meio-campista, atacante). Vence a raiz mais citada.
- **Descartes**: jogador sem posição, sem nacionalidade mapeável, sem nascimento ou sem nome em alfabeto latino fica de fora. O log da importação conta cada motivo.
- **Estádio**: o `P115` sem data de fim, com rank preferido primeiro e depois o início mais recente. Capacidade abaixo de 1.000 é tratada como erro de cadastro.
- **Ids**: o do clube é curado. O do jogador vem do nome (`agustin-rossi`); homônimos ganham o ano de nascimento e, se ainda empatarem, o QID.
- **Elencos curtos** (`SquadFiller`): completados com jogadores **gerados** até `minSquadSize` (22) e `minGoalkeepers` (2), seguindo o elenco-modelo 3 GK / 7 DF / 7 MF / 5 FW. O nome é plausível pelo país do clube: o Brasil reusa o gerador da #88, e os outros países usam listas próprias no mesmo esquema. A idade vai de 18 a 32 anos na `referenceDate`, e a nacionalidade é a do clube. O `Random` de cada clube nasce de `seed + id.hashCode()`, o que torna o resultado estável. O gerado tem `"generated": true` e id `{clube}-gen-NN`.
- Se faltar curadoria (clube sem reputação, país sem confederação, clube sem estádio com capacidade), a importação **falha listando tudo** e não escreve nada. No fim, o pack escrito é carregado pelo `FileWorldDataProvider`; se não for válido, o comando falha.

### Cache e determinismo

Cada consulta tem seu arquivo em `tools/world-import/cache/{nome}.json`, com o texto da consulta, a data e as linhas **já ordenadas**. O cache é commitado (~0,7 MB). Ele é a "resposta congelada" que torna a importação reprodutível. Uma consulta cujo texto mudou é refeita, e no `--offline` isso é erro. Todo desempate do assembler é explícito (nada depende da ordem das linhas do endpoint), e o JSON sai com ordem de campos fixa. O teste `GeneratedWorldPackTest` reimporta do cache num diretório temporário e compara **byte a byte** com o pack commitado.

Política de uso do endpoint: User-Agent genérico do projeto (`GoalFather-world-import/1.0 (open-source study project)`), sem dado pessoal; poucas consultas em lote (13, sendo uma de jogadores por competição); pausa de 2 s entre consultas; e respeito ao `Retry-After` em 429/503.

### Atualizar o pack

1. Mudou a temporada ou um clube subiu/caiu: edite `competitions.yaml` (id, nome, QID) e `club-reputation.yaml`.
2. Rode `./gradlew worldImport --args=--refresh` e leia o log: descartes, avisos e erros de curadoria.
3. Corrija o que faltar em `overrides.yaml` e rode `./gradlew worldImport` (agora do cache).
4. Revise o diff de `data/world/v1/` e do cache e commite os dois juntos.

## Estado atual e próximos passos

- O bean `WorldDataProvider` está registrado, mas **o seed do jogo ainda é o `InitialLeague`**. A troca é da #131. A carga é preguiçosa: sem o pack no disco (por exemplo, na imagem Docker, que não copia `data/`), a aplicação sobe normalmente, e só o `load()` devolve `MissingResource`.
- `data/world/v1/` tem o pack importado do Wikidata: as 7 ligas do MVP (Brasileirão A e B 2026; Premier League, La Liga, Serie A, Bundesliga e Ligue 1 2026/27) e a fase de grupos da Libertadores 2026. São 162 clubes, com 1.329 jogadores reais e 2.258 gerados (3.587 no total, 2,1 MB de JSON). O Wikidata cobre bem os grandes clubes europeus, mas mal o futebol sul-americano, e os elencos brasileiros vêm, na maioria, completados por gerados. A Copa do Brasil ainda não está no pack (a maior parte dos participantes é de fora das Séries A e B). O teste `FileWorldDataProviderTest` carrega o pack em todo build, e o `GeneratedWorldPackTest` confere os critérios da #127.
- `calendar/2026.json` continua mantido à mão; o importador não toca nele.
- O catálogo `Countries` em `domain/model/Country.kt` continua sendo o interino da #123, usado pela persistência. A substituição dele pelos países do pack acontece com o seed (#131).
