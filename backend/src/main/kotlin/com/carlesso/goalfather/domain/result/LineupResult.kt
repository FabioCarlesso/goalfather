package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Resultado de uma tentativa de salvar escalação.
 * Não tem contrato OpenAPI explícito ainda — o endpoint atual retorna 204
 * no sucesso e 4xx em erros. Aqui modelamos como sealed para que o
 * controller decida o status apropriado por variante.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface LineupResult {

    @Serializable
    @SerialName("Success")
    data class Success(val club: Club) : LineupResult

    @Serializable
    @SerialName("ClubNotFound")
    data class ClubNotFound(val clubId: ClubId) : LineupResult

    @Serializable
    @SerialName("IncompleteLineup")
    data class IncompleteLineup(val expected: Int, val actual: Int) : LineupResult

    @Serializable
    @SerialName("PlayersNotInSquad")
    data class PlayersNotInSquad(val missingIds: List<Long>) : LineupResult
}
