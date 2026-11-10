package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.event.TrainingEvent
import com.carlesso.goalfather.domain.event.TrainingReport
import com.carlesso.goalfather.domain.model.BoardObjective
import com.carlesso.goalfather.domain.model.BoardReview
import com.carlesso.goalfather.domain.model.BoardVerdict
import com.carlesso.goalfather.domain.model.Retirement
import com.carlesso.goalfather.domain.model.RoundFinance
import com.carlesso.goalfather.domain.model.TrainedAttribute
import com.carlesso.goalfather.domain.model.TrainingFocus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * DTOs do stream de rodada (WS `/ws/round/{n}`) — issue #72. Estende ao
 * WebSocket a regra de `Responses.kt`: nada de domínio cruza a fronteira web.
 *
 * O defeito concreto era o mesmo do PR #71, um nível abaixo: `Retirement` e
 * `TrainingEvent` embutem o `Player` de domínio, e `Player.star` (computed
 * property) não sobrevive ao `kotlinx.serialization`. `GET /api/clubs/{id}`
 * emitia `star`; o WS não.
 *
 * `RoundFinance` e `BoardReview` já batiam campo a campo com o contrato — viram
 * DTO por consistência: assim um campo novo no domínio não vaza para o cliente
 * sem passar pelo contrato. As tabelas (`StandingsDto`) moram em
 * `LeagueDtos.kt`, porque também saem pelo `GET /api/league/standings`.
 *
 * Reusados do domínio, pelo mesmo critério de `Responses.kt` (forma JSON já é
 * EXATAMENTE a do contrato e não embute agregado): os enums `TrainingFocus`,
 * `TrainedAttribute` e `BoardObjective`; o sealed `BoardVerdict` (só um
 * `Long`); e `MatchEvent`, que só carrega ids e está fora do escopo da #72.
 */
@Serializable
data class RoundFinanceDto(
    val clubId: Long,
    val ticketRevenue: Long,
    val salariesPaid: Long,
    val deficit: Long,
    val ticketPrice: Long,
    val attendance: Int,
    val cupPrize: Long,
)

/** O jogador que saiu e o que subiu da base passam pelo `Player.toDto()` — com `star`. */
@Serializable
data class RetirementDto(
    val clubId: Long,
    val retired: PlayerDto,
    val promoted: PlayerDto,
)

/** Espelha o `oneOf` + `discriminator` de `TrainingEvent` no contrato. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface TrainingEventDto {

    @Serializable
    @SerialName("Improved")
    data class Improved(val player: PlayerDto, val attribute: TrainedAttribute) : TrainingEventDto

    @Serializable
    @SerialName("Injured")
    data class Injured(val player: PlayerDto, val roundsOut: Int) : TrainingEventDto
}

@Serializable
data class TrainingReportDto(
    val clubId: Long,
    val focus: TrainingFocus,
    val events: List<TrainingEventDto>,
)

@Serializable
data class BoardReviewDto(
    val clubId: Long,
    val clubName: String,
    val season: Int,
    val objective: BoardObjective,
    val targetPosition: Int,
    val finalPosition: Int,
    val confidence: Int,
    val verdict: BoardVerdict,
)

/**
 * Espelha o `oneOf` + `discriminator: type` de `RoundEvent` no contrato. É o
 * ÚNICO formato de fio do stream de rodada: o sealed de domínio deixou de ser
 * `@Serializable` (issue #96), então os `@SerialName` daqui é que fixam os
 * nomes das variantes que o frontend lê.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface RoundEventDto {

    @Serializable
    @SerialName("MatchUpdate")
    data class MatchUpdate(val matchId: Long, val event: MatchEvent) : RoundEventDto

    @Serializable
    @SerialName("RoundFinished")
    data class RoundFinished(
        val standings: List<StandingsDto>,
        val finances: List<RoundFinanceDto>,
        val training: List<TrainingReportDto>,
    ) : RoundEventDto

    @Serializable
    @SerialName("SeasonFinished")
    data class SeasonFinished(
        val season: Int,
        val champion: StandingRowDto,
        val standings: List<StandingsDto>,
        val retirements: List<RetirementDto>,
        /** Ausente (não `null`) quando não houve campeão de copa — `explicitNulls = false`. */
        val cupChampion: CupClubDto? = null,
        val boardReviews: List<BoardReviewDto>,
    ) : RoundEventDto
}

// ─── Mapeamento domínio → DTO ─────────────────────────────────────────────

fun RoundFinance.toDto(): RoundFinanceDto = RoundFinanceDto(
    clubId = clubId.value,
    ticketRevenue = ticketRevenue.cents,
    salariesPaid = salariesPaid.cents,
    deficit = deficit.cents,
    ticketPrice = ticketPrice.cents,
    attendance = attendance,
    cupPrize = cupPrize.cents,
)

fun Retirement.toDto(): RetirementDto =
    RetirementDto(clubId = clubId.value, retired = retired.toDto(), promoted = promoted.toDto())

/** `when` exaustivo, sem `else`: desfecho de treino novo quebra a compilação aqui. */
fun TrainingEvent.toDto(): TrainingEventDto = when (this) {
    is TrainingEvent.Improved -> TrainingEventDto.Improved(player = player.toDto(), attribute = attribute)
    is TrainingEvent.Injured -> TrainingEventDto.Injured(player = player.toDto(), roundsOut = roundsOut)
}

fun TrainingReport.toDto(): TrainingReportDto =
    TrainingReportDto(clubId = clubId.value, focus = focus, events = events.map { it.toDto() })

fun BoardReview.toDto(): BoardReviewDto = BoardReviewDto(
    clubId = clubId.value,
    clubName = clubName,
    season = season,
    objective = objective,
    targetPosition = targetPosition,
    finalPosition = finalPosition,
    confidence = confidence,
    verdict = verdict,
)

/**
 * `when` exaustivo, sem `else`: variante nova em `RoundEvent` quebra a
 * compilação aqui em vez de sair no formato errado em runtime.
 */
fun RoundEvent.toDto(): RoundEventDto = when (this) {
    is RoundEvent.MatchUpdate -> RoundEventDto.MatchUpdate(matchId = matchId, event = event)

    is RoundEvent.RoundFinished -> RoundEventDto.RoundFinished(
        standings = standings.map { it.toDto() },
        finances = finances.map { it.toDto() },
        training = training.map { it.toDto() },
    )

    is RoundEvent.SeasonFinished -> RoundEventDto.SeasonFinished(
        season = season,
        champion = champion.toDto(),
        standings = standings.map { it.toDto() },
        retirements = retirements.map { it.toDto() },
        cupChampion = cupChampion?.toDto(),
        boardReviews = boardReviews.map { it.toDto() },
    )
}
