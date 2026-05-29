package com.carlesso.goalfather.adapter.`in`.web.dto

import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.PlayerId
import kotlinx.serialization.Serializable

/**
 * DTOs de request. Responses reusam tipos de domínio (já @Serializable)
 * — débito técnico aceito por ora (CLAUDE.md sugere DTOs separadas; o
 * trade-off é justificado pelo fato de o frontend gerar seus tipos do
 * MESMO contrato OpenAPI, então o acoplamento controle/contrato já é
 * implícito).
 */

@Serializable
data class LineupRequest(
    val formation: Formation,
    val playerIds: List<PlayerId>,
)

@Serializable
data class ExpandStadiumRequest(val additionalSeats: Int)

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
