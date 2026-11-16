package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.CupClub
import com.carlesso.goalfather.domain.model.CupLeg
import com.carlesso.goalfather.domain.model.CupPhase
import com.carlesso.goalfather.domain.model.CupStage
import com.carlesso.goalfather.domain.model.CupTie
import com.carlesso.goalfather.domain.model.LegScore
import com.carlesso.goalfather.domain.model.Legs
import com.carlesso.goalfather.domain.model.PenaltyScore
import com.carlesso.goalfather.domain.model.TieResult
import com.carlesso.goalfather.domain.rules.prizeOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * DTOs da copa (issues #61 e #131) — `GET /api/league/cup`. Seguem a regra de
 * `Responses.kt`: nada de domínio cruza o controller. `Legs` é reusado por ser
 * enum cuja forma JSON já é a do contrato (`CupLegs`).
 *
 * O que o DTO ACRESCENTA ao domínio são derivações que a UI precisaria
 * recalcular — e recalcular seria duplicar regra no frontend (CLAUDE.md):
 * `winnerId` (quem passou, pênaltis incluídos), o agregado da ida e volta, a
 * fase corrente, o campeão e o prêmio de cada fase.
 */
@Serializable
data class CupClubDto(val id: Long, val name: String)

@Serializable
data class PenaltyScoreDto(val home: Int, val away: Int)

@Serializable
data class TieResultDto(
    val homeGoals: Int,
    val awayGoals: Int,
    val penalties: PenaltyScoreDto? = null,
)

/** Placar de UM jogo, na orientação mandante × visitante daquele jogo (contrato `CupLegScore`). */
@Serializable
data class LegScoreDto(val homeGoals: Int, val awayGoals: Int)

/** Gols somados nos dois jogos, na orientação do CONFRONTO (`home` = mandante da ida). */
@Serializable
data class AggregateDto(val home: Int, val away: Int)

/** Espelha o `oneOf` + `discriminator` de `CupTie` no contrato. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface CupTieDto {

    @Serializable
    @SerialName("Single")
    data class Single(
        val index: Int,
        val home: CupClubDto,
        val away: CupClubDto,
        val result: TieResultDto? = null,
        /** Quem passou — ausente enquanto o confronto não foi disputado. */
        val winnerId: Long? = null,
    ) : CupTieDto

    /**
     * Ida e volta. `firstLeg`/`secondLeg` na orientação de CADA jogo (a volta
     * tem o mando invertido, como na súmula); `aggregate` e `penalties` na
     * orientação do confronto — a UI lê "agregado 2 × 2 (pên. 4 × 3)" sem
     * inverter nada.
     */
    @Serializable
    @SerialName("TwoLegged")
    data class TwoLegged(
        val index: Int,
        val home: CupClubDto,
        val away: CupClubDto,
        val firstLeg: LegScoreDto? = null,
        val secondLeg: LegScoreDto? = null,
        val aggregate: AggregateDto? = null,
        val penalties: PenaltyScoreDto? = null,
        val winnerId: Long? = null,
    ) : CupTieDto
}

@Serializable
data class CupStageDto(
    val name: String,
    val legs: Legs,
    val ties: List<CupTieDto>,
)

/** Uma fase do plano da copa — inclusive as ainda não sorteadas — com quem entra nela e o prêmio. */
@Serializable
data class CupPhaseDto(
    val name: String,
    val teams: Int,
    val legs: Legs,
    val entrants: List<CupClubDto>,
    val prizeCents: Long,
)

@Serializable
data class CupDto(
    val season: Int,
    val competitionId: String,
    val competitionName: String,
    /** Todas as fases da copa, da abertura à final — a UI desenha a chave inteira. */
    val phases: List<CupPhaseDto>,
    /** Fases já sorteadas, com confrontos e resultados. */
    val stages: List<CupStageDto>,
    /** Nome da fase em disputa; ausente quando a copa acabou. */
    val currentStage: String? = null,
    /** Vencedor da final; ausente enquanto a copa não acabou. */
    val champion: CupClubDto? = null,
)

/**
 * A perna de um jogo de copa no contrato (`CupRoundStage.leg`). O sealed do
 * domínio vira um enum + o placar da ida ao lado: para a UI basta o rótulo.
 */
@Serializable
enum class CupLegDto { SINGLE, FIRST, SECOND }

// ─── Mapeamento domínio → DTO ─────────────────────────────────────────────

fun Cup.toDto(): CupDto = CupDto(
    season = season,
    competitionId = competitionId.value,
    competitionName = competitionName,
    // `mapIndexed`: o prêmio é regra por ORDEM da fase (`prizeOf`).
    phases = phases.mapIndexed { i, phase -> phase.toDto(prizeOf(i).cents) },
    stages = stages.zip(phases) { stage, phase -> stage.toDto(phase.legs) },
    currentStage = currentStage?.name,
    champion = champion?.toDto(),
)

fun CupClub.toDto(): CupClubDto = CupClubDto(id = id.value, name = name)

private fun CupPhase.toDto(prizeCents: Long): CupPhaseDto = CupPhaseDto(
    name = name,
    teams = teams,
    legs = legs,
    entrants = entrants.map { it.toDto() },
    prizeCents = prizeCents,
)

private fun CupStage.toDto(legs: Legs): CupStageDto =
    CupStageDto(name = name, legs = legs, ties = ties.map { it.toDto() })

/** `when` exaustivo sobre o sealed de domínio: formato de confronto novo quebra a compilação aqui. */
private fun CupTie.toDto(): CupTieDto = when (this) {
    is CupTie.Single -> CupTieDto.Single(
        index = index,
        home = home.toDto(),
        away = away.toDto(),
        result = result?.toDto(),
        winnerId = winner?.id?.value,
    )
    is CupTie.TwoLegged -> CupTieDto.TwoLegged(
        index = index,
        home = home.toDto(),
        away = away.toDto(),
        firstLeg = firstLeg?.toDto(),
        secondLeg = secondLeg?.toDto(),
        aggregate = firstLeg?.let { AggregateDto(homeAggregate, awayAggregate) },
        // Domínio: orientação da volta (mandante = `away`). Contrato: do confronto.
        penalties = penalties?.let { PenaltyScoreDto(home = it.away, away = it.home) },
        winnerId = winner?.id?.value,
    )
}

private fun TieResult.toDto(): TieResultDto =
    TieResultDto(homeGoals = homeGoals, awayGoals = awayGoals, penalties = penalties?.toDto())

fun LegScore.toDto(): LegScoreDto = LegScoreDto(homeGoals = homeGoals, awayGoals = awayGoals)

fun PenaltyScore.toDto(): PenaltyScoreDto = PenaltyScoreDto(home = home, away = away)

/** `when` exaustivo sobre a perna do domínio. */
fun CupLeg.toDto(): CupLegDto = when (this) {
    CupLeg.Single -> CupLegDto.SINGLE
    CupLeg.First -> CupLegDto.FIRST
    is CupLeg.Second -> CupLegDto.SECOND
}
