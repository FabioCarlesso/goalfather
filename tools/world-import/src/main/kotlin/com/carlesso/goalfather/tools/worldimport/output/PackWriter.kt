package com.carlesso.goalfather.tools.worldimport.output

import com.carlesso.goalfather.adapter.out.worlddata.WorldPackLoader
import com.carlesso.goalfather.adapter.out.worlddata.dto.ClubDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.CompetitionDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.CountryDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.EnvelopeDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.SquadDto
import com.carlesso.goalfather.tools.worldimport.assemble.GeneratedPack
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText

/** Proveniência gravada em todo arquivo gerado. */
data class Provenance(val generatedAt: String, val source: String, val generator: String)

/**
 * Escreve o [GeneratedPack] no layout v1 (`docs/WORLD_DATA.md`).
 *
 * `competitions/` e `clubs/` são apagados e reescritos: um clube que saiu do
 * pack não deixa arquivo órfão. `calendar/` NÃO é tocado — datas FIFA e
 * janelas não vêm do Wikidata, são mantidas à mão.
 *
 * O JSON sai sempre igual para a mesma entrada (ordem dos campos = ordem das
 * propriedades dos DTOs; ordem das listas decidida pelo assembler), com
 * indentação de 2 espaços e quebra de linha final — o diff do git fica limpo.
 */
class PackWriter(private val root: Path, private val provenance: Provenance) {

    @OptIn(ExperimentalPathApi::class)
    fun write(pack: GeneratedPack) {
        root.resolve("competitions").deleteRecursively()
        root.resolve("clubs").deleteRecursively()

        write(WorldPackLoader.COUNTRIES, ListSerializer(CountryDto.serializer()), pack.countries)
        write(WorldPackLoader.COMPETITIONS, ListSerializer(CompetitionDto.serializer()), pack.competitions)
        pack.clubsByCompetition.forEach { (competition, clubs) ->
            write("competitions/$competition/clubs.json", ListSerializer(ClubDto.serializer()), clubs)
        }
        pack.squads.forEach { (club, players) ->
            write("clubs/$club/squad.json", SquadDto.serializer(), SquadDto(players))
        }
    }

    private fun <T> write(resource: String, serializer: KSerializer<T>, data: T) {
        val envelope = EnvelopeDto(
            schemaVersion = 1,
            generatedAt = provenance.generatedAt,
            source = provenance.source,
            generator = provenance.generator,
            data = data,
        )
        val file = root.resolve(resource)
        file.parent.createDirectories()
        file.writeText(JSON.encodeToString(EnvelopeDto.serializer(serializer), envelope) + "\n")
    }

    companion object {
        /**
         * `explicitNulls = false` omite os opcionais vazios (`displayName`,
         * `secondNationality`...): o arquivo fica só com o que existe.
         * `encodeDefaults = true` grava o `legs` do formato mesmo no valor
         * padrão, para o pack ser legível sem consultar o código.
         * `explicitNulls` e `prettyPrintIndent` são API "experimental" do
         * kotlinx — daí o `@OptIn`.
         */
        @OptIn(ExperimentalSerializationApi::class)
        private val JSON = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
