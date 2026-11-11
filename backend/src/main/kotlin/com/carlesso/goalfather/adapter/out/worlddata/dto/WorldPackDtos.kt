package com.carlesso.goalfather.adapter.out.worlddata.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DTOs do provedor de dados do mundo (issue #126): a forma EXTERNA do pack,
 * espelhando o JSON de `data/world/v1/`. Vivem só aqui, no adapter — o domínio
 * nunca os vê (o `ArchitectureBoundaryTest` garante). Tudo que é tipado no domínio
 * (`CountryCode`, `Position`, `LocalDate`, faixas) aqui é `String`/`Int` cru:
 * quem valida e traduz é o `PackMapper`, a camada anticorrupção.
 *
 * Campos opcionais têm default: junto com `ignoreUnknownKeys = true` no `Json`
 * do adapter, isso deixa um pack mais NOVO (com campos a mais) ser lido por
 * código mais velho, e um pack mais VELHO (sem campos novos) por código novo.
 */

/**
 * Só o cabeçalho do envelope. Lido ANTES do corpo: se a versão é
 * desconhecida, o erro é "versão não suportada", e não um "campo X faltando"
 * que esconderia a causa real.
 */
@Serializable
data class EnvelopeHeaderDto(val schemaVersion: Int)

/**
 * Envelope de todo arquivo do pack: `{schemaVersion, generatedAt, source, data}`.
 *
 * Genérico em `T`: o plugin do kotlinx gera um serializer que recebe o
 * serializer de `T` como parâmetro (`EnvelopeDto.serializer(dataSerializer)`),
 * o equivalente ao `TypeReference<Envelope<List<Club>>>` do Jackson, só que
 * resolvido em compilação, sem reflexão.
 */
@Serializable
data class EnvelopeDto<T>(
    val schemaVersion: Int,
    val generatedAt: String,
    val source: String,
    val data: T,
)

/** `countries.json` → lista. */
@Serializable
data class CountryDto(
    val code: String,
    val name: String,
    val confederation: String,
    val isoAlpha2: String,
)

/** `competitions.json` → lista. Exatamente um entre [country] e [confederation]. */
@Serializable
data class CompetitionDto(
    val id: String,
    val name: String,
    val country: String? = null,
    val confederation: String? = null,
    val tier: Int? = null,
    val strength: Int? = null,
    val format: FormatDto,
    val tiebreakers: List<String>? = null,
)

/**
 * Formato da competição. `sealed` + `@SerialName`: o kotlinx escolhe a
 * subclasse pelo campo `"type"` do JSON (`"roundRobin"`, `"knockout"`...).
 * Um `type` desconhecido vira erro de formato do recurso, não exception solta.
 */
@Serializable
sealed interface FormatDto {
    @Serializable
    @SerialName("roundRobin")
    data class RoundRobin(val legs: Int = 2) : FormatDto

    @Serializable
    @SerialName("knockout")
    data class Knockout(val stages: List<KnockoutStageDto>) : FormatDto

    @Serializable
    @SerialName("groupsThenKnockout")
    data class GroupsThenKnockout(
        val groups: Int,
        val perGroup: Int,
        val qualifiersPerGroup: Int,
        val groupLegs: Int = 2,
        val knockout: List<KnockoutStageDto>,
    ) : FormatDto

    @Serializable
    @SerialName("swissLeague")
    data class SwissLeague(
        val teams: Int,
        val matchesPerTeam: Int,
        val directQualifiers: Int,
        val playoffFrom: Int? = null,
        val playoffTo: Int? = null,
    ) : FormatDto
}

@Serializable
data class KnockoutStageDto(
    val name: String,
    val teams: Int,
    val legs: Int = 1,
    val newEntrants: Int = 0,
)

/**
 * Item de `competitions/{id}/clubs.json`. Só [id] é obrigatório: um item SÓ
 * com id é uma REFERÊNCIA a um clube definido por completo em outra
 * competição (a Copa do Brasil cita os clubes da Série A sem repetir os
 * dados), como uma API que embute só o id de um recurso relacionado.
 */
@Serializable
data class ClubDto(
    val id: String,
    val name: String? = null,
    val country: String? = null,
    val reputation: Int? = null,
    val stadium: StadiumDto? = null,
) {
    /** Sem nenhum dado além do id = referência. */
    val isReference: Boolean get() = name == null && country == null && reputation == null && stadium == null
}

@Serializable
data class StadiumDto(val name: String, val capacity: Int)

/** `clubs/{id}/squad.json` → objeto com a lista de jogadores. */
@Serializable
data class SquadDto(val players: List<PlayerDto>)

/**
 * Jogador: só fatos públicos. Atributo, salário, valor e contrato NÃO existem
 * aqui de propósito (política de direitos) — se o pack trouxer, o
 * `ignoreUnknownKeys` descarta.
 */
@Serializable
data class PlayerDto(
    val id: String,
    val name: String,
    val displayName: String? = null,
    val birthDate: String,
    val nationality: String,
    val secondNationality: String? = null,
    val position: String,
)

/** `calendar/{year}.json`. */
@Serializable
data class CalendarDto(
    val year: Int,
    val fifaWindows: List<DateRangeDto> = emptyList(),
    val transferWindows: List<TransferWindowDto> = emptyList(),
)

@Serializable
data class DateRangeDto(val start: String, val end: String)

/** Janela por federação; [country] é o trigrama FIFA da federação (`BRA` = CBF). */
@Serializable
data class TransferWindowDto(val country: String, val start: String, val end: String)
