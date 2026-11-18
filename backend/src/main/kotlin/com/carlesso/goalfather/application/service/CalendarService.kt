package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.CalendarUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.application.port.out.ContinentalRepository
import com.carlesso.goalfather.application.port.out.CupRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.CalendarView
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.NextMatch
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.leagueOf
import com.carlesso.goalfather.domain.rules.worldPlan
import com.carlesso.goalfather.domain.rules.worldSeasons

/**
 * Implementação de [CalendarUseCase] (issues #125/#131). O encaixe corrente
 * vem do repositório (é o último gerado); o próximo jogo do clube sai do MESMO
 * plano do mundo que o `PlayRoundService` usa para andar o relógio — uma regra
 * só para "o que vem depois".
 *
 * A temporada mostrada é a da LIGA do clube (issue #131): o técnico da Premier
 * League vê `2026` / `2026/27` mesmo quando o encaixe corrente é de janeiro de
 * 2027. Sem clube (ou clube sem liga), vale o ano civil do encaixe.
 */
class CalendarService(
    private val leagueRepo: LeagueRepository,
    private val clubRepo: ClubRepository,
    private val cupRepo: CupRepository,
    private val catalogRepo: CompetitionCatalogRepository,
    // Continentais (issue #132): o jogo da Libertadores aparece no calendário do clube.
    private val continentalRepo: ContinentalRepository = ContinentalRepository.NONE,
) : CalendarUseCase {

    override suspend fun calendar(clubId: ClubId?): CalendarView {
        val current = leagueRepo.findLatest()
            ?: return CalendarView(current = null, season = null, nextMatch = null)
        val view = CalendarView(current = current.matchday, season = current.season, nextMatch = null)
        if (clubId == null) return view

        val catalog = catalogRepo.findAll()
        val clubs = clubRepo.findAll()
        val seasons = leagueRepo.currentStandings().worldSeasons()
        val league = clubs.firstOrNull { it.id == clubId }?.let { catalog.leagueOf(it) }
        val withSeason = league?.let { game ->
            seasons[game.country.code]?.let { year ->
                view.copy(season = year, seasonLabel = game.seasonOf(year).label)
            }
        } ?: view

        val inPlay = current.status != RoundStatus.Finished
        // O encaixe corrente, se o clube joga nele e ele ainda não acabou.
        current.matches.firstOrNull { inPlay && it.involves(clubId) }
            ?.let { return withSeason.copy(nextMatch = NextMatch(current.matchday, it)) }

        val plan = worldPlan(
            catalog,
            clubs,
            seasons,
            cupRepo.currentCups(catalog, seasons),
            continentalRepo.currentContinentals(catalog, seasons),
        )
        val next = plan.nextMatchOf(
            clubId = clubId,
            after = current.matchday,
            clubs = clubs,
            // Fase de copa ainda em jogo no encaixe corrente: a próxima fase
            // nem foi sorteada, então nenhum encaixe adiante dela é conhecido.
            cupsInPlay = if (inPlay) current.matches.filter { it.isKnockout }.mapTo(HashSet()) { it.competition } else emptySet(),
        )
        return withSeason.copy(nextMatch = next)
    }
}
