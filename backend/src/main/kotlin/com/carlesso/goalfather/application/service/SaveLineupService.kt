package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.SaveLineupUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.LineupResult

/**
 * Implementação de `SaveLineupUseCase`. Valida:
 * 1. Clube existe;
 * 2. Exatamente 11 jogadores;
 * 3. Todos os IDs estão no elenco do clube.
 *
 * Validação posicional (jogador certo no slot certo da formação)
 * fica para o backend num refinamento futuro — frontend já faz UI-
 * level com o mapeamento de `formations.ts`. Quando o backend Spring
 * estiver com auth, esta camada vira a fonte de verdade.
 */
class SaveLineupService(
    private val clubRepo: ClubRepository,
) : SaveLineupUseCase {

    override suspend fun execute(
        clubId: ClubId,
        requesterId: Long,
        formation: Formation,
        playerIds: List<PlayerId>,
    ): LineupResult {
        // Carrega UMA vez: a checagem de posse (issue #18) e a validação da
        // escalação reusam o mesmo clube — sem o duplo fetch que existia quando
        // o controller pré-buscava o clube só para conferir o dono.
        val club = clubRepo.findById(clubId)
            ?: return LineupResult.ClubNotFound(clubId)

        if (club.ownerId != requesterId) {
            return LineupResult.NotOwner(clubId)
        }

        if (playerIds.size != EXPECTED_SIZE) {
            return LineupResult.IncompleteLineup(
                expected = EXPECTED_SIZE,
                actual = playerIds.size,
            )
        }

        val squadIndex = club.squad.associateBy { it.id }
        val missing = playerIds.filterNot { it in squadIndex }.map { it.value }
        if (missing.isNotEmpty()) {
            return LineupResult.PlayersNotInSquad(missingIds = missing)
        }

        // !! seguro: missing está vazio, então todos os IDs existem no índice.
        val orderedPlayers = playerIds.map { squadIndex.getValue(it) }
        val lineup = Lineup(players = orderedPlayers, formation = formation)
        val updatedClub = clubRepo.save(club.copy(lineup = lineup))

        return LineupResult.Success(updatedClub)
    }

    companion object {
        const val EXPECTED_SIZE: Int = 11
    }
}
