package com.carlesso.goalfather.domain.engine

import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.matchStats
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Posture
import com.carlesso.goalfather.domain.model.Tactics
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
                is MatchEvent.Miss -> "miss"
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

    // ─── Tática (issue #56) ────────────────────────────────────────────────

    private fun MatchSetup.withPostures(home: Posture, away: Posture) = copy(
        home = this.home.copy(tactics = Tactics(home)),
        away = this.away.copy(tactics = Tactics(away)),
    )

    private suspend fun goalsIn(setup: MatchSetup, seeds: IntRange): Int =
        seeds.sumOf { seed ->
            val ft = MatchSimulator().simulate(setup, Random(seed.toLong())).toList()
                .filterIsInstance<MatchEvent.FullTime>().single()
            ft.homeGoals + ft.awayGoals
        }

    @Test
    fun `KickOff carrega a postura de cada lado`() = runTest {
        val tactical = setup.withPostures(Posture.OFFENSIVE, Posture.DEFENSIVE)
        val kickOff = MatchSimulator().simulate(tactical, Random(42)).toList()
            .filterIsInstance<MatchEvent.KickOff>().single()

        assertEquals(Posture.OFFENSIVE, kickOff.homePosture)
        assertEquals(Posture.DEFENSIVE, kickOff.awayPosture)
    }

    @Test
    fun `mesma seed com posturas diferentes produz partidas diferentes`() = runTest {
        val balanced = MatchSimulator().simulate(setup, Random(42)).toList()
        val offensive = MatchSimulator()
            .simulate(setup.withPostures(Posture.OFFENSIVE, Posture.OFFENSIVE), Random(42))
            .toList()

        assertNotEquals(balanced, offensive)

        // ...e reprodutível: a mesma tática com a mesma seed repete o jogo.
        val again = MatchSimulator()
            .simulate(setup.withPostures(Posture.OFFENSIVE, Posture.OFFENSIVE), Random(42))
            .toList()
        assertEquals(offensive, again)
    }

    @Test
    fun `EQUILIBRADA em 4-4-2 preserva a simulacao neutra`() = runTest {
        // Regressão: a tática é uma CAMADA sobre a engine, não uma troca dela.
        // O eixo neutro tem de dar exatamente a partida de antes da issue #56.
        val explicit = setup.withPostures(Posture.BALANCED, Posture.BALANCED)
        assertEquals(
            MatchSimulator().simulate(setup, Random(7)).toList(),
            MatchSimulator().simulate(explicit, Random(7)).toList(),
        )
    }

    @Test
    fun `jogo aberto rende mais gols que dois times fechados`() = runTest {
        // Teste estatístico (100 partidas): o RNG manda numa partida isolada,
        // mas o volume de gols tem de separar as posturas com folga.
        val open = goalsIn(setup.withPostures(Posture.OFFENSIVE, Posture.OFFENSIVE), 1..100)
        val closed = goalsIn(setup.withPostures(Posture.DEFENSIVE, Posture.DEFENSIVE), 1..100)

        assertTrue(
            open > closed,
            "OFENSIVA×OFENSIVA deveria render mais gols que DEFENSIVA×DEFENSIVA. Aberto: $open, fechado: $closed",
        )
    }

    @Test
    fun `fechar o time reduz os gols do adversario`() = runTest {
        // A postura do RIVAL pesa: o mesmo ataque produz menos contra um time
        // fechado. É o que torna a escolha um confronto, e não um botão isolado.
        suspend fun awayGoals(homePosture: Posture): Int = (1..100).sumOf { seed ->
            MatchSimulator()
                .simulate(setup.withPostures(homePosture, Posture.OFFENSIVE), Random(seed.toLong()))
                .toList()
                .filterIsInstance<MatchEvent.FullTime>()
                .single().awayGoals
        }

        val vsClosed = awayGoals(Posture.DEFENSIVE)
        val vsOpen = awayGoals(Posture.OFFENSIVE)
        assertTrue(
            vsClosed < vsOpen,
            "Adversário ofensivo deveria marcar menos contra time fechado. Fechado: $vsClosed, aberto: $vsOpen",
        )
    }

    @Test
    fun `formacao inclina o placar mesmo com a mesma postura`() = runTest {
        val attacking = setup.copy(home = homeLineup.copy(formation = Formation.F_4_3_3))
        val defending = setup.copy(home = homeLineup.copy(formation = Formation.F_5_3_2))

        assertTrue(
            goalsIn(attacking, 1..100) > goalsIn(defending, 1..100),
            "4-3-3 deveria render mais gols no agregado que 5-3-2",
        )
    }

    // ─── Artilheiro ponderado, novos eventos e sumário (issue #57) ─────────

    private suspend fun eventsOver(seeds: IntRange): List<MatchEvent> =
        seeds.flatMap { seed -> MatchSimulator().simulate(setup, Random(seed.toLong())).toList() }

    @Test
    fun `distribuicao de artilheiros respeita o peso da posicao`() = runTest {
        // 200 partidas: numa isolada o RNG manda, mas no agregado os pesos
        // têm de aparecer. Só o lado da casa entra na conta — o visitante
        // deste setup é todo MF de propósito (serve de controle).
        val byPosition = eventsOver(1..200)
            .filterIsInstance<MatchEvent.Goal>()
            .filter { it.home }
            .groupingBy { homeLineup.players.first { p -> p.id == it.scorerId }.position }
            .eachCount()

        assertEquals(0, byPosition[Position.GK] ?: 0, "goleiro não marca (peso 0)")
        val forwards = byPosition[Position.FW] ?: 0
        val defenders = byPosition[Position.CB] ?: 0
        assertTrue(forwards > 0, "atacantes deveriam marcar em 200 partidas")
        assertTrue(
            forwards > defenders,
            "atacante (2 em campo, peso 6) deve marcar mais que zagueiro (4 em campo, peso 1). FW: $forwards, CB: $defenders",
        )
    }

    @Test
    fun `chute para fora sai com autor do squad correto`() = runTest {
        val misses = eventsOver(1..50).filterIsInstance<MatchEvent.Miss>()
        val homeIds = homeLineup.players.map { it.id }.toSet()
        val awayIds = awayLineup.players.map { it.id }.toSet()

        assertTrue(misses.isNotEmpty(), "50 partidas deveriam produzir algum chute para fora")
        for (miss in misses) {
            val squad = if (miss.home) homeIds else awayIds
            assertTrue(miss.playerId in squad, "Miss com autor fora do squad: ${miss.playerId}")
        }
        // O chute para fora usa o mesmo sorteio ponderado do gol: goleiro fora.
        val homeKeeper = homeLineup.players.first { it.position == Position.GK }.id
        assertTrue(misses.none { it.playerId == homeKeeper }, "goleiro não finaliza")
    }

    @Test
    fun `defesa identifica o goleiro do lado que defendeu`() = runTest {
        val saves = eventsOver(1..50).filterIsInstance<MatchEvent.Save>()
        val homeKeeper = homeLineup.players.first { it.position == Position.GK }.id

        assertTrue(saves.isNotEmpty(), "50 partidas deveriam produzir alguma defesa")
        for (save in saves) {
            if (save.home) {
                assertEquals(homeKeeper, save.goalkeeperId, "defesa do mandante é do goleiro do mandante")
            } else {
                // O visitante deste setup não tem goleiro escalado (11 MF):
                // ausência modelada como null, sem id inventado.
                assertEquals(null, save.goalkeeperId)
            }
        }
    }

    @Test
    fun `estatisticas do FullTime batem com os eventos emitidos`() = runTest {
        // Propriedade, não valor fixo: o sumário é uma projeção do stream, então
        // recalculá-lo a partir dos eventos tem de dar exatamente o mesmo.
        for (seed in 1..30) {
            val events = MatchSimulator().simulate(setup, Random(seed.toLong())).toList()
            val fullTime = events.filterIsInstance<MatchEvent.FullTime>().single()

            assertEquals(events.dropLast(1).matchStats(), fullTime.stats, "seed $seed")
            // Sanidade cruzada com o placar: todo gol é uma finalização no gol.
            assertTrue(fullTime.stats.home.shotsOnTarget >= fullTime.homeGoals)
            assertTrue(fullTime.stats.away.shotsOnTarget >= fullTime.awayGoals)
        }
    }

    @Test
    fun `escalacao so de goleiros nao produz finalizacao`() = runTest {
        // Caso limite do sorteio ponderado: sem ninguém com peso, o lance não
        // acontece em vez de estourar ou eleger o goleiro na marra.
        val keepersOnly = setup.copy(
            home = Lineup(
                formation = Formation.F_4_4_2,
                players = (1L..11L).map { player(it, Position.GK, 70) },
            ),
        )

        val events = (1..30).flatMap {
            MatchSimulator().simulate(keepersOnly, Random(it.toLong())).toList()
        }
        assertTrue(events.filterIsInstance<MatchEvent.Goal>().none { it.home })
        assertTrue(events.filterIsInstance<MatchEvent.Miss>().none { it.home })
    }
}
