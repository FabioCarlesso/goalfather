package com.carlesso.goalfather.domain.engine

import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.teamStrength
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Setup imutável de uma partida.
 *
 * `homeName`/`awayName` são parâmetros porque a Lineup não carrega
 * o nome do clube (responsabilidade do nível acima). Mantém o domínio
 * focado no que precisa para simular: força + elenco.
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
 * - **Aleatoriedade injetada**: `Random` por construtor. Testes passam
 *   `Random(seed)` para resultados determinísticos. Sem isso, seria
 *   impossível ter testes confiáveis sobre fluxo aleatório.
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
class MatchSimulator(private val rng: Random = Random.Default) {

    fun simulate(setup: MatchSetup): Flow<MatchEvent> = flow {
        val homeStrength = setup.home.teamStrength()
        val awayStrength = setup.away.teamStrength()

        emit(
            MatchEvent.KickOff(
                minute = 0,
                homeClubName = setup.homeName,
                awayClubName = setup.awayName,
                homeStrength = homeStrength,
                awayStrength = awayStrength,
            ),
        )

        var homeGoals = 0
        var awayGoals = 0
        val totalStrength = homeStrength + awayStrength
        val homeRatio = if (totalStrength > 0) homeStrength / totalStrength else 0.5

        for (minute in 1..90) {
            if (rng.nextDouble() >= EVENT_RATE) continue

            val roll = rng.nextDouble()
            when {
                roll < P_GOAL -> {
                    val isHome = rng.nextDouble() < homeRatio
                    val squad = if (isHome) setup.home.players else setup.away.players
                    if (squad.isEmpty()) continue
                    val scorer = squad[rng.nextInt(squad.size)]
                    if (isHome) homeGoals++ else awayGoals++
                    emit(MatchEvent.Goal(minute = minute, scorerId = scorer.id, home = isHome))
                }
                roll < P_GOAL + P_SAVE -> {
                    emit(MatchEvent.Save(minute = minute))
                }
                roll < P_GOAL + P_SAVE + P_CARD -> {
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
                    emit(MatchEvent.Injury(minute = minute, playerId = player.id))
                }
            }
        }

        emit(MatchEvent.FullTime(minute = 90, homeGoals = homeGoals, awayGoals = awayGoals))
    }

    companion object {
        const val EVENT_RATE: Double = 0.12
        const val P_GOAL: Double = 0.32
        const val P_SAVE: Double = 0.30
        const val P_CARD: Double = 0.22

        // P_INJURY = 1.0 - P_GOAL - P_SAVE - P_CARD = 0.16 (implícito no else)
        const val P_RED_CARD: Double = 0.12
    }
}
