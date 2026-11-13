package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.metrics.GoalfatherMetrics
import com.carlesso.goalfather.application.port.`in`.MatchStream
import com.carlesso.goalfather.application.port.`in`.StreamMatchUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.engine.MatchSetup
import com.carlesso.goalfather.domain.engine.MatchSimulator
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.Matchday
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlin.random.Random

/**
 * Implementa o drill-down de uma partida (`/ws/matches/{id}`). Localiza a
 * partida no encaixe dela (`Matchday.ofMatchId`), monta o setup com os elencos dos clubes e
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
    // Injetável para métricas; default isolado preserva os construtores dos testes.
    private val meterRegistry: MeterRegistry = SimpleMeterRegistry(),
) : StreamMatchUseCase {

    private val simulationTimer: Timer = meterRegistry.timer(GoalfatherMetrics.MATCH_SIMULATION)

    override suspend fun open(matchId: Long): MatchStream {
        // Encaixe DA PARTIDA, não o último (issue #151): depois que a rodada
        // encerra, `findLatest` já aponta o encaixe seguinte, e o drill-down de
        // um jogo recém-jogado caía em "não está na rodada corrente".
        val matchday = Matchday.ofMatchId(matchId)
            ?: throw IllegalArgumentException("Partida $matchId inválida")
        val round = leagueRepo.findRound(matchday)
            ?: throw IllegalArgumentException("Partida $matchId não encontrada")
        val match = round.matches.find { it.matchId == matchId }
            ?: throw IllegalArgumentException("Partida $matchId não encontrada")

        // Gate de prontidão (issue #151): a seed é o `matchId`, então simular
        // um encaixe ainda `Scheduled` mostraria o resultado ANTES de ele ser
        // jogado — dava para trocar a escalação até ver uma vitória. Encaixe
        // `Finished` re-simula com os elencos de agora (mesma ressalva do
        // replay da rodada: pode divergir se o elenco mudou depois).
        if (!round.isReleased) return MatchStream.NotStarted

        val home = clubRepo.findById(match.homeClubId)
        val away = clubRepo.findById(match.awayClubId)
        val setup = MatchSetup(
            home = home?.startingLineup() ?: emptyLineup(),
            away = away?.startingLineup() ?: emptyLineup(),
            homeName = match.homeClubName,
            awayName = match.awayClubName,
            // Mesmo modo da rodada: jogo de copa reproduz a mesma disputa de
            // pênaltis no drill-down (issue #61).
            knockout = match.isKnockout,
        )

        // Cronometra só a geração dos eventos da engine. Materializamos ANTES de
        // emitir porque o WS handler intercala ~80ms/minuto para dar sensação de
        // "ao vivo" — cronometrar o emit mediria a animação, não a simulação
        // (mesmo cuidado do PlayRoundService). São ~poucas dezenas de eventos.
        // `flow { }` é frio: nada roda até o adapter coletar.
        return MatchStream.Live(
            flow {
                val sample = Timer.start(meterRegistry)
                val events = simulator.simulate(setup, Random(matchId)).toList()
                sample.stop(simulationTimer)

                for (event in events) emit(event)
            },
        )
    }

    private fun emptyLineup() =
        Lineup(players = emptyList(), formation = Formation.F_4_4_2)
}
