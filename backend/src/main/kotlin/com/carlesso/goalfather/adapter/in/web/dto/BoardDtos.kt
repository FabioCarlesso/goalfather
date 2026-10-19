package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.BoardMood
import com.carlesso.goalfather.domain.model.BoardObjective
import com.carlesso.goalfather.domain.model.BoardStatus
import com.carlesso.goalfather.domain.model.Dismissal
import com.carlesso.goalfather.domain.rules.FIRING_CONFIDENCE
import com.carlesso.goalfather.domain.rules.mood
import kotlinx.serialization.Serializable

/**
 * Painel da diretoria (issue #62) — contrato `BoardStatus`. Achata a
 * `BoardExpectation` e materializa o que o domínio DERIVA (`mood`, o bônus do
 * objetivo, o limiar de demissão): a UI pinta o painel com esses valores em vez
 * de reimplementar os limiares (regra de ouro do frontend no CLAUDE.md).
 */
@Serializable
data class BoardStatusDto(
    val clubId: Long,
    val season: Int,
    val objective: BoardObjective,
    val targetPosition: Int,
    val currentPosition: Int,
    val divisionSize: Int,
    val matchday: Int,
    val confidence: Int,
    val mood: BoardMood,
    val bonusCents: Long,
    val firingConfidence: Int,
)

/** Demissão como o `AuthUser` a carrega (issue #62) — ids crus, como o resto da API. */
@Serializable
data class DismissalDto(
    val clubId: Long,
    val clubName: String,
    val season: Int,
    val objective: BoardObjective,
    val targetPosition: Int,
    val finalPosition: Int,
)

fun BoardStatus.toDto(): BoardStatusDto = BoardStatusDto(
    clubId = clubId.value,
    season = expectation.season,
    objective = expectation.objective,
    targetPosition = expectation.targetPosition,
    currentPosition = currentPosition,
    divisionSize = divisionSize,
    matchday = matchday,
    confidence = expectation.confidence,
    mood = expectation.mood,
    bonusCents = expectation.objective.bonusCents,
    firingConfidence = FIRING_CONFIDENCE,
)

fun Dismissal.toDto(): DismissalDto = DismissalDto(
    clubId = clubId.value,
    clubName = clubName,
    season = season,
    objective = objective,
    targetPosition = targetPosition,
    finalPosition = finalPosition,
)
