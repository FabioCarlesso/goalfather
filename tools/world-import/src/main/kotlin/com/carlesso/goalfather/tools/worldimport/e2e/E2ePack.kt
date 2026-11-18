package com.carlesso.goalfather.tools.worldimport.e2e

import com.carlesso.goalfather.adapter.out.worlddata.WorldPackLoader
import com.carlesso.goalfather.adapter.out.worlddata.dto.CalendarDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.ClubDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.CompetitionDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.CountryDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.EnvelopeDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.FormatDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.KnockoutStageDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.SquadDto
import com.carlesso.goalfather.tools.worldimport.assemble.GeneratedPack
import com.carlesso.goalfather.tools.worldimport.output.PackWriter
import com.carlesso.goalfather.tools.worldimport.output.Provenance
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Pack REDUZIDO do profile `e2e` (issue #131): o reset do E2E recria o mundo
 * a cada teste, e o pack completo (~3.500 jogadores) custaria segundos por
 * reset. Forma FIXADA — o mundo dos mocks do frontend tem a mesma, e a suíte
 * E2E roda contra os dois:
 *
 * - `bra-serie-a`: os 6 clubes de MAIOR reputação da Série A (empate: id);
 * - `bra-serie-b`: os 6 de maior reputação da Série B;
 * - `bra-copa-do-brasil`: esses 12, num formato curto (ver [E2E_CUP_FORMAT]);
 * - `eng-premier-league`: os 4 de maior reputação.
 *
 * Elencos e datas FIFA são COPIADOS do pack completo; países, só os citados.
 * Os clubes ficam na ordem do pack completo (é um recorte, não uma
 * reordenação). Função pura ([build], [e2eCalendar]) + E/S em volta ([write]).
 *
 * **Janelas de transferência (issue #137)** — o único dado do recorte que NÃO
 * é cópia fiel. O mundo começa em 04/01/2026, um dia antes da janela real da
 * CBF (05/01), e todas as janelas europeias de inverno já estão abertas nessa
 * data. A suíte E2E precisa dos dois estados na data inicial, sem jogar dezenas
 * de encaixes: o Brasil (onde estão os fluxos de compra, venda e proposta)
 * com o mercado ABERTO, e a Inglaterra com ele FECHADO (o E2E da compra fora da
 * janela). Por isso, no ano de início, [e2eCalendar]: a 1ª janela de janeiro do
 * [OPEN_AT_START] abre no dia 1º, e a de janeiro do [CLOSED_AT_START] sai. Só
 * as federações com competição no recorte ficam no calendário.
 */
object E2ePack {

    /** Competição → quantos clubes de maior reputação entram. */
    val LEAGUE_CUTS: Map<String, Int> = linkedMapOf(
        "bra-serie-a" to 6,
        "bra-serie-b" to 6,
        "eng-premier-league" to 4,
    )

    const val CUP_ID = "bra-copa-do-brasil"

    /** A copa cujos participantes são os clubes recortados destas ligas. */
    val CUP_LEAGUES: List<String> = listOf("bra-serie-a", "bra-serie-b")

    /**
     * Copa do Brasil do E2E: 8 jogam a 1ª fase (jogo único), 4 entram nas
     * quartas (ida e volta), semifinal ida e volta, final única. 12 clubes.
     */
    val E2E_CUP_FORMAT: FormatDto.Knockout = FormatDto.Knockout(
        listOf(
            KnockoutStageDto(name = "Primeira fase", teams = 8, legs = 1, newEntrants = 8),
            KnockoutStageDto(name = "Quartas de final", teams = 8, legs = 2, newEntrants = 4),
            KnockoutStageDto(name = "Semifinal", teams = 4, legs = 2, newEntrants = 0),
            KnockoutStageDto(name = "Final", teams = 2, legs = 1, newEntrants = 0),
        ),
    )

    /** Federação com o mercado aberto na data inicial do E2E (issue #137). */
    const val OPEN_AT_START = "BRA"

    /** Federação com o mercado fechado na data inicial do E2E (issue #137). */
    const val CLOSED_AT_START = "ENG"

    /**
     * O calendário do recorte (issue #137; ver o KDoc do objeto): datas FIFA
     * iguais; janelas só de [federations], com os dois ajustes do ano de
     * início [startYear]. Puro.
     */
    fun e2eCalendar(calendar: CalendarDto, startYear: Int, federations: Set<String>): CalendarDto {
        val january = "${calendar.year}-01-"
        val windows = calendar.transferWindows
            .filter { it.country in federations }
            .filterNot { calendar.year == startYear && it.country == CLOSED_AT_START && it.start.startsWith(january) }
            .map {
                if (calendar.year == startYear && it.country == OPEN_AT_START && it.start.startsWith(january)) {
                    it.copy(start = "${january}01")
                } else {
                    it
                }
            }
        return calendar.copy(transferWindows = windows)
    }

    /** Recorta [full] na forma do E2E. Puro: mesma entrada, mesma saída. */
    fun build(full: GeneratedPack): GeneratedPack {
        val kept: Map<String, List<ClubDto>> = LEAGUE_CUTS.mapValues { (competition, size) ->
            val clubs = full.clubsByCompetition[competition] ?: error("pack completo sem $competition")
            val top = clubs
                .sortedWith(compareByDescending<ClubDto> { it.reputation ?: 0 }.thenBy { it.id })
                .take(size)
                .mapTo(HashSet()) { it.id }
            clubs.filter { it.id in top }
        }
        val cupClubs = CUP_LEAGUES.flatMap { league -> kept.getValue(league).map { ClubDto(id = it.id) } }
        val clubsByCompetition = LinkedHashMap<String, List<ClubDto>>().apply {
            put("bra-serie-a", kept.getValue("bra-serie-a"))
            put("bra-serie-b", kept.getValue("bra-serie-b"))
            put(CUP_ID, cupClubs)
            put("eng-premier-league", kept.getValue("eng-premier-league"))
        }
        val competitions = clubsByCompetition.keys.map { id ->
            val competition = full.competitions.firstOrNull { it.id == id } ?: error("pack completo sem a competição $id")
            if (id == CUP_ID) competition.copy(format = E2E_CUP_FORMAT) else competition
        }
        val clubIds = kept.values.flatten().map { it.id }
        val squads = clubIds.sorted().associateWith { full.squads[it] ?: error("pack completo sem o elenco de $it") }

        val usedCountries = buildSet {
            competitions.mapNotNullTo(this) { it.country }
            kept.values.flatten().mapNotNullTo(this) { it.country }
            squads.values.flatten().forEach { player ->
                add(player.nationality)
                player.secondNationality?.let { add(it) }
            }
        }
        return GeneratedPack(
            countries = full.countries.filter { it.code in usedCountries },
            competitions = competitions,
            clubsByCompetition = clubsByCompetition,
            squads = squads,
        )
    }

    /**
     * Lê o pack completo em [fullDir], escreve o recorte em [e2eDir] (com o
     * mesmo [PackWriter] do importador, então o JSON sai no mesmo formato) e
     * o calendário do recorte ([e2eCalendar]). A proveniência é a do pack
     * completo, com o recorte anotado em `source`.
     */
    @OptIn(ExperimentalPathApi::class)
    fun write(fullDir: Path, e2eDir: Path) {
        val reader = PackReader(fullDir)
        val (full, provenance) = reader.read()
        val e2e = build(full)
        PackWriter(e2eDir, provenance.copy(source = "${provenance.source}; recorte e2e de data/world/v1 (issue #131)"))
            .write(e2e)

        val calendar = e2eDir.resolve("calendar")
        calendar.deleteRecursively()
        calendar.createDirectories()
        val federations = e2e.competitions.mapNotNullTo(HashSet()) { it.country }
        val files = fullDir.resolve("calendar").listDirectoryEntries("*.json").sorted()
        val envelopes = files.map { it to reader.calendar(it) }
        val startYear = envelopes.minOfOrNull { (_, envelope) -> envelope.data.year } ?: return
        envelopes.forEach { (file, envelope) ->
            val adjusted = envelope.copy(
                source = "${envelope.source}; recorte e2e com ajustes de janela (issue #137)",
                data = e2eCalendar(envelope.data, startYear, federations),
            )
            calendar.resolve(file.fileName.toString())
                .writeText(CALENDAR_JSON.encodeToString(EnvelopeDto.serializer(CalendarDto.serializer()), adjusted) + "\n")
        }
    }

    /**
     * Mesmo formato do `PackWriter` (indentação de 2, defaults gravados,
     * opcionais nulos omitidos): o calendário do recorte é gerado, então sai
     * no formato gerado, não no compacto do arquivo mantido à mão.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private val CALENDAR_JSON = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = false
    }
}

/**
 * Lê um pack do disco de volta para a forma do importador ([GeneratedPack]),
 * com os DTOs do próprio adapter — o recorte trabalha sobre o formato
 * externo, sem passar pelo domínio (que descartaria campos como `generated`).
 */
private class PackReader(private val root: Path) {

    fun read(): Pair<GeneratedPack, Provenance> {
        val countriesEnvelope = envelope(WorldPackLoader.COUNTRIES, ListSerializer(CountryDto.serializer()))
        val competitions = envelope(WorldPackLoader.COMPETITIONS, ListSerializer(CompetitionDto.serializer())).data
        val clubsByCompetition = competitions.associate { competition ->
            competition.id to envelope("competitions/${competition.id}/clubs.json", ListSerializer(ClubDto.serializer())).data
        }
        val definedClubs = clubsByCompetition.values.flatten().filterNot { it.isReference }.map { it.id }.distinct()
        val squads = definedClubs.sorted().associateWith { envelope("clubs/$it/squad.json", SquadDto.serializer()).data.players }
        val provenance = Provenance(
            generatedAt = countriesEnvelope.generatedAt,
            source = countriesEnvelope.source,
            generator = countriesEnvelope.generator ?: "world-import",
        )
        return GeneratedPack(countriesEnvelope.data, competitions, clubsByCompetition, squads) to provenance
    }

    /** Um `calendar/{ano}.json` do pack, com o envelope (a proveniência vai junto para o recorte). */
    fun calendar(file: Path): EnvelopeDto<CalendarDto> =
        WorldPackLoader.PACK_JSON.decodeFromString(EnvelopeDto.serializer(CalendarDto.serializer()), file.readText())

    private fun <T> envelope(resource: String, serializer: KSerializer<T>): EnvelopeDto<T> =
        WorldPackLoader.PACK_JSON.decodeFromString(EnvelopeDto.serializer(serializer), root.resolve(resource).readText())
}
