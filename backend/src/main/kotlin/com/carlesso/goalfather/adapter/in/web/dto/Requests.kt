package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Posture
import com.carlesso.goalfather.domain.model.TrainingFocus
import kotlinx.serialization.Serializable

/**
 * DTOs de request. Os de RESPOSTA vivem em `Responses.kt`.
 *
 * O débito que estava anotado aqui — "responses reusam tipos de domínio" —
 * foi pago: a premissa de que o acoplamento era inofensivo porque o frontend
 * gera seus tipos do mesmo contrato estava errada justamente ao contrário. O
 * frontend seguia o contrato e o backend seguia o domínio, e os dois
 * divergiram em silêncio (`Lineup`, `Player.star`). Ver o KDoc de
 * `Responses.kt`.
 */

/**
 * `posture` (issue #56) é opcional no contrato e cai em `BALANCED` quando
 * ausente — o request achatado (postura solta, não `{"tactics":{...}}`)
 * poupa um nível de aninhamento para um único campo; o controller reconstrói
 * o `Tactics` do domínio.
 */
@Serializable
data class LineupRequest(
    val formation: Formation,
    val playerIds: List<PlayerId>,
    val posture: Posture = Posture.BALANCED,
)

@Serializable
data class ExpandStadiumRequest(val additionalSeats: Int)

/**
 * Foco de treino da semana (issue #58). Sem default: escolher treino é um
 * comando explícito — um corpo vazio virando `DESCANSO` silenciosamente
 * esconderia um cliente quebrado.
 */
@Serializable
data class TrainingFocusRequest(val focus: TrainingFocus)

@Serializable
data class BuyPlayerRequest(val clubId: Long, val playerId: Long)

@Serializable
data class SellPlayerRequest(val clubId: Long, val playerId: Long)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
data class PlayRoundResponse(val roundNumber: Int)
