package com.carlesso.goalfather.domain.result

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId

/**
 * Resultado de uma tentativa de salvar escalação.
 *
 * Como `TransferResult`, modela cada falha de negócio como variante
 * com contexto. `Success` retorna o clube atualizado para que o
 * caller (controller, no futuro) não precise refetch.
 */
sealed interface LineupResult {
    data class Success(val club: Club) : LineupResult
    data class ClubNotFound(val clubId: ClubId) : LineupResult
    data class IncompleteLineup(val expected: Int, val actual: Int) : LineupResult
    data class PlayersNotInSquad(val missingIds: List<Long>) : LineupResult
}
