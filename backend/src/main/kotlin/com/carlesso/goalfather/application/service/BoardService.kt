package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.BoardUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.BoardStatus
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.rules.boardExpectations

/**
 * Painel da diretoria (issue #62). Só leitura: quem move a confiança é a
 * rodada (`PlayRoundService`), dentro do claim que garante uma aplicação por
 * rodada — um GET que gravasse a meta abriria uma segunda via de escrita.
 *
 * Save anterior à issue ganha a meta no boot (`DataInitializer`). Se ainda
 * assim faltar (o backfill perdeu uma corrida de lock), ela é DERIVADA aqui
 * com a mesma regra pura que a primeira rodada vai usar para fixá-la — rede de
 * segurança, não o caminho comum.
 */
class BoardService(
    private val clubRepo: ClubRepository,
    private val leagueRepo: LeagueRepository,
) : BoardUseCase {

    override suspend fun status(clubId: ClubId): BoardStatus? {
        val club = clubRepo.findById(clubId) ?: return null
        val table = leagueRepo.currentStandings().firstOrNull { t -> t.rows.any { it.clubId == clubId } }
            ?: return null
        val row = table.rows.first { it.clubId == clubId }

        val expectation = club.board?.takeIf { it.season == table.season }
            ?: boardExpectations(table.season, clubRepo.findAll())[clubId]
            ?: return null

        return BoardStatus(
            clubId = clubId,
            expectation = expectation,
            currentPosition = row.position,
            divisionSize = table.rows.size,
            matchday = table.round,
        )
    }
}
