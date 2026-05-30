package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.StreamMatchUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.engine.MatchSetup
import com.carlesso.goalfather.domain.engine.MatchSimulator
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Implementa o drill-down de uma partida (`/ws/matches/{id}`). Localiza a
 * partida na rodada corrente, monta o setup com os elencos dos clubes e
 * re-roda a engine com `Random(matchId)` — mesma seed do `PlayRoundService`,
 * então os eventos batem com o que a rodada produziu.
 *
 * Sem efeitos colaterais (não persiste): é só visualização. Espelha o
 * handler MSW `matchStream` do frontend.
 */
class PlayMatchService(
    private val clubRepo: ClubRepository,
    private val leagueRepo: LeagueRepository,
    private val simulator: MatchSimulator = MatchSimulator(),
) : StreamMatchUseCase {

    override fun stream(matchId: Long): Flow<MatchEvent> = flow {
        val round = leagueRepo.findLatest()
            ?: throw IllegalArgumentException("Nenhuma rodada disponível")
        val match = round.matches.find { it.matchId == matchId }
            ?: throw IllegalArgumentException("Partida $matchId não está na rodada corrente")

        val home = clubRepo.findById(match.homeClubId)
        val away = clubRepo.findById(match.awayClubId)
        val setup = MatchSetup(
            home = home?.startingLineup() ?: emptyLineup(),
            away = away?.startingLineup() ?: emptyLineup(),
            homeName = match.homeClubName,
            awayName = match.awayClubName,
        )

        simulator.simulate(setup, Random(matchId)).collect { emit(it) }
    }

    private fun emptyLineup() =
        Lineup(players = emptyList(), formation = Formation.F_4_4_2)
}
