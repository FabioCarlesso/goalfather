package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.adapter.out.persistence.entity.CompetitionCatalogEntity
import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.DisciplineRules
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.KnockoutStage
import com.carlesso.goalfather.domain.model.Legs
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.Tiebreaker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Catálogo de competições do save (issue #131): `GameCompetition` ↔ linha de
 * `competition_catalog`. O formato vai como JSON de um registro PRÓPRIO da
 * persistência ([FormatRecord]), e não o `CompetitionFormat` serializado:
 * renomear um campo do domínio não pode quebrar a leitura de um save gravado
 * (mesma escolha do `discipline_json` do `PlayerMapper`).
 */

private const val LEAGUE = "LEAGUE"
private const val NATIONAL_CUP = "NATIONAL_CUP"

/** Continental (issue #132). `country` da linha = o país que hospeda o calendário dela. */
private const val CONTINENTAL = "CONTINENTAL"

/** [order] é a posição no catálogo (ordem do pack), devolvida na leitura. */
fun GameCompetition.toEntity(order: Int): CompetitionCatalogEntity = CompetitionCatalogEntity(
    id = id.value,
    sortOrder = order,
    name = competition.name,
    country = country.code.value,
    // `when` exaustivo sobre o sealed: um papel novo (continental, #132) não
    // compila até ganhar a sua representação no banco.
    kind = when (kind) {
        is CompetitionKind.League -> LEAGUE
        CompetitionKind.NationalCup -> NATIONAL_CUP
        CompetitionKind.Continental -> CONTINENTAL
    },
    tier = (kind as? CompetitionKind.League)?.tier?.value,
    seasonSpan = span.name,
    pausesOnFifaWindows = pausesOnFifaWindows,
    formatJson = catalogJson.encodeToString(FormatRecord.serializer(), competition.format.toRecord()),
    tiebreakers = competition.tiebreakers.joinToString(",") { it.name },
    foreignPlayerLimit = competition.foreignPlayerLimit,
    yellowsForSuspension = competition.discipline.yellowsForSuspension,
    matchesPerRedCard = competition.discipline.matchesPerRedCard,
    resetsYellowsAtKnockout = competition.discipline.resetsYellowsAtKnockout,
)

/**
 * Linha → domínio. Ao contrário do foco de treino ou do preço do ingresso, um
 * valor inválido AQUI derruba a leitura (o `init` do domínio lança): o
 * catálogo é a regra do jogo inteiro, e "consertar" uma competição em silêncio
 * mudaria o campeonato sem ninguém saber. Só o próprio seed escreve nesta tabela.
 */
fun CompetitionCatalogEntity.toDomain(): GameCompetition = GameCompetition(
    competition = Competition(
        id = CompetitionId(id),
        name = name,
        format = catalogJson.decodeFromString(FormatRecord.serializer(), formatJson).toDomain(),
        tiebreakers = tiebreakers.split(',').filter { it.isNotBlank() }.map(Tiebreaker::valueOf),
        discipline = DisciplineRules(yellowsForSuspension, matchesPerRedCard, resetsYellowsAtKnockout),
        foreignPlayerLimit = foreignPlayerLimit,
    ),
    country = country.toCountry(),
    kind = when (kind) {
        LEAGUE -> CompetitionKind.League(Division(requireNotNull(tier) { "liga $id sem tier no catálogo" }))
        NATIONAL_CUP -> CompetitionKind.NationalCup
        CONTINENTAL -> CompetitionKind.Continental
        else -> error("Papel de competição desconhecido no catálogo: '$kind' ($id)")
    },
    span = SeasonSpan.valueOf(seasonSpan),
    pausesOnFifaWindows = pausesOnFifaWindows,
)

private val catalogJson = Json { ignoreUnknownKeys = true }

/**
 * Formato da competição como a persistência o grava. `sealed` + `@SerialName`
 * dá o discriminador `"type"` no JSON, como no pack — os quatro formatos do
 * domínio, porque o `when` de tradução tem de ser exaustivo.
 */
@Serializable
private sealed interface FormatRecord {
    @Serializable
    @SerialName("roundRobin")
    data class RoundRobin(val legs: Int) : FormatRecord

    @Serializable
    @SerialName("knockout")
    data class Knockout(val stages: List<StageRecord>) : FormatRecord

    @Serializable
    @SerialName("groupsThenKnockout")
    data class GroupsThenKnockout(
        val groups: Int,
        val perGroup: Int,
        val qualifiersPerGroup: Int,
        val groupLegs: Int,
        val knockout: List<StageRecord>,
    ) : FormatRecord

    @Serializable
    @SerialName("swissLeague")
    data class SwissLeague(
        val teams: Int,
        val matchesPerTeam: Int,
        val directQualifiers: Int,
        val playoffFrom: Int? = null,
        val playoffTo: Int? = null,
    ) : FormatRecord
}

@Serializable
private data class StageRecord(val name: String, val teams: Int, val legs: Int, val newEntrants: Int)

private fun CompetitionFormat.toRecord(): FormatRecord = when (this) {
    is CompetitionFormat.RoundRobin -> FormatRecord.RoundRobin(legs)
    is CompetitionFormat.Knockout -> FormatRecord.Knockout(stages.map { it.toRecord() })
    is CompetitionFormat.GroupsThenKnockout ->
        FormatRecord.GroupsThenKnockout(groups, perGroup, qualifiersPerGroup, groupLegs, knockout.stages.map { it.toRecord() })
    is CompetitionFormat.SwissLeague -> FormatRecord.SwissLeague(
        teams = teams,
        matchesPerTeam = matchesPerTeam,
        directQualifiers = directQualifiers,
        // `takeUnless` devolve `null` quando a condição vale: faixa vazia = sem playoff.
        playoffFrom = playoffRange.takeUnless { it.isEmpty() }?.first,
        playoffTo = playoffRange.takeUnless { it.isEmpty() }?.last,
    )
}

private fun FormatRecord.toDomain(): CompetitionFormat = when (this) {
    is FormatRecord.RoundRobin -> CompetitionFormat.RoundRobin(legs)
    is FormatRecord.Knockout -> CompetitionFormat.Knockout(stages.map { it.toDomain() })
    is FormatRecord.GroupsThenKnockout -> CompetitionFormat.GroupsThenKnockout(
        groups = groups,
        perGroup = perGroup,
        qualifiersPerGroup = qualifiersPerGroup,
        knockout = CompetitionFormat.Knockout(knockout.map { it.toDomain() }),
        groupLegs = groupLegs,
    )
    is FormatRecord.SwissLeague -> CompetitionFormat.SwissLeague(
        teams = teams,
        matchesPerTeam = matchesPerTeam,
        directQualifiers = directQualifiers,
        playoffRange = if (playoffFrom != null && playoffTo != null) playoffFrom..playoffTo else IntRange.EMPTY,
    )
}

private fun KnockoutStage.toRecord() = StageRecord(name, teams, legs.matches, newEntrants)

private fun StageRecord.toDomain() = KnockoutStage(
    name = name,
    teams = teams,
    legs = Legs.entries.first { it.matches == legs },
    newEntrants = newEntrants,
)
