package com.carlesso.goalfather.tools.worldimport.wikidata

import java.time.Instant
import java.time.LocalDate

/*
 * Os fatos crus do Wikidata que o importador usa, já agrupados por item mas
 * SEM decisão nenhuma (qual nome, qual estádio, qual nacionalidade): escolher
 * é trabalho do `PackAssembler`, que é puro e testável sem rede.
 */

/** Tudo o que veio do Wikidata numa execução. */
data class WikidataSnapshot(
    val countries: List<CountryFacts>,
    /** QID de posição → raízes (`GK`, `DF`, `MF`, `FW`) de que ela descende. */
    val positionRoots: Map<String, Set<String>>,
    val clubs: Map<String, ClubFacts>,
    /** Vínculos atuais (regra de [Queries.players]) com os clubes do pack. */
    val memberships: List<PlayerFacts>,
    /** Jogadores citados nos overrides, buscados pelo QID. */
    val playersById: Map<String, PlayerFacts>,
    /** Quando a resposta mais recente foi obtida (vai para o envelope). */
    val retrievedAt: Instant,
)

/** Uma seleção masculina: trigrama FIFA e os itens de país que apontam para ela. */
data class CountryFacts(
    val code: String,
    /** `P1532` da seleção (país para o esporte: Inglaterra). */
    val sportCountry: String?,
    /** `P17` da seleção (país: às vezes Reino Unido). */
    val country: String?,
    val confederation: String?,
    val isoAlpha2: String?,
    val isoSubdivision: String?,
    val namePt: String?,
)

data class ClubFacts(
    val qid: String,
    /** Rótulo por idioma. */
    val labels: Map<String, String>,
    val venues: List<VenueClaim>,
)

/** Um `P115` (estádio) do clube, com o que serve para escolher o atual. */
data class VenueClaim(
    val qid: String,
    val preferred: Boolean,
    val start: String?,
    val end: String?,
    val labels: Map<String, String>,
    val capacities: List<CapacityClaim>,
)

data class CapacityClaim(val value: Int, val preferred: Boolean, val pointInTime: String?)

/** Um jogador e (quando veio da consulta de vínculos) o clube atual. */
data class PlayerFacts(
    val qid: String,
    val clubQid: String?,
    val membershipStart: String?,
    val birthDate: LocalDate?,
    val sportCountries: List<String>,
    val citizenships: List<String>,
    val positions: List<String>,
    val labels: Map<String, String>,
)
