package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.CalendarUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.CupRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.CalendarView
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.NextMatch
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.rules.canScheduleSeason
import com.carlesso.goalfather.domain.rules.seasonPlan

/**
 * Implementação de [CalendarUseCase] (issue #125). O encaixe corrente vem do
 * repositório (é o último gerado); o próximo jogo do clube sai do MESMO plano
 * da temporada que o `PlayRoundService` usa para andar o relógio — uma regra
 * só para "o que vem depois".
 */
class CalendarService(
    private val leagueRepo: LeagueRepository,
    private val clubRepo: ClubRepository,
    private val cupRepo: CupRepository,
) : CalendarUseCase {

    override suspend fun calendar(clubId: ClubId?): CalendarView {
        val current = leagueRepo.findLatest() ?: return CalendarView(current = null, season = null, nextMatch = null)
        val view = CalendarView(current = current.matchday, season = current.season, nextMatch = null)
        if (clubId == null) return view

        val inPlay = current.status != RoundStatus.Finished
        // O encaixe corrente, se o clube joga nele e ele ainda não acabou.
        current.matches.firstOrNull { inPlay && it.involves(clubId) }
            ?.let { return view.copy(nextMatch = NextMatch(current.matchday, it)) }

        val clubs = clubRepo.findAll()
        if (!canScheduleSeason(clubs)) return view
        val cup = cupRepo.findBySeason(current.season)
        val next = seasonPlan(current.season, clubs, cup).nextMatchOf(
            clubId = clubId,
            after = current.matchday,
            clubs = clubs,
            cup = cup,
            // Fase da copa ainda em jogo no encaixe corrente: a próxima fase
            // nem foi sorteada, então nenhum encaixe de copa adiante é conhecido.
            cupStageInPlay = inPlay && current.matches.any { it.isKnockout },
        )
        return view.copy(nextMatch = next)
    }
}
