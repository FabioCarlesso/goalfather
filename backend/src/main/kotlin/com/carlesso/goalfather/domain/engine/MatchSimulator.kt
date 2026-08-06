package com.carlesso.goalfather.domain.engine

import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.attackFactor
import com.carlesso.goalfather.domain.model.defenseFactor
import com.carlesso.goalfather.domain.model.teamStrength
import com.carlesso.goalfather.domain.rules.drawInjuryDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Setup imutável de uma partida.
 *
 * `homeName`/`awayName` são parâmetros porque a Lineup não carrega
 * o nome do clube (responsabilidade do nível acima). Mantém o domínio
 * focado no que precisa para simular: força + elenco.
 *
 * A tática de cada lado (issue #56) vem DENTRO da própria `Lineup`
 * (`lineup.tactics`), junto da formação — não há campo separado aqui de
 * propósito: duas fontes para a mesma decisão só criariam divergência.
 */
data class MatchSetup(
    val home: Lineup,
    val away: Lineup,
    val homeName: String,
    val awayName: String,
)

/**
 * Engine de simulação — pura sobre `MatchSetup` + RNG.
 *
 * - **Aleatoriedade injetada por CHAMADA**: `simulate(setup, rng)` recebe
 *   `Random` por parâmetro (default `Random.Default`). Permite reuso da
 *   mesma instância de `MatchSimulator` para várias partidas com seeds
 *   diferentes (ex.: `PlayRoundService` usa `matchId` como seed por
 *   partida para determinismo).
 * - **Sem timing**: retorna `Flow<MatchEvent>` SEM `delay()`. O timing
 *   de stream (80ms/minuto, etc.) é responsabilidade do adapter
 *   WebSocket — não do domínio. Mantém a engine fast em testes e
 *   torna o streaming uma decisão de infraestrutura.
 * - **Zero dependências de framework**: só Kotlin + coroutines.
 *
 * Espelha o algoritmo da engine de mock em
 * `frontend/src/mocks/engine.ts`. Esta é a fonte de verdade — a do
 * frontend será descartada quando os adapters Spring estiverem prontos.
 */
class MatchSimulator {

    fun simulate(setup: MatchSetup, rng: Random = Random.Default): Flow<MatchEvent> = flow {
        val homeStrength = setup.home.teamStrength()
        val awayStrength = setup.away.teamStrength()

        emit(
            MatchEvent.KickOff(
                minute = 0,
                homeClubName = setup.homeName,
                awayClubName = setup.awayName,
                homeStrength = homeStrength,
                awayStrength = awayStrength,
                homePosture = setup.home.tactics.posture,
                awayPosture = setup.away.tactics.posture,
            ),
        )

        var homeGoals = 0
        var awayGoals = 0

        // Peso de gol de cada lado = quanto ELE ataca × quanto o adversário
        // DEIXA atacar (issue #56). É o produto que faz a postura do rival
        // pesar: atacar contra um time fechado rende menos que contra um time
        // aberto — sem isso a escolha seria isolada e não um confronto.
        val homeGoalWeight = setup.home.attackFactor() * setup.away.defenseFactor()
        val awayGoalWeight = setup.away.attackFactor() * setup.home.defenseFactor()

        // A chance de UM lance virar gol acompanha a média dos dois pesos: dois
        // times abertos produzem um jogo de muitos gols, dois fechados um 0x0
        // arrastado. O que sai do gol volta para a defesa (`P_SAVE`) em vez de
        // sumir — assim cartão e lesão mantêm a mesma frequência de sempre, e
        // 1.0/1.0 reproduz EXATAMENTE a engine anterior à tática.
        val goalP = (P_GOAL * (homeGoalWeight + awayGoalWeight) / 2)
            .coerceIn(0.0, P_GOAL + P_SAVE)
        val saveP = P_GOAL + P_SAVE - goalP

        // O mesmo peso reparte os gols entre os times: força × tática.
        val homeShare = homeStrength * homeGoalWeight
        val awayShare = awayStrength * awayGoalWeight
        val homeRatio = if (homeShare + awayShare > 0) homeShare / (homeShare + awayShare) else 0.5

        for (minute in 1..90) {
            if (rng.nextDouble() >= EVENT_RATE) continue

            val roll = rng.nextDouble()
            when {
                roll < goalP -> {
                    val isHome = rng.nextDouble() < homeRatio
                    val squad = if (isHome) setup.home.players else setup.away.players
                    if (squad.isEmpty()) continue
                    val scorer = squad[rng.nextInt(squad.size)]
                    if (isHome) homeGoals++ else awayGoals++
                    emit(MatchEvent.Goal(minute = minute, scorerId = scorer.id, home = isHome))
                }
                roll < goalP + saveP -> {
                    emit(MatchEvent.Save(minute = minute))
                }
                roll < goalP + saveP + P_CARD -> {
                    val isHome = rng.nextDouble() < 0.5
                    val squad = if (isHome) setup.home.players else setup.away.players
                    if (squad.isEmpty()) continue
                    val player = squad[rng.nextInt(squad.size)]
                    emit(
                        MatchEvent.Card(
                            minute = minute,
                            playerId = player.id,
                            red = rng.nextDouble() < P_RED_CARD,
                        ),
                    )
                }
                else -> {
                    val isHome = rng.nextDouble() < 0.5
                    val squad = if (isHome) setup.home.players else setup.away.players
                    if (squad.isEmpty()) continue
                    val player = squad[rng.nextInt(squad.size)]
                    // Duração sorteada aqui, com o RNG da partida (issue #54):
                    // o evento já sai completo e o serviço só aplica.
                    emit(
                        MatchEvent.Injury(
                            minute = minute,
                            playerId = player.id,
                            roundsOut = drawInjuryDuration(rng),
                        ),
                    )
                }
            }
        }

        emit(MatchEvent.FullTime(minute = 90, homeGoals = homeGoals, awayGoals = awayGoals))
    }

    companion object {
        const val EVENT_RATE: Double = 0.12

        /** Chance BASE de um lance virar gol; a tática a escala por partida. */
        const val P_GOAL: Double = 0.32
        const val P_SAVE: Double = 0.30
        const val P_CARD: Double = 0.22

        // P_INJURY = 1.0 - P_GOAL - P_SAVE - P_CARD = 0.16 (implícito no else)
        const val P_RED_CARD: Double = 0.12
    }
}
