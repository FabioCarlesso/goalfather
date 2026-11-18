# Dados do mundo (pack `data/world/`)

> Issues #126 (provedor), #127 (importação do Wikidata) e #131 (o seed do jogo a partir do pack) · épico #148 (Fase 8).

O mundo do jogo (países, competições, clubes, elencos, estádios, datas FIFA e janelas de transferência) é **dado**, não código. Ele fica no repositório, em `data/world/`, organizado **como se fosse uma API externa**: cada arquivo é a resposta de um "endpoint". O backend lê esse pack por um port (`WorldDataProvider`) e o traduz para o domínio por uma **camada anticorrupção**. Trocar o arquivo por um pack fora do git ou por uma API HTTP de verdade é escrever outro adapter. O domínio não muda.

## Layout

```
data/world/v1/
├── countries.json                    GET /countries
├── competitions.json                 GET /competitions
├── competitions/{id}/clubs.json      GET /competitions/{id}/clubs
├── clubs/{id}/squad.json             GET /clubs/{id}/squad
└── calendar/{year}.json              GET /calendar/{year}

data/world/e2e/v1/                    o mesmo layout, recortado para o profile `e2e` (ver "Pack reduzido do E2E")
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
| `foreignPlayerLimit` | int? | Máximo de estrangeiros em campo por jogo (issue #131). Ausente = sem limite. Brasileirão A/B e Copa do Brasil: `9` (CBF 2026). Ligas europeias: sem limite **no MVP** — as regras reais delas (home-grown na Inglaterra, extracomunitários na Espanha e na Itália) não são "N estrangeiros em campo", e o jogo ainda não modela passaporte comunitário. |
| `pausesOnFifaWindows` | bool | A competição para nas datas FIFA? Default `true` (Europa, copas, continentais). Brasileirão A/B: `false` — a CBF não para a liga. |

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
- O gerador consome o `Random` a cada chamada. Para que um jogador não dependa da ordem dos outros, o seed da #131 dá um `Random` **por jogador**, com a seed derivada do id do pack (ver "Seed do jogo").

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
4. Revise o diff de `data/world/v1/`, de `data/world/e2e/v1/` (o recorte do E2E é refeito na mesma execução) e do cache, e commite tudo junto.

## Estado do pack

- `data/world/v1/` tem o pack importado do Wikidata: as 7 ligas do MVP (Brasileirão A e B 2026; Premier League, La Liga, Serie A, Bundesliga e Ligue 1 2026/27), a Copa do Brasil 2026 recortada (só referências aos 40 clubes de A e B, ver abaixo) e a fase de grupos da Libertadores 2026. São 162 clubes, com 1.329 jogadores reais e 2.258 gerados (3.587 no total, 2,1 MB de JSON). O Wikidata cobre bem os grandes clubes europeus, mas mal o futebol sul-americano, e os elencos brasileiros vêm, na maioria, completados por gerados. O `FileWorldDataProviderTest` carrega o pack em todo build, e o `GeneratedWorldPackTest` confere os critérios da #127.
- `calendar/2026.json` continua mantido à mão; o importador não toca nele.
- `data/world/e2e/v1/` é o recorte do profile `e2e` (16 clubes, ~350 jogadores), gerado na mesma importação.

## Seed do jogo (issue #131)

Com o banco vazio, o jogo nasce do pack: o `DataInitializer` carrega o pack pelo `WorldDataProvider`, passa pelo seed **puro** (`application/seed/WorldSeed.kt`, `WorldPack.toInitialWorld()`), abre a temporada com `startWorld` (o calendário) e grava tudo numa transação só (`SeedWriter`). O seed não conhece Spring nem banco: o `WorldSeedTest` monta o mundo do MVP inteiro a partir do pack real, sem contexto.

```
WorldDataProvider.load() ──► WorldPack ──► toInitialWorld() ──► InitialWorld ──► startWorld() ──► WorldStart
        (adapter)                           (puro, application)                    (puro, domain)
                                                      └──────────── SeedWriter.write(world, start) ────────────┘
```

`NewWorldFactory` (config) guarda o mundo montado em memória (`by lazy`): é imutável e determinístico, então o reset do E2E só regrava, sem reler o disco nem refazer o seed.

### O que entra no save

- **Competições (catálogo do save):** as **nacionais** do pack, na ordem dele. Liga de pontos corridos com `tier` vira `CompetitionKind.League(tier)`, e mata-mata nacional vira `CompetitionKind.NationalCup`. A janela da temporada sai de `SeasonSpan.of(confederação do país)`: ano civil no Brasil e agosto a maio na UEFA. `pausesOnFifaWindows` e `foreignPlayerLimit` vêm do pack. O MVP tem 8 competições: Série A, Série B, Copa do Brasil, Premier League, La Liga, Serie A, Bundesliga e Ligue 1. **A Libertadores fica no pack, mas fora do save** (continental, #132), e com ela os clubes que só aparecem nela. O catálogo vai para a tabela `competition_catalog` (V19), e o pack não é mais lido depois disso: mudar o pack não mexe num jogo em andamento.
- **Clubes:** só os das ligas do catálogo (136). Cada clube entra uma vez, na primeira liga em que aparece. `division` = `tier` da liga, `country` = país do clube. A liga de um clube é a do catálogo para (país, divisão) (`List<GameCompetition>.leagueOf`).
- **Países:** todos os do pack (97), na tabela `countries` (V19). As nacionalidades dos jogadores reais citam países sem liga (Nigéria, Japão...), e o catálogo fixo `Countries` do domínio (16 países, interino da #123) as transformaria em "Brasil". O mapper de persistência resolve o trigrama primeiro nos países do save (`SavedCountries`, recarregado no boot e depois de cada seed) e só depois no catálogo fixo.

### Ids (determinísticos)

| O quê | Regra | Exemplo |
|---|---|---|
| Clube | `1..N` na ordem do pack: liga a liga e, dentro dela, a ordem de `clubs.json` | Série A → 1..20, Série B → 21..40, Premier League → 41..60 |
| Jogador de clube | `clube × 1000 + posição no elenco` (1-based, ordem de `squad.json`) | 3º do clube 9 → 9003 |
| Jogador livre (mercado) | `1..999` (o "clube 0") | 1..16 |

São estáveis entre boots para o **mesmo** pack, mas não entre versões dele: um clube a mais na Série A desloca a numeração. O save não depende disso, porque o pack só é lido para criar um jogo novo. O id do pack (`bra-flamengo`) não é gravado. Nada o consulta depois do seed, e regravar um jogador com o mapper atual (que cria a entidade a partir do domínio) apagaria a coluna. Os ids dos garotos da base (`youthPlayerId`) ficam numa faixa disjunta, a partir de 10¹².

### Jogadores

- **Atributos:** `AttributeGenerator` com a força da liga do clube (`WorldPack.leagueStrengthOf`), a reputação do clube, a posição e a idade. Cada jogador tem o próprio `Random`, com seed `id do pack.hashCode() xor sal`. O `String.hashCode` é definido pela especificação do Java, então a seed não muda entre JVMs. O resultado é que o mesmo jogador tem sempre os mesmos atributos, mesmo que o elenco mude de ordem ou ganhe um jogador.
- **Idade:** completa em **1º de julho** do ano inicial, a mesma `referenceDate` do importador. A idade fica limitada a `Player.AGE_RANGE`.
- **Nome:** `displayName` se houver; senão o nome real.

### Economia inicial (`domain/rules/InitialEconomyRules.kt`)

Tudo em USD (`Money`, centavos), por regra pura, sem `Random`. A escala é a do jogo antes da #131 (craque de 88 ganhava US$ 11 mil por folha; o clube do usuário tinha US$ 160 mil), para mercado, prêmios e ampliação de estádio continuarem coerentes.

| Valor | Regra | Exemplos |
|---|---|---|
| Salário (por folha) | `US$ 1.500 × 2^((overall − 60) / 10)`, arredondado a US$ 10, piso US$ 500 | 60 → 1,5 mil; 70 → 3 mil; 80 → 6 mil; 90 → 12 mil |
| Caixa | `(4 + reputação / 10)` folhas salariais do elenco gerado, arredondado a US$ 1.000 | rep 20 → 6 folhas; rep 50 → 9; rep 90 → 13 |
| Estádio | capacidade **real** do pack (Wikidata + `overrides.yaml`) | Maracanã 78.838 |
| Ingresso | preço justo da curva de demanda (`fairTicketPriceCents`) para a força esperada do clube (`strength` da liga + `(rep − 50) / 5`), × `1 + (rep − 50) / 250` (−20% a +20%), arredondado ao dólar inteiro (o passo do input da UI), dentro de `TICKET_PRICE_RANGE` | liga 70, rep 50 → US$ 9,00 |

Por que a caixa depende da folha: a força da liga já está no overall (o gerador parte dela) e, portanto, na folha. Um clube grande da Premier League e um pequeno da Série B nascem com o mesmo fôlego relativo, e o grande nasce mais rico em dólares. Por que o ingresso parte do preço justo: a IA nunca mexe no ingresso, e um preço longe do justo esvaziaria (ou lotaria de graça) o estádio dela a temporada inteira.

Ressalva: com as capacidades reais (até 99 mil lugares), a bilheteria dos clubes grandes passa com folga a folha salarial. O equilíbrio fino da economia com o mundo real é da #135/#136.

### Mercado inicial

O pack só tem jogadores com clube. Para a tela de mercado não nascer vazia, o seed gera **16 jogadores livres**: 2 goleiros, 5 zagueiros, 5 meias e 4 atacantes. Os nomes vêm do gerador da #88 (brasileiros) e não repetem nomes do pack. A nacionalidade é o país da primeira liga do catálogo (o Brasil no MVP). Os atributos saem do `AttributeGenerator` com a força dessa liga e reputação mediana. A idade vai de 20 a 32 anos, o preço é o de tabela (`marketListingPrice`, 12× o salário) e cada vaga tem seed fixa.

### Copa do Brasil: o corte do MVP

A Copa do Brasil 2026 tem 126 clubes: estaduais, Copa do Nordeste e Copa Verde nas primeiras fases, e a Série A entrando na 5ª fase. O pack só tem as Séries A e B, então a copa do MVP começa **na 4ª fase**, com os 40 clubes do pack:

| Fase | Clubes | Jogos | Entram |
|---|---|---|---|
| Quarta fase | 16 | jogo único | 16 (os 16 mais fracos da Série B) |
| Quinta fase | 32 | ida e volta | 24 (os 20 da Série A + os 4 mais fortes da Série B) |
| Oitavas de final | 16 | ida e volta | — |
| Quartas de final | 8 | ida e volta | — |
| Semifinal | 4 | ida e volta | — |
| Final | 2 | jogo único | — |

O formato e a lista de participantes (os 40, como referências) estão no pack. Quem distribui os clubes entre a 4ª e a 5ª fase, pelo ranking de força, é o motor da copa. Desempate omitido = default. Limite de 9 estrangeiros e pausa nas datas FIFA, como uma copa nacional da CBF.

### Pack reduzido do E2E (`data/world/e2e/v1`)

O reset do E2E (`POST /test-support/reset`, profile `e2e`) recria o mundo a cada teste, e o profile aponta `app.world-data.path` para um **recorte** do pack completo:

- `bra-serie-a`: os 6 clubes de maior reputação da Série A (empate: id);
- `bra-serie-b`: os 6 de maior reputação da Série B;
- `bra-copa-do-brasil`: esses 12 clubes. Na Primeira fase, 8 clubes em jogo único; nas Quartas de final, 8 clubes em ida e volta, 4 deles entrando ali; Semifinal em ida e volta; Final em jogo único;
- `eng-premier-league`: os 4 de maior reputação.

Elencos e calendário são copiados do pack completo; os países são só os citados. Os clubes ficam na ordem do pack completo. O mundo dos mocks do frontend tem a **mesma forma**, e a suíte E2E roda contra os dois. O recorte é **gerado**, não editado à mão: o `./gradlew worldImport` o refaz a partir de `data/world/v1` (`tools/world-import/.../e2e/E2ePack.kt`), e o `E2ePackTest` compara o recorte com o commitado byte a byte. Os testes de integração do backend (`src/test/resources/application.yml`) usam o mesmo pack: cada contexto Spring com banco vazio monta 16 clubes em vez de 136.

### Save antigo

Um banco com clubes mas **sem** catálogo de competições é um save anterior à #131 (o mundo de 12 clubes do `InitialLeague`). Ids, ligas e calendário mudaram e não há migração que faça sentido, então o boot **falha** com uma mensagem dizendo como zerar o banco:

- H2 de desenvolvimento: `rm -rf backend/data/` e `./gradlew bootRun` de novo;
- Postgres do `docker compose`: `docker compose down -v` (apaga o volume) e `up` de novo.

Pelo mesmo motivo saíram os remendos de save antigo do `DataInitializer` (`restartSeasonWithoutMatchday`, da #125, e `backfillBoards`, da #62). Um save anterior à #131 nem passa do boot, e o seed atual já grava o primeiro encaixe e as metas. O `InitialLeague` e a DSL de seed viraram **fixture de teste** (`src/test/.../application/seed/`): uma liga pequena e conhecida para os testes de regra.

Um pack inválido com o banco vazio também derruba o boot, listando **todos** os erros do `PackLoadResult.Invalid`: é erro de configuração do servidor. Com o banco já populado, o pack nem é lido. A imagem Docker copia `data/world/v1` (contexto nomeado `world`, `WORLD_DATA_PATH=/app/world/v1`).

### Tempo de subida

O boot loga o tempo de cada etapa do seed: carga do pack, seed puro e abertura da temporada (`NewWorldFactory`), gravação (`DataInitializer`) e o total. Para medir, apague o banco (`rm -rf backend/data/`), rode `./gradlew bootRun` e procure as linhas `Mundo montado do pack`, `Mundo novo gravado` e `Seed do mundo concluído`, além do `Started GoalfatherApplication in N seconds` do Spring.

**Medido (novembro de 2026)** — pack completo `data/world/v1`, H2 em arquivo vazio, perfil padrão, container Linux de 4 vCPUs / 15 GB, JDK 21:

| Etapa | Tempo |
|---|---|
| Spring até `Started GoalfatherApplication` | 6,5 s |
| Carga do pack (8 competições, 136 clubes, 3.031 jogadores) | 97 ms |
| Seed puro | 36 ms |
| Abertura da temporada (`startWorld`: calendário, tabelas, Copa do Brasil) | 39 ms |
| Gravação no banco | 267 ms |
| **Seed total** | **445 ms** |

O seed é ~7% do boot: o custo é o do Spring, não o do mundo. O pack do E2E (16 clubes, 368 jogadores) sobe em ~6,1 s, com a carga em 61 ms. O plano do calendário do mundo inteiro, recalculado a cada encaixe, sai em ~5 ms (medido pela frente de calendário).

**Escalação automática.** O seed ordena cada elenco para que os 11 primeiros sejam o melhor 4-4-2 (`domain/rules/SquadOrder.kt`, `inStartingOrder`) e só depois numera os ids dos jogadores. A escalação automática (`Club.startingLineup`, a de todo clube da IA) usa os 11 primeiros, e o pack lista os goleiros primeiro; sem a ordem, um clube real entraria em campo com dois ou três goleiros. Os ids saem depois da ordem porque o elenco é relido do banco pelo id.

## Seleções (issue #134)

O pack não traz seleções: elas saem dos **jogadores do save**, pela nacionalidade (`Player.nationality`). Os 97 países do pack ficam no save, mas só os 136 clubes das ligas, então a força de cada seleção depende de quantos jogadores daquela nacionalidade as ligas do MVP têm. No pack completo: Brasil 878, Espanha 374, França 345, Itália 294, Alemanha 290, Inglaterra 264, Argentina 40, Uruguai 18, Colômbia 17, Equador 7, Paraguai 6, Venezuela 5, Chile 2, Peru 1, Bolívia 0.

**Corte:**

- Tem seleção o país com pelo menos **11 jogadores aptos** no mundo (`MIN_PLAYERS_FOR_NATIONAL_TEAM`). No pack completo são 23 países. Abaixo disso ninguém é convocado, e o jogador continua no clube.
- As **10 da CONMEBOL** jogam sempre, porque as Eliminatórias precisam delas. Seleção com menos de 23 convocados completa o time com **reforços estimados**: jogadores gerados na hora, com overall 62 (`ESTIMATED_PLAYER_OVERALL`), id negativo e nunca persistidos. Eles entram na simulação e somem depois; nenhuma lesão ou estatística sai deles. A Bolívia do pack joga só com eles.
- Jogadores livres (mercado) não são convocados.

Quando a #132 trouxer os clubes estrangeiros da Libertadores para o save, Argentina, Uruguai e as demais ganham jogadores de verdade sem mudar nenhuma regra.

**Datas FIFA:** `calendar/2026.json` ainda só tem a janela de março. O jogo usa as interinas de `FifaCalendar.kt` (o padrão FIFA 2026–2030, alinhado à segunda-feira). Quando o pack trouxer o calendário completo, basta o serviço ler as janelas dele em vez de `interimFifaWindows` (`NationalTeamService.windowsOf`).
