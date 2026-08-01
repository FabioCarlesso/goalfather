package com.carlesso.goalfather.domain.engine

import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.rules.INJURY_DURATION
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Espelha frontend/src/mocks/engine.test.ts — mesmos invariantes,
 * mesma forma. Quando o backend Spring expor o WS real, o frontend
 * deliga o mock e estes testes (mais o E2E Playwright) garantem que
 * o swap mantem o contrato.
 */
class MatchSimulatorTest {

    private fun player(id: Long, position: Position, overall: Int) = Player(
        id = PlayerId(id),
        name = "Player$id",
        position = position,
        overall = overall,
        pace = overall,
        shooting = overall,
        passing = overall,
        defending = overall,
        salary = 10_000,
        age = 25,
    )

    private val homeLineup = Lineup(
        formation = Formation.F_4_4_2,
        players = listOf(
            player(1, Position.GK, 78),
            player(2, Position.CB, 75),
            player(3, Position.CB, 75),
            player(4, Position.CB, 72),
            player(5, Position.CB, 70),
            player(6, Position.MF, 80),
            player(7, Position.MF, 78),
            player(8, Position.MF, 76),
            player(9, Position.MF, 74),
            player(10, Position.FW, 88),
            player(11, Position.FW, 79),
        ),
    )

    private val awayLineup = Lineup(
        formation = Formation.F_4_4_2,
        players = (101L..111L).map { player(it, Position.MF, 75) },
    )

    private val setup = MatchSetup(
        home = homeLineup,
        away = awayLineup,
        homeName = "Goal Father FC",
        awayName = "Atlético Bonsucesso",
    )

    @Test
    fun `primeiro evento sempre eh KickOff com metadata dos times`() = runTest {
        val events = MatchSimulator().simulate(setup, Random(42)).toList()

        val first = events.first()
        assertIs<MatchEvent.KickOff>(first)
        assertEquals("Goal Father FC", first.homeClubName)
        assertEquals("Atlético Bonsucesso", first.awayClubName)
        assertEquals(0, first.minute)
        // teamStrength da lineup home (média de overall)
        assertTrue(first.homeStrength in 75.0..78.0)
        assertEquals(75.0, first.awayStrength)
    }

    @Test
    fun `ultimo evento sempre eh FullTime no minuto 90`() = runTest {
        val events = MatchSimulator().simulate(setup, Random(42)).toList()

        val last = events.last()
        assertIs<MatchEvent.FullTime>(last)
        assertEquals(90, last.minute)
    }

    @Test
    fun `total de gols casa com placar do FullTime`() = runTest {
        val events = MatchSimulator().simulate(setup, Random(42)).toList()

        val homeGoals = events.filterIsInstance<MatchEvent.Goal>().count { it.home }
        val awayGoals = events.filterIsInstance<MatchEvent.Goal>().count { !it.home }
        val fullTime = events.filterIsInstance<MatchEvent.FullTime>().single()

        assertEquals(homeGoals, fullTime.homeGoals)
        assertEquals(awayGoals, fullTime.awayGoals)
    }

    @Test
    fun `mesma seed produz mesma sequencia (determinismo)`() = runTest {
        val events1 = MatchSimulator().simulate(setup, Random(42)).toList()
        val events2 = MatchSimulator().simulate(setup, Random(42)).toList()
        assertEquals(events1, events2)
    }

    @Test
    fun `seeds diferentes geram sequencias diferentes`() = runTest {
        val sim = MatchSimulator()
        val events1 = sim.simulate(setup, Random(1)).toList()
        val events2 = sim.simulate(setup, Random(99)).toList()
        assertNotEquals(events1, events2)
    }

    @Test
    fun `minutos sao monotonicamente nao-decrescentes`() = runTest {
        val events = MatchSimulator().simulate(setup, Random(42)).toList()

        for (i in 1 until events.size) {
            assertTrue(
                events[i].minute >= events[i - 1].minute,
                "Minutos não-decrescentes: ${events[i - 1].minute} -> ${events[i].minute}",
            )
        }
    }

    @Test
    fun `minutos ficam em 0 a 90`() = runTest {
        val events = MatchSimulator().simulate(setup, Random(42)).toList()
        for (event in events) {
            assertTrue(event.minute in 0..90, "Minuto fora do range: ${event.minute}")
        }
    }

    @Test
    fun `when exaustivo sobre MatchEvent compila sem else`() = runTest {
        // Este teste eh principalmente uma garantia de COMPILACAO: se uma
        // nova variante for adicionada ao sealed interface MatchEvent, o
        // when abaixo deixa de compilar sem `else`. Esse eh exatamente o
        // ponto do sealed: o compilador forca cobertura.
        val events = MatchSimulator().simulate(setup, Random(42)).toList()

        for (event in events) {
            val label: String = when (event) {
                is MatchEvent.KickOff -> "kick"
                is MatchEvent.Goal -> "goal"
                is MatchEvent.Card -> "card"
                is MatchEvent.Injury -> "injury"
                is MatchEvent.Save -> "save"
                is MatchEvent.FullTime -> "end"
            }
            assertTrue(label.isNotBlank())
        }
    }

    @Test
    fun `gols sao atribuidos a jogadores dos squads corretos`() = runTest {
        val events = MatchSimulator().simulate(setup, Random(42)).toList()
        val homeIds = homeLineup.players.map { it.id }.toSet()
        val awayIds = awayLineup.players.map { it.id }.toSet()

        for (goal in events.filterIsInstance<MatchEvent.Goal>()) {
            if (goal.home) {
                assertTrue(goal.scorerId in homeIds, "Gol home com scorer fora do squad home: ${goal.scorerId}")
            } else {
                assertTrue(goal.scorerId in awayIds, "Gol away com scorer fora do squad away: ${goal.scorerId}")
            }
        }
    }

    @Test
    fun `lesao sai do evento ja com a duracao sorteada (issue 54)`() = runTest {
        // Varre várias seeds para garantir que pelo menos uma partida tenha lesão.
        val injuries = (1..200).flatMap { seed ->
            MatchSimulator().simulate(setup, Random(seed.toLong())).toList()
                .filterIsInstance<MatchEvent.Injury>()
        }

        assertTrue(injuries.isNotEmpty(), "200 partidas deveriam produzir alguma lesão")
        for (injury in injuries) {
            assertTrue(
                injury.roundsOut in INJURY_DURATION,
                "afastamento fora da faixa $INJURY_DURATION: ${injury.roundsOut}",
            )
        }
    }

    @Test
    fun `time mais forte tende a fazer mais gols em multiplas partidas`() = runTest {
        // Teste estatistico: media de 50 partidas com home muito mais forte.
        // Nao garante em uma unica partida (RNG manda), mas a media converge.
        val strongHome = setup.copy(
            home = Lineup(
                formation = Formation.F_4_4_2,
                players = (1L..11L).map { player(it, Position.MF, 95) },
            ),
            away = Lineup(
                formation = Formation.F_4_4_2,
                players = (101L..111L).map { player(it, Position.MF, 50) },
            ),
        )

        var totalHomeGoals = 0
        var totalAwayGoals = 0
        for (seed in 1..50) {
            val events = MatchSimulator().simulate(strongHome, Random(seed.toLong())).toList()
            val ft = events.filterIsInstance<MatchEvent.FullTime>().single()
            totalHomeGoals += ft.homeGoals
            totalAwayGoals += ft.awayGoals
        }

        assertTrue(
            totalHomeGoals > totalAwayGoals,
            "Time muito mais forte deve marcar mais em 50 jogos. Home: $totalHomeGoals, Away: $totalAwayGoals",
        )
    }
}
