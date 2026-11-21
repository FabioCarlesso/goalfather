package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import java.time.LocalDate

/**
 * Port de saída do esfriamento da renovação (issue #138, V26): até quando um
 * jogador não ouve nova proposta de renovação do clube depois de uma oferta
 * ofensiva. Por jogador × clube: trocar de clube zera a conversa.
 */
interface ContractTalksRepository {
    /** Até quando (exclusive) [playerId] não negocia com [clubId], ou `null`. */
    suspend fun cooldownUntil(playerId: PlayerId, clubId: ClubId): LocalDate?

    /** Grava (ou estende) o esfriamento de [playerId] com [clubId] até [until]. */
    suspend fun startCooldown(playerId: PlayerId, clubId: ClubId, until: LocalDate)

    companion object {
        /** Sem memória: ninguém esfria. Para testes montados à mão. */
        val NONE: ContractTalksRepository = object : ContractTalksRepository {
            override suspend fun cooldownUntil(playerId: PlayerId, clubId: ClubId): LocalDate? = null

            override suspend fun startCooldown(playerId: PlayerId, clubId: ClubId, until: LocalDate) = Unit
        }
    }
}
