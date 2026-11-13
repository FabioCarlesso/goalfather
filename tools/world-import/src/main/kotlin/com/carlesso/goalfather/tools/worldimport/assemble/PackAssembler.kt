package com.carlesso.goalfather.tools.worldimport.assemble

import com.carlesso.goalfather.adapter.out.worlddata.dto.ClubDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.CompetitionDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.CountryDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.PlayerDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.StadiumDto
import com.carlesso.goalfather.tools.worldimport.config.ClubSource
import com.carlesso.goalfather.tools.worldimport.config.CompetitionSource
import com.carlesso.goalfather.tools.worldimport.config.CuratedInputs
import com.carlesso.goalfather.tools.worldimport.wikidata.CapacityClaim
import com.carlesso.goalfather.tools.worldimport.wikidata.ClubFacts
import com.carlesso.goalfather.tools.worldimport.wikidata.PlayerFacts
import com.carlesso.goalfather.tools.worldimport.wikidata.Queries
import com.carlesso.goalfather.tools.worldimport.wikidata.VenueClaim
import com.carlesso.goalfather.tools.worldimport.wikidata.WikidataSnapshot
import java.time.LocalDate

/** O pack pronto para ser escrito: um campo por "endpoint" do layout v1. */
data class GeneratedPack(
    val countries: List<CountryDto>,
    val competitions: List<CompetitionDto>,
    /** Id da competição → clubes, na ordem do `competitions.yaml`. */
    val clubsByCompetition: Map<String, List<ClubDto>>,
    /** Id do clube → elenco (reais e gerados). */
    val squads: Map<String, List<PlayerDto>>,
)

/** Números e avisos da importação, para o log e a revisão humana. */
data class ImportReport(
    val clubs: Int,
    val realPlayers: Int,
    val generatedPlayers: Int,
    /** Motivo → quantos vínculos do Wikidata ficaram de fora. */
    val dropped: Map<String, Int>,
    val warnings: List<String>,
)

/**
 * Resultado da montagem: erro como VALOR. `Failed` lista TODOS os problemas
 * que impedem gerar um pack válido (clube sem estádio, sem reputação...), para
 * a curadoria corrigir de uma vez.
 */
sealed interface AssemblyResult {
    data class Assembled(val pack: GeneratedPack, val report: ImportReport) : AssemblyResult

    data class Failed(val errors: List<String>) : AssemblyResult
}

/**
 * Junta os fatos do Wikidata com a curadoria e decide o pack (issue #127).
 * PURO: sem rede, sem disco, sem relógio — mesma entrada, mesma saída, o que
 * é a base do critério "duas execuções geram arquivos idênticos".
 *
 * Toda escolha entre valores concorrentes (vários nomes, estádios,
 * nacionalidades) tem desempate explícito e estável; nada depende da ordem em
 * que o endpoint devolveu as linhas.
 */
class PackAssembler(private val inputs: CuratedInputs, private val snapshot: WikidataSnapshot) {

    private val settings = inputs.competitions.import
    private val overrides = inputs.overrides
    private val errors = mutableListOf<String>()
    private val warnings = mutableListOf<String>()
    private val dropped = sortedMapOf<String, Int>()

    /** Clubes DEFINIDOS (com QID), na ordem do arquivo. */
    private val definedClubs: List<DefinedClub> = inputs.competitions.competitions.flatMap { source ->
        source.clubs.filterNot { it.isReference }.map { club -> DefinedClub(source, club) }
    }
    private val clubIdByQid: Map<String, String> = definedClubs.associate { it.qid to it.id }
    private val countryByQid: Map<String, String> = countryIndex()

    private data class DefinedClub(val competition: CompetitionSource, val club: ClubSource) {
        val id: String get() = club.id
        val qid: String get() = requireNotNull(club.wikidata)
        val country: String
            get() = club.country ?: competition.competition.country
                ?: error("clube ${club.id} sem country numa competição continental")
    }

    fun assemble(): AssemblyResult {
        checkCuration()
        val real = realPlayers()
        val realNames = real.values.flatten().map { it.name }.toSet()
        val filler = SquadFiller(
            seed = settings.seed,
            referenceDate = LocalDate.parse(settings.referenceDate),
            minSize = settings.minSquadSize,
            minGoalkeepers = settings.minGoalkeepers,
        )

        val squads = definedClubs.sortedBy { it.id }.associate { defined ->
            val realSquad = real[defined.id].orEmpty()
            val generated = filler.fill(defined.id, defined.country, realSquad, realNames)
            defined.id to (realSquad + generated).sortedWith(SQUAD_ORDER)
        }
        val clubsByCompetition = inputs.competitions.competitions.associate { source ->
            source.competition.id to source.clubs.map { club ->
                if (club.isReference) ClubDto(id = club.id) else clubDto(DefinedClub(source, club))
            }
        }
        val countries = countries(clubsByCompetition, squads)

        if (errors.isNotEmpty()) return AssemblyResult.Failed(errors.toList())
        val players = squads.values.flatten()
        return AssemblyResult.Assembled(
            pack = GeneratedPack(
                countries = countries,
                competitions = inputs.competitions.competitions.map { it.competition },
                clubsByCompetition = clubsByCompetition,
                squads = squads,
            ),
            report = ImportReport(
                clubs = definedClubs.size,
                realPlayers = players.count { it.generated != true },
                generatedPlayers = players.count { it.generated == true },
                dropped = dropped.toMap(),
                warnings = warnings.toList(),
            ),
        )
    }

    // ── Curadoria ─────────────────────────────────────────────────────────

    private fun checkCuration() {
        definedClubs.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            errors += "clube '$it' definido em mais de uma competição (use referência só com id)"
        }
        definedClubs.filter { it.club.id !in inputs.reputation }.forEach {
            errors += "clube '${it.id}' sem reputação em club-reputation.yaml"
        }
        (inputs.reputation.keys - definedClubs.map { it.id }.toSet()).forEach {
            warnings += "club-reputation.yaml cita '$it', que não está no pack"
        }
        (overrides.clubs.keys - definedClubs.map { it.id }.toSet()).forEach {
            errors += "overrides.yaml corrige o clube '$it', que não está no pack"
        }
    }

    // ── Países ────────────────────────────────────────────────────────────

    /**
     * QID de país → trigrama FIFA. Primeiro o "país para o esporte" das
     * seleções (Inglaterra → ENG), depois o país (`P17`) quando ele aponta
     * para UMA seleção só (o Reino Unido aponta para quatro e fica de fora:
     * ambíguo). Por fim, os apelidos curados no `overrides.yaml`.
     */
    private fun countryIndex(): Map<String, String> {
        // `mapNotNull` + `let` monta os pares só onde o QID existe — sem `!!`.
        fun unambiguous(pairs: List<Pair<String, String>>): Map<String, String> =
            pairs.groupBy({ it.first }, { it.second })
                .filterValues { it.toSet().size == 1 }
                .mapValues { (_, codes) -> codes.first() }
        val bySport = unambiguous(snapshot.countries.mapNotNull { c -> c.sportCountry?.let { it to c.code } })
        val byCountry = unambiguous(snapshot.countries.mapNotNull { c -> c.country?.let { it to c.code } })
            .filterKeys { it !in bySport }
        val aliases = overrides.countries.flatMap { (code, override) -> override.wikidata.map { it to code } }
        return byCountry + bySport + aliases
    }

    private fun countries(clubs: Map<String, List<ClubDto>>, squads: Map<String, List<PlayerDto>>): List<CountryDto> {
        val used = buildSet {
            inputs.competitions.competitions.mapNotNullTo(this) { it.competition.country }
            clubs.values.flatten().mapNotNullTo(this) { it.country }
            squads.values.flatten().forEach { player ->
                add(player.nationality)
                player.secondNationality?.let(::add)
            }
        }
        val factsByCode = snapshot.countries.groupBy { it.code }
        return used.sorted().mapNotNull { code ->
            val facts = factsByCode[code].orEmpty()
            val override = overrides.countries[code]
            // `firstNotNullOfOrNull`: o primeiro valor não-nulo entre as linhas
            // (a mesma seleção pode vir em várias linhas, uma por ISO/rótulo).
            val confederation = override?.confederation ?: facts.mapNotNull { it.confederation }.minOrNull()
            val name = override?.name ?: facts.mapNotNull { it.namePt }.minOrNull()
            if (confederation == null || name == null) {
                errors += "país '$code' sem ${if (name == null) "nome" else "confederação"}: complete em overrides.yaml"
                return@mapNotNull null
            }
            CountryDto(
                code = code,
                name = name,
                confederation = confederation,
                isoAlpha2 = override?.isoAlpha2
                    ?: facts.mapNotNull { it.isoAlpha2 }.minOrNull()
                    ?: facts.mapNotNull { it.isoSubdivision }.minOrNull()
                    ?: "",
            )
        }
    }

    // ── Clubes ────────────────────────────────────────────────────────────

    private fun clubDto(defined: DefinedClub): ClubDto {
        val facts = snapshot.clubs[defined.qid]
        val override = overrides.clubs[defined.id]
        val languages = languagesFor(defined.country)
        val name = override?.name ?: defined.club.name ?: facts?.let { clubName(it, languages) }
        val stadium = override?.stadium ?: facts?.let { stadium(it, languages) }
        if (name == null) errors += "clube '${defined.id}' (${defined.qid}) sem nome no Wikidata: use overrides.yaml"
        if (stadium == null) {
            errors += "clube '${defined.id}' (${defined.qid}) sem estádio com capacidade no Wikidata: use overrides.yaml"
        }
        return ClubDto(
            id = defined.id,
            name = name,
            country = defined.country,
            reputation = inputs.reputation[defined.id],
            stadium = stadium,
        )
    }

    /** Sem nome curado, o rótulo do Wikidata no idioma do país (nome oficial). */
    private fun clubName(facts: ClubFacts, languages: List<String>): String? =
        languages.firstNotNullOfOrNull { facts.labels[it] }

    /**
     * Estádio atual: `P115` sem data de fim; rank preferido primeiro, depois o
     * de início mais recente, depois o menor QID. Capacidade: preferida, mais
     * recente (`P585`), maior. Capacidade abaixo de [MIN_CAPACITY] é erro de
     * cadastro (já veio "60" e "22") e é ignorada — sem outra, o clube cai no
     * erro de curadoria e pede override.
     */
    private fun stadium(facts: ClubFacts, languages: List<String>): StadiumDto? {
        val venue = facts.venues.filter { it.end == null }
            .map { venue -> venue.copy(capacities = venue.capacities.filter { it.value >= MIN_CAPACITY }) }
            .sortedWith(
                compareByDescending<VenueClaim> { it.preferred }
                    .thenByDescending { it.start.orEmpty() }
                    .thenBy { it.qid.numeric() },
            )
            .firstOrNull { venue -> venue.capacities.isNotEmpty() && languages.any { it in venue.labels } }
            ?: return null
        val capacity = venue.capacities
            .sortedWith(
                compareByDescending<CapacityClaim> { it.preferred }
                    .thenByDescending { it.pointInTime.orEmpty() }
                    .thenByDescending { it.value },
            )
            .first().value
        val name = languages.firstNotNullOf { venue.labels[it] }
        return StadiumDto(name = name, capacity = capacity)
    }

    // ── Jogadores ─────────────────────────────────────────────────────────

    /** Elencos REAIS por id de clube, já com overrides e ids únicos. */
    private fun realPlayers(): Map<String, List<PlayerDto>> {
        val current = currentMemberships()
        val drafts = current.mapNotNull { (facts, clubId) -> draft(facts, clubId) }
        return withUniqueIds(drafts)
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, squad) -> squad.sortedWith(SQUAD_ORDER) }
    }

    /**
     * Um clube por jogador. Se o Wikidata deixa o jogador "atual" em dois
     * clubes do pack, vale o vínculo mais recente (empate: menor id de clube).
     * Depois entram os overrides: `exclude` tira, `club` põe/move.
     */
    private fun currentMemberships(): List<Pair<PlayerFacts, String>> {
        val byPlayer = snapshot.memberships
            .mapNotNull { facts -> facts.clubQid?.let(clubIdByQid::get)?.let { facts to it } }
            .groupBy { it.first.qid }
            .mapValues { (qid, claims) ->
                val chosen = claims.sortedWith(
                    compareByDescending<Pair<PlayerFacts, String>> { it.first.membershipStart.orEmpty() }.thenBy { it.second },
                ).first()
                if (claims.map { it.second }.toSet().size > 1) {
                    warnings += "$qid atual em ${claims.map { it.second }.sorted()}; ficou em ${chosen.second}"
                }
                chosen
            }
            .toMutableMap()

        overrides.players.forEach { (qid, override) ->
            when {
                override.exclude -> byPlayer.remove(qid)
                override.club != null -> {
                    if (override.club !in clubIdByQid.values) {
                        errors += "overrides.yaml põe $qid no clube '${override.club}', que não está no pack"
                        return@forEach
                    }
                    val facts = byPlayer[qid]?.first ?: snapshot.playersById[qid]
                    if (facts == null) errors += "overrides.yaml cita $qid, mas não há fatos dele no Wikidata"
                    else byPlayer[qid] = facts to override.club
                }
            }
        }
        // Ordem por QID: o resto do pipeline nunca depende da ordem de um Map.
        return byPlayer.toSortedMap(compareBy { it.numeric() }).values.toList()
    }

    /** Fatos crus → jogador do pack, ou `null` (com o motivo contado) se falta algo essencial. */
    private fun draft(facts: PlayerFacts, clubId: String): Pair<String, PlayerDto>? {
        val override = overrides.players[facts.qid]
        val (sportNationality, second) = nationalities(facts)
        // `?: return skip(...)`: o elvis com `return` sai cedo e, dali em
        // diante, o compilador sabe que o valor não é nulo (smart cast) — sem `!!`.
        val nationality = override?.nationality ?: sportNationality
            ?: return skip("nacionalidade sem seleção conhecida")
        val name = override?.name ?: playerName(facts, nationality) ?: return skip("sem nome em alfabeto latino")
        val birthDate = override?.birthDate ?: facts.birthDate?.toString() ?: return skip("sem data de nascimento")
        val position = override?.position ?: position(facts) ?: return skip("sem posição")
        return clubId to PlayerDto(
            // Provisório: o id definitivo sai de `withUniqueIds`, que vê o pack inteiro.
            id = facts.qid,
            name = name,
            displayName = override?.displayName,
            birthDate = birthDate,
            nationality = nationality,
            secondNationality = second?.takeIf { it != nationality },
            position = position,
        )
    }

    /** Conta o descarte pelo motivo e devolve `null` (para o `?: return skip(...)`). */
    private fun skip(reason: String): Nothing? {
        dropped.merge(reason, 1, Int::plus)
        return null
    }

    /**
     * Nacionalidade "esportiva" (`P1532`) primeiro; sem ela, a cidadania
     * (`P27`). A segunda é qualquer outra das duas listas. Ordem por QID numérico.
     */
    private fun nationalities(facts: PlayerFacts): Pair<String?, String?> {
        fun codes(qids: List<String>) = qids.sortedBy { it.numeric() }.mapNotNull(countryByQid::get).distinct()
        val sport = codes(facts.sportCountries)
        val all = (sport + codes(facts.citizenships)).distinct()
        val primary = sport.firstOrNull() ?: all.firstOrNull()
        return primary to all.firstOrNull { it != primary }
    }

    /** Rótulo multilíngue/inglês primeiro (o nome "de futebol"), depois o idioma do país. */
    private fun playerName(facts: PlayerFacts, nationality: String?): String? {
        val languages = (listOf("mul", "en") + languagesFor(nationality.orEmpty())).distinct()
        return languages.firstNotNullOfOrNull { facts.labels[it] }?.trim()?.takeIf { it.isNotEmpty() && it.slug().isNotEmpty() }
    }

    /**
     * Posição do jogo pelas raízes das posições do Wikidata ("lateral-direito"
     * descende de "defensor"). Vence a raiz mais citada; empate na ordem GK,
     * DF, MF, FW.
     */
    private fun position(facts: PlayerFacts): String? {
        val roots = facts.positions.flatMap { snapshot.positionRoots[it].orEmpty() }
        if (roots.isEmpty()) return null
        val order = Queries.POSITION_ROOTS.values.toList()
        return roots.groupingBy { it }.eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { order.indexOf(it.key) })
            .first().key
    }

    /**
     * Id do pack a partir do nome. Homônimos ganham o ano de nascimento e, se
     * ainda empatarem, o QID — todos os do grupo, para nenhum "ganhar" o id
     * limpo por acaso de ordem.
     */
    private fun withUniqueIds(drafts: List<Pair<String, PlayerDto>>): List<Pair<String, PlayerDto>> {
        val bySlug = drafts.groupBy { it.second.name.slug() }
        return bySlug.flatMap { (slug, group) ->
            if (group.size == 1) {
                group.map { (club, player) -> club to player.copy(id = slug) }
            } else {
                val byYear = group.groupBy { "$slug-${it.second.birthDate.take(4)}" }
                byYear.flatMap { (withYear, sameYear) ->
                    sameYear.map { (club, player) ->
                        val id = if (sameYear.size == 1) withYear else "$withYear-${player.id.lowercase()}"
                        club to player.copy(id = id)
                    }
                }
            }
        }
    }

    companion object {
        /** Goleiros, defensores, meias, atacantes; reais antes dos gerados; nome; id. */
        private val POSITION_ORDER = listOf("GK", "DF", "MF", "FW")

        /** Menor capacidade aceita como real; abaixo disso é erro de digitação no Wikidata. */
        const val MIN_CAPACITY = 1_000
        val SQUAD_ORDER: Comparator<PlayerDto> =
            compareBy<PlayerDto> { POSITION_ORDER.indexOf(it.position) }
                .thenBy { it.generated == true }
                .thenBy { it.name }
                .thenBy { it.id }

        /** Idiomas para nomes de clube/estádio: o do país primeiro, depois os demais. */
        fun languagesFor(countryCode: String): List<String> {
            val native = when (countryCode) {
                "BRA", "POR" -> "pt"
                "ESP", "ARG", "URU", "PAR", "CHI", "COL", "ECU", "PER", "BOL", "VEN", "MEX" -> "es"
                "ITA" -> "it"
                "GER", "AUT", "SUI" -> "de"
                "FRA", "BEL" -> "fr"
                else -> "en"
            }
            return (listOf(native) + Queries.LABEL_LANGUAGES).distinct()
        }

        /** `Q17479` → 17479, para ordenar QIDs como números e não como texto. */
        private fun String.numeric(): Long = drop(1).toLongOrNull() ?: Long.MAX_VALUE
    }
}
