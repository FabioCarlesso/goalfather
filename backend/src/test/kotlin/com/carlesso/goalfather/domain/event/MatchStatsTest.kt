package com.carlesso.goalfather.domain.event

import com.carlesso.goalfather.domain.model.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Sumário da partida (issue #57) — projeção pura do stream de eventos.
 */
class MatchStatsTest {

    private val events = listOf(
        MatchEvent.KickOff(
            homeClubName = "A",
            awayClubName = "B",
            homeStrength = 75.0,
            awayStrength = 70.0,
        ),
        MatchEvent.Goal(minute = 10, scorerId = PlayerId(10), home = true),
        MatchEvent.Miss(minute = 15, playerId = PlayerId(11), home = true),
        // Defesa do goleiro visitante: finalização no gol do mandante.
        MatchEvent.Save(minute = 20, goalkeeperId = PlayerId(101), home = false),
        MatchEvent.Card(minute = 30, playerId = PlayerId(102), red = false, home = false),
        MatchEvent.Card(minute = 33, playerId = PlayerId(103), red = true, home = false),
        MatchEvent.Injury(minute = 40, playerId = PlayerId(5), roundsOut = 2),
    )

    @Test
    fun `conta finalizacoes defesas e cartoes de cada lado`() {
        val stats = events.matchStats()

        assertEquals(
            TeamStats(shots = 3, shotsOnTarget = 2, saves = 0, yellowCards = 0, redCards = 0),
            stats.home,
        )
        assertEquals(
            TeamStats(shots = 0, shotsOnTarget = 0, saves = 1, yellowCards = 1, redCards = 1),
            stats.away,
        )
    }

    @Test
    fun `defesa conta para os dois lados`() {
        // A mesma jogada é defesa de quem pegou e finalização no gol de quem
        // chutou — a assimetria mais fácil de errar no agregado.
        val stats = listOf(
            MatchEvent.Save(minute = 5, goalkeeperId = PlayerId(1), home = true),
        ).matchStats()

        assertEquals(1, stats.home.saves)
        assertEquals(0, stats.home.shots)
        assertEquals(1, stats.away.shots)
        assertEquals(1, stats.away.shotsOnTarget)
        assertEquals(0, stats.away.saves)
    }

    @Test
    fun `stream vazio produz sumario zerado`() {
        assertEquals(MatchStats.EMPTY, emptyList<MatchEvent>().matchStats())
    }

    @Test
    fun `KickOff Injury e FullTime nao alteram o sumario`() {
        val neutral = listOf(
            events.first(),
            MatchEvent.Injury(minute = 40, playerId = PlayerId(5), roundsOut = 2),
            MatchEvent.FullTime(homeGoals = 0, awayGoals = 0, stats = MatchStats.EMPTY),
        )
        assertEquals(MatchStats.EMPTY, neutral.matchStats())
    }
}
