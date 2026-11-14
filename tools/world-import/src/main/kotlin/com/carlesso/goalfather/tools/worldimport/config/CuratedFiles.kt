package com.carlesso.goalfather.tools.worldimport.config

import com.carlesso.goalfather.adapter.out.worlddata.dto.CompetitionDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.StadiumDto
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/*
 * Os arquivos CURADOS à mão em `data/world/` (issue #127). São a parte do
 * pack que o Wikidata não responde (ou responde mal) e que é decisão nossa.
 * Todos viram classes `@Serializable` via kaml — o mesmo kotlinx.serialization
 * dos DTOs do pack, então `competitions.yaml` REUSA o `CompetitionDto` do
 * adapter: o YAML curado e o JSON gerado têm, por construção, o mesmo formato.
 */

/** `data/world/competitions.yaml`. */
@Serializable
data class CompetitionsFile(
    val import: ImportSettings,
    val competitions: List<CompetitionSource>,
)

/** Parâmetros do importador (ver o comentário de cada um no YAML). Datas em ISO. */
@Serializable
data class ImportSettings(
    val referenceDate: String,
    val minMembershipStart: String,
    val minBirthDate: String,
    val minSquadSize: Int,
    val minGoalkeepers: Int,
    val seed: Long,
)

@Serializable
data class CompetitionSource(
    val competition: CompetitionDto,
    /** De onde veio a lista de participantes (documentação; não interpretado). */
    val reference: String,
    val clubs: List<ClubSource>,
)

/**
 * Clube numa competição. Sem [wikidata] é REFERÊNCIA a um clube definido em
 * outra competição. [country] só quando difere do país da competição.
 */
@Serializable
data class ClubSource(
    val id: String,
    /** Nome de exibição curado ("Flamengo"); sem ele, o rótulo do Wikidata. */
    val name: String? = null,
    val wikidata: String? = null,
    val country: String? = null,
) {
    val isReference: Boolean get() = wikidata == null
}

/**
 * `data/world/overrides.yaml`: correções aplicadas DEPOIS da importação.
 * Chaves: trigrama FIFA (países), id do pack (clubes) e QID (jogadores — o id
 * do pack de um jogador nasce do nome, que é justamente o que pode mudar).
 */
@Serializable
data class Overrides(
    val countries: Map<String, CountryOverride> = emptyMap(),
    val clubs: Map<String, ClubOverride> = emptyMap(),
    val players: Map<String, PlayerOverride> = emptyMap(),
)

@Serializable
data class CountryOverride(
    val name: String? = null,
    val confederation: String? = null,
    val isoAlpha2: String? = null,
    /** Outros itens do Wikidata que significam este país (ex.: Reino dos Países Baixos → NED). */
    val wikidata: List<String> = emptyList(),
)

@Serializable
data class ClubOverride(
    val name: String? = null,
    val stadium: StadiumDto? = null,
)

/**
 * Correção de um jogador. [club] põe o jogador num clube do pack (mudou de
 * clube e o Wikidata não sabe, ou a regra de vínculo atual não o pegou);
 * [exclude] tira do pack (saiu do clube, aposentou, pediu remoção).
 */
@Serializable
data class PlayerOverride(
    val club: String? = null,
    val exclude: Boolean = false,
    val name: String? = null,
    val displayName: String? = null,
    val position: String? = null,
    val nationality: String? = null,
    val birthDate: String? = null,
)

/** Os três arquivos curados juntos, como o importador os consome. */
data class CuratedInputs(
    val competitions: CompetitionsFile,
    val overrides: Overrides,
    /** `data/world/club-reputation.yaml`: id do clube → reputação 1..100. */
    val reputation: Map<String, Int>,
) {
    companion object {
        /**
         * `type` como discriminador do `sealed FormatDto`, igual ao JSON do pack
         * (o padrão do kaml seria uma tag YAML `!<roundRobin>`).
         */
        private val YAML = Yaml(
            configuration = YamlConfiguration(
                polymorphismStyle = PolymorphismStyle.Property,
                polymorphismPropertyName = "type",
            ),
        )

        fun read(worldDir: Path): CuratedInputs = CuratedInputs(
            competitions = decode(worldDir.resolve("competitions.yaml"), CompetitionsFile.serializer()),
            overrides = worldDir.resolve("overrides.yaml")
                .takeIf { it.exists() }
                ?.let { decode(it, Overrides.serializer()) }
                ?: Overrides(),
            reputation = decode(
                worldDir.resolve("club-reputation.yaml"),
                MapSerializer(String.serializer(), Int.serializer()),
            ),
        )

        private fun <T> decode(file: Path, serializer: KSerializer<T>): T =
            YAML.decodeFromString(serializer, file.readText())
    }
}
