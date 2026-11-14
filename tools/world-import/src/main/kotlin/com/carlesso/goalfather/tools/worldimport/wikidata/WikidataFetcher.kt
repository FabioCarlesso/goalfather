package com.carlesso.goalfather.tools.worldimport.wikidata

import java.time.LocalDate

/**
 * Roda as consultas de [Queries] pelo [SparqlClient] e agrupa as linhas em
 * [WikidataSnapshot]. São ~8 consultas de jogadores (uma por competição) e
 * mais 5 pequenas; com o cache cheio, nenhuma vai à rede.
 *
 * @param clubsByCompetition id da competição → QIDs dos clubes que ela DEFINE
 *   (referências não entram: o clube já é buscado na competição de origem).
 */
class WikidataFetcher(private val client: SparqlClient) {

    fun fetch(
        clubsByCompetition: Map<String, List<String>>,
        extraPlayers: Set<String>,
        minMembershipStart: LocalDate,
        minBirthDate: LocalDate,
    ): WikidataSnapshot {
        val allClubs = clubsByCompetition.values.flatten().toSortedSet()

        val countries = client.select("countries", Queries.countries()).map(::countryFacts)
        // `groupBy` + `mapValues` monta o "multimapa" posição → raízes sem laço manual.
        val positionRoots = client.select("positions", Queries.positions())
            .groupBy({ it.getValue("position") }, { Queries.POSITION_ROOTS.getValue(it.getValue("root")) })
            .mapValues { (_, roots) -> roots.toSortedSet() }

        val clubNames = client.select("club-names", Queries.clubNames(allClubs)).groupBy { it.getValue("club") }
        val venueRows = client.select("club-venues", Queries.venues(allClubs)).groupBy { it.getValue("club") }
        val venueQids = venueRows.values.flatten().map { it.getValue("venue") }.toSortedSet()
        val venueFacts = client.select("venue-facts", Queries.venueFacts(venueQids)).groupBy { it.getValue("venue") }

        val clubs = allClubs.associateWith { qid ->
            val names = clubNames[qid].orEmpty()
            ClubFacts(
                qid = qid,
                labels = names.associate { it.getValue("lang") to it.getValue("text") },
                venues = venueRows[qid].orEmpty().map { venueClaim(it, venueFacts[it.getValue("venue")].orEmpty()) },
            )
        }

        // Uma consulta por competição: lotes de ~20 clubes, bem abaixo do
        // tempo-limite de 60 s do endpoint. O nome do cache é o id da competição.
        val memberships = clubsByCompetition.flatMap { (competition, clubQids) ->
            if (clubQids.isEmpty()) {
                emptyList()
            } else {
                client.select("players-$competition", Queries.players(clubQids, minMembershipStart, minBirthDate))
                    .map(::playerFacts)
            }
        }

        val playersById = if (extraPlayers.isEmpty()) {
            emptyMap()
        } else {
            client.select("players-by-id", Queries.playersById(extraPlayers)).map(::playerFacts).associateBy { it.qid }
        }

        return WikidataSnapshot(
            countries = countries,
            positionRoots = positionRoots,
            clubs = clubs,
            memberships = memberships,
            playersById = playersById,
            retrievedAt = client.retrievals.max(),
        )
    }

    private fun countryFacts(row: Row) = CountryFacts(
        code = row.getValue("fifa"),
        sportCountry = row["sport"],
        country = row["country"],
        confederation = row["conf"]?.let(Queries.CONFEDERATIONS::get),
        isoAlpha2 = row["iso2"],
        isoSubdivision = row["iso32"],
        namePt = row["ptName"],
    )

    private fun venueClaim(row: Row, facts: List<Row>) = VenueClaim(
        qid = row.getValue("venue"),
        preferred = row["rank"] == "PreferredRank",
        start = row["start"],
        end = row["end"],
        labels = facts.filter { it["kind"] == "label" }.associate { it.getValue("lang") to it.getValue("text") },
        capacities = facts.filter { it["kind"] == "capacity" }.mapNotNull { capacity ->
            // Capacidade vem como decimal ("78838" ou "+78838"); valor estranho é descartado.
            capacity.getValue("text").removePrefix("+").toDoubleOrNull()?.toInt()?.let {
                CapacityClaim(it, capacity["rank"] == "PreferredRank", capacity["time"])
            }
        },
    )

    private fun playerFacts(row: Row) = PlayerFacts(
        qid = row.getValue("player"),
        clubQid = row["club"],
        membershipStart = row["start"],
        // Datas do Wikidata vêm como "1995-08-21T00:00:00Z"; só a parte da data interessa.
        // Datas com precisão de ano/século ou fora do formato viram `null` (o assembler descarta).
        birthDate = row["birth"]?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        sportCountries = row.words("sports"),
        citizenships = row.words("citizenships"),
        positions = row.words("positions"),
        labels = row["labels"].orEmpty().split('\t').filter { ':' in it }
            .associate { it.substringBefore(':') to it.substringAfter(':') },
    )

    /** Valor de `GROUP_CONCAT` separado por espaço → lista (vazia se a variável não veio). */
    private fun Row.words(key: String): List<String> = this[key].orEmpty().split(' ').filter { it.isNotBlank() }.sorted()
}
