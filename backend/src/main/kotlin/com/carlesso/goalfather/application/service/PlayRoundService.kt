package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.PlayRoundUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.engine.MatchSetup
import com.carlesso.goalfather.domain.engine.MatchSimulator
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.rules.applyRoundToStandings
import com.carlesso.goalfather.domain.rules.generateRound
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlin.random.Random

/**
 * Implementação de `PlayRoundUseCase`. Orquestra a engine + repos para
 * simular TODAS as partidas de uma rodada e emitir um stream
 * multiplexado de `RoundEvent`.
 *
 * Determinismo: cada partida usa `matchId` como seed do `Random`.
 * Mesmo `matchId` → mesma sequência. Útil para testes E2E e debug.
 *
 * Espelha exatamente o handler MSW `/ws/round/:n` em
 * `frontend/src/mocks/handlers.ts`. Quando este service rodar dentro
 * do Spring (Fase 3), o handler MSW pode ser desligado e a UI
 * continua igual.
 */
class PlayRoundService(
    private val clubRepo: ClubRepository,
    private val leagueRepo: LeagueRepository,
    private val simulator: MatchSimulator = MatchSimulator(),
) : PlayRoundUseCase {

    override fun stream(roundNumber: Int): Flow<RoundEvent> = flow {
        val round = leagueRepo.findRound(roundNumber)
            ?: throw IllegalArgumentException("Rodada $roundNumber não encontrada")

        // Pré-computa todos os eventos de todas as partidas.
        // (Em uma versão futura podemos intercalar geração + emissão para
        // não materializar tudo na memória, mas com ~3 partidas × ~30
        // eventos cabe folgado.)
        val tagged = mutableListOf<Pair<Long, MatchEvent>>()
        val finalScores = mutableMapOf<Long, Pair<Int, Int>>()

        for (match in round.matches) {
            val home = clubRepo.findById(match.homeClubId)
            val away = clubRepo.findById(match.awayClubId)

            val setup = MatchSetup(
                home = home?.startingLineup() ?: emptyLineup(),
                away = away?.startingLineup() ?: emptyLineup(),
                homeName = match.homeClubName,
                awayName = match.awayClubName,
            )

            for (event in simulator.simulate(setup, Random(match.matchId)).toList()) {
                tagged.add(match.matchId to event)
                if (event is MatchEvent.FullTime) {
                    finalScores[match.matchId] = event.homeGoals to event.awayGoals
                }
            }
        }

        // Estável por minuto: eventos de minuto igual saem juntos
        // (sensação de "vários estádios ao mesmo tempo").
        tagged.sortBy { it.second.minute }
        for ((matchId, event) in tagged) {
            emit(RoundEvent.MatchUpdate(matchId, event))
        }

        // Atualiza estado pós-rodada e dispara RoundFinished.
        val finishedMatches = round.matches.map { m -> m.finish(finalScores[m.matchId]) }
        val finishedRound = round.copy(matches = finishedMatches, status = RoundStatus.Finished)
        val newStandings = applyRoundToStandings(finishedRound, leagueRepo.currentStandings())

        leagueRepo.saveRound(finishedRound)
        leagueRepo.saveStandings(newStandings)

        // Gera a PRÓXIMA rodada (Berger) antes de sinalizar RoundFinished, para
        // que `getCurrentRound` já encontre uma rodada Scheduled e o usuário
        // possa jogar N rodadas em sequência sem intervenção.
        val clubs = clubRepo.findAll()
        if (clubs.size >= 2 && clubs.size % 2 == 0) {
            val nextRound = generateRound(round.number + 1, round.season, clubs)
            leagueRepo.saveRound(nextRound)
        }

        emit(RoundEvent.RoundFinished(newStandings))
    }

    private fun RoundMatch.finish(score: Pair<Int, Int>?): RoundMatch =
        if (score == null) this
        else copy(
            status = RoundStatus.Finished,
            homeGoals = score.first,
            awayGoals = score.second,
            minute = 90,
        )

    private fun emptyLineup() =
        Lineup(players = emptyList(), formation = Formation.F_4_4_2)
}
