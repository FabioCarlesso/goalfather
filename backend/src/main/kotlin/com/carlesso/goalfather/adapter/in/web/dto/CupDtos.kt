package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.CupClub
import com.carlesso.goalfather.domain.model.CupStage
import com.carlesso.goalfather.domain.model.CupTie
import com.carlesso.goalfather.domain.model.KnockoutPhase
import com.carlesso.goalfather.domain.model.PenaltyScore
import com.carlesso.goalfather.domain.model.TieResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * DTOs da copa (issue #61) — `GET /api/league/cup`. Seguem a regra de
 * `Responses.kt`: nada de domínio cruza o controller. `KnockoutPhase` é
 * reusado por ser enum cuja forma JSON já é a do contrato.
 *
 * O que o DTO ACRESCENTA ao domínio são derivações que a UI precisaria
 * recalcular — e recalcular seria duplicar regra no frontend (CLAUDE.md):
 * `winnerId` (quem passou, pênaltis incluídos), `currentPhase`, `champion` e o
 * prêmio de cada fase.
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

/** Espelha o `oneOf` + `discriminator` de `CupTie` no contrato. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface CupTieDto {

    @Serializable
    @SerialName("Bye")
    data class Bye(val index: Int, val club: CupClubDto) : CupTieDto

    @Serializable
    @SerialName("Fixture")
    data class Fixture(
        val index: Int,
        val home: CupClubDto,
        val away: CupClubDto,
        val result: TieResultDto? = null,
        /** Quem passou — ausente enquanto o confronto não foi disputado. */
        val winnerId: Long? = null,
    ) : CupTieDto
}

@Serializable
data class CupStageDto(
    val phase: KnockoutPhase,
    val ties: List<CupTieDto>,
)

/** Uma fase do mata-mata e o prêmio de quem a vence — inclusive as ainda não sorteadas. */
@Serializable
data class CupPhaseDto(
    val phase: KnockoutPhase,
    val prizeCents: Long,
)

@Serializable
data class CupDto(
    val season: Int,
    /** Todas as fases da copa, da abertura à final — a UI desenha a chave inteira. */
    val phases: List<CupPhaseDto>,
    /** Fases já sorteadas, com confrontos e resultados. */
    val stages: List<CupStageDto>,
    /** Fase em disputa; ausente quando a copa acabou. */
    val currentPhase: KnockoutPhase? = null,
    /** Vencedor da final; ausente enquanto a copa não acabou. */
    val champion: CupClubDto? = null,
)

// ─── Mapeamento domínio → DTO ─────────────────────────────────────────────

fun Cup.toDto(): CupDto = CupDto(
    season = season,
    phases = phases.map { CupPhaseDto(it, it.prizeCents) },
    stages = stages.map { it.toDto() },
    currentPhase = currentStage?.phase,
    champion = champion?.toDto(),
)

fun CupClub.toDto(): CupClubDto = CupClubDto(id = id.value, name = name)

private fun CupStage.toDto(): CupStageDto = CupStageDto(phase = phase, ties = ties.map { it.toDto() })

/** `when` exaustivo sobre o sealed de domínio: tipo de vaga novo quebra a compilação aqui. */
private fun CupTie.toDto(): CupTieDto = when (this) {
    is CupTie.Bye -> CupTieDto.Bye(index = index, club = club.toDto())
    is CupTie.Fixture -> CupTieDto.Fixture(
        index = index,
        home = home.toDto(),
        away = away.toDto(),
        result = result?.toDto(),
        winnerId = winner?.id?.value,
    )
}

private fun TieResult.toDto(): TieResultDto =
    TieResultDto(homeGoals = homeGoals, awayGoals = awayGoals, penalties = penalties?.toDto())

private fun PenaltyScore.toDto(): PenaltyScoreDto = PenaltyScoreDto(home = home, away = away)
