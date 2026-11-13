package com.carlesso.goalfather.tools.worldimport.wikidata

import java.time.LocalDate

/**
 * As consultas SPARQL do importador (issue #127). Poucas e em lote: uma por
 * tipo de dado, e a de jogadores uma por competição (um `VALUES` com todos os
 * clubes dela).
 *
 * São `raw strings` (`"""..."""`) com template: o `$` interpola valores do
 * Kotlin e `${'$'}` seria o cifrão literal (não usado aqui — SPARQL usa `?`).
 * `trimIndent()` tira a indentação comum para a consulta guardada no cache
 * ficar limpa.
 */
object Queries {

    /** Idiomas aceitos para nomes (rótulos), do mais ao menos preferido no geral. */
    val LABEL_LANGUAGES: List<String> = listOf("mul", "en", "pt", "es", "it", "de", "fr")

    /** Goleiro, defensor, meio-campista e atacante: as quatro raízes de posição do Wikidata. */
    val POSITION_ROOTS: Map<String, String> = linkedMapOf(
        "Q201330" to "GK",
        "Q336286" to "DF",
        "Q193592" to "MF",
        "Q280658" to "FW",
    )

    /** CONMEBOL, UEFA, CONCACAF, CAF, AFC e OFC no Wikidata. */
    val CONFEDERATIONS: Map<String, String> = linkedMapOf(
        "Q58733" to "CONMEBOL",
        "Q35572" to "UEFA",
        "Q160549" to "CONCACAF",
        "Q168360" to "CAF",
        "Q83276" to "AFC",
        "Q180344" to "OFC",
    )

    private val languageFilter = LABEL_LANGUAGES.joinToString(", ") { "\"$it\"" }

    private fun values(qids: Collection<String>): String = qids.sorted().joinToString(" ") { "wd:$it" }

    /**
     * Países do futebol: cada seleção masculina principal tem o trigrama FIFA
     * (`P3441`) e o "país para o esporte" (`P1532`, ex.: Inglaterra, não Reino
     * Unido). A confederação vem da federação (mesmo trigrama, `P463` membro de).
     * `?place` é o país da seleção, de onde saem o nome em português e o ISO.
     */
    fun countries(): String = """
        SELECT ?fifa ?sport ?country ?conf ?iso2 ?iso32 ?ptName WHERE {
          ?team wdt:P31 wd:Q135408445 ; wdt:P3441 ?fifa .
          OPTIONAL { ?team wdt:P1532 ?sport }
          OPTIONAL { ?team wdt:P17 ?country }
          BIND(COALESCE(?sport, ?country) AS ?place)
          OPTIONAL {
            ?assoc wdt:P3441 ?fifa ; wdt:P31 wd:Q1478443 ; wdt:P463 ?conf .
            VALUES ?conf { ${values(CONFEDERATIONS.keys)} }
          }
          OPTIONAL { ?place wdt:P297 ?iso2 }
          OPTIONAL { ?place wdt:P300 ?iso32 }
          OPTIONAL { ?place rdfs:label ?ptName FILTER(LANG(?ptName) = "pt") }
        }
    """.trimIndent()

    /** Toda posição que é subclasse (direta ou não) de uma das quatro raízes. */
    fun positions(): String = """
        SELECT ?position ?root WHERE {
          VALUES ?root { ${values(POSITION_ROOTS.keys)} }
          ?position wdt:P279* ?root .
        }
    """.trimIndent()

    /** Rótulos dos clubes (o nome oficial; o de exibição é curado em `competitions.yaml`). */
    fun clubNames(clubs: Collection<String>): String = """
        SELECT ?club ?lang ?text WHERE {
          VALUES ?club { ${values(clubs)} }
          ?club rdfs:label ?text .
          BIND(LANG(?text) AS ?lang)
          FILTER(?lang IN ($languageFilter))
        }
    """.trimIndent()

    /** Estádio (`P115`, mando de campo) de cada clube, com rank e datas para escolher o atual. */
    fun venues(clubs: Collection<String>): String = """
        SELECT ?club ?venue ?rank ?start ?end WHERE {
          VALUES ?club { ${values(clubs)} }
          ?club p:P115 ?st . ?st ps:P115 ?venue ; wikibase:rank ?rank .
          FILTER(?rank != wikibase:DeprecatedRank)
          OPTIONAL { ?st pq:P580 ?start }
          OPTIONAL { ?st pq:P582 ?end }
        }
    """.trimIndent()

    /** Nome e capacidade (`P1083`, com rank e data de referência) de cada estádio. */
    fun venueFacts(venues: Collection<String>): String = """
        SELECT ?venue ?kind ?lang ?text ?rank ?time WHERE {
          VALUES ?venue { ${values(venues)} }
          {
            ?venue rdfs:label ?text . BIND("label" AS ?kind) BIND(LANG(?text) AS ?lang)
            FILTER(?lang IN ($languageFilter))
          } UNION {
            ?venue p:P1083 ?st . ?st ps:P1083 ?text ; wikibase:rank ?rank . BIND("capacity" AS ?kind)
            FILTER(?rank != wikibase:DeprecatedRank)
            OPTIONAL { ?st pq:P585 ?time }
          }
        }
    """.trimIndent()

    /**
     * Jogadores ATUAIS dos [clubs]. O Wikidata tem muito vínculo antigo sem
     * data de fim, então "atual" aqui é a regra abaixo (documentada em
     * `docs/WORLD_DATA.md`):
     *
     * 1. vínculo `P54` com o clube, sem `P582` (fim) e não depreciado;
     * 2. COM `P580` (início) a partir de [minStart] — vínculo sem data não dá
     *    para distinguir de um antigo esquecido aberto;
     * 3. nenhum OUTRO vínculo de clube com início posterior (seleções ficam
     *    de fora da comparação: elas têm trigrama FIFA, clubes não);
     * 4. humano, vivo, nascido a partir de [minBirth] e não do sexo feminino
     *    (o jogo, por ora, só tem futebol masculino).
     *
     * `GROUP_CONCAT` junta os valores múltiplos (duas nacionalidades, várias
     * posições, rótulos em vários idiomas) numa linha por vínculo, em vez do
     * produto cartesiano que o SPARQL devolveria.
     */
    fun players(clubs: Collection<String>, minStart: LocalDate, minBirth: LocalDate): String = """
        SELECT ?player ?club ?start
          (MIN(?birthDate) AS ?birth)
          (GROUP_CONCAT(DISTINCT STRAFTER(STR(?sport), "entity/"); separator=" ") AS ?sports)
          (GROUP_CONCAT(DISTINCT STRAFTER(STR(?citizen), "entity/"); separator=" ") AS ?citizenships)
          (GROUP_CONCAT(DISTINCT STRAFTER(STR(?pos), "entity/"); separator=" ") AS ?positions)
          (GROUP_CONCAT(DISTINCT CONCAT(LANG(?label), ":", ?label); separator="\t") AS ?labels)
        WHERE {
          VALUES ?club { ${values(clubs)} }
          ?player p:P54 ?st . ?st ps:P54 ?club ; wikibase:rank ?rank ; pq:P580 ?start .
          FILTER(?rank != wikibase:DeprecatedRank)
          FILTER NOT EXISTS { ?st pq:P582 ?end }
          FILTER(?start >= "${minStart}T00:00:00Z"^^xsd:dateTime)
          ?player wdt:P31 wd:Q5 ; wdt:P569 ?birthDate .
          FILTER(?birthDate >= "${minBirth}T00:00:00Z"^^xsd:dateTime)
          FILTER NOT EXISTS { ?player wdt:P570 ?death }
          FILTER NOT EXISTS { ?player wdt:P21 wd:Q6581072 }
          FILTER NOT EXISTS {
            ?player p:P54 ?other . ?other pq:P580 ?otherStart ; ps:P54 ?otherClub .
            FILTER(?otherStart > ?start)
            FILTER NOT EXISTS { ?otherClub wdt:P3441 ?nationalTeamCode }
          }
          OPTIONAL { ?player wdt:P1532 ?sport }
          OPTIONAL { ?player wdt:P27 ?citizen }
          OPTIONAL { ?player wdt:P413 ?pos }
          OPTIONAL { ?player rdfs:label ?label FILTER(LANG(?label) IN ($languageFilter)) }
        }
        GROUP BY ?player ?club ?start
    """.trimIndent()

    /**
     * Fatos de jogadores citados no `overrides.yaml` (os que a regra de
     * "vínculo atual" não pega, mas a curadoria sabe onde jogam). Mesma
     * projeção de [players], sem as regras de vínculo.
     */
    fun playersById(players: Collection<String>): String = """
        SELECT ?player
          (MIN(?birthDate) AS ?birth)
          (GROUP_CONCAT(DISTINCT STRAFTER(STR(?sport), "entity/"); separator=" ") AS ?sports)
          (GROUP_CONCAT(DISTINCT STRAFTER(STR(?citizen), "entity/"); separator=" ") AS ?citizenships)
          (GROUP_CONCAT(DISTINCT STRAFTER(STR(?pos), "entity/"); separator=" ") AS ?positions)
          (GROUP_CONCAT(DISTINCT CONCAT(LANG(?label), ":", ?label); separator="\t") AS ?labels)
        WHERE {
          VALUES ?player { ${values(players)} }
          OPTIONAL { ?player wdt:P569 ?birthDate }
          OPTIONAL { ?player wdt:P1532 ?sport }
          OPTIONAL { ?player wdt:P27 ?citizen }
          OPTIONAL { ?player wdt:P413 ?pos }
          OPTIONAL { ?player rdfs:label ?label FILTER(LANG(?label) IN ($languageFilter)) }
        }
        GROUP BY ?player
    """.trimIndent()
}
