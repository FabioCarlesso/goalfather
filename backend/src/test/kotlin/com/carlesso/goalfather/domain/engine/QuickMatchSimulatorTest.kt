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
import com.carlesso.goalfather.domain.model.cents
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Simulação rápida (issue #129): determinismo por seed, coerência interna do
 * resultado e — o principal — a mesma distribuição de resultados da engine
 * completa, medida em 10 mil jogos.
 */
class QuickMatchSimulatorTest {

    private fun player(id: Long, position: Position, overall: Int) = Player(
        id = PlayerId(id),
        name = "Player$id",
        position = position,
        overall = overall,
        pace = overall,
        shooting = overall,
        passing = overall,
        defending = overall,
        salary = 10_000.cents,
        age = 25,
    )

    private fun squad(firstId: Long, overall: Int): List<Player> {
        val positions = listOf(Position.GK) + List(4) { Position.CB } + List(4) { Position.MF } + List(2) { Position.FW }
        return positions.mapIndexed { i, pos -> player(firstId + i, pos, overall) }
    }

    // Mandante mais forte de propósito: a "taxa de vitória do mandante" só
    // discrimina engines diferentes se não for ~50%.
    private val setup = MatchSetup(
        home = Lineup(players = squad(1, 82), formation = Formation.F_4_4_2),
        away = Lineup(players = squad(101, 72), formation = Formation.F_4_4_2),
        homeName = "Home",
        awayName = "Away",
    )

    private val quick = QuickMatchSimulator()

    @Test
    fun `mesma seed da o mesmo resultado`() {
        repeat(50) { seed ->
            assertEquals(
                quick.simulate(setup, Random(seed)),
                quick.simulate(setup, Random(seed)),
                "seed $seed",
            )
        }
    }

    @Test
    fun `placar, destaques e estatisticas contam a mesma historia`() {
        repeat(500) { seed ->
            val result = quick.simulate(setup, Random(seed.toLong()))
            val goals = result.highlights.filterIsInstance<MatchEvent.Goal>()
            assertEquals(result.homeGoals, goals.count { it.home })
            assertEquals(result.awayGoals, goals.count { !it.home })
            // Cartões das estatísticas = cartões dos destaques (a mesma
            // projeção que a engine completa faz do próprio feed).
            val fromHighlights = result.highlights.matchStats()
            assertEquals(fromHighlights.home.yellowCards, result.stats.home.yellowCards)
            assertEquals(fromHighlights.away.redCards, result.stats.away.redCards)
            assertTrue(result.stats.home.shotsOnTarget >= result.homeGoals)
            assertTrue(result.stats.home.shots >= result.stats.home.shotsOnTarget)
            // Cronológico e dentro dos 90 minutos.
            assertEquals(result.highlights.sortedBy { it.minute }, result.highlights)
            assertTrue(result.highlights.all { it.minute in 1..90 })
            assertIs<MatchEvent.FullTime>(result.summary().last())
        }
    }

    @Test
    fun `quem sai de campo nao volta a marcar nem a levar cartao`() {
        repeat(2_000) { seed ->
            val result = quick.simulate(setup, Random(seed.toLong()))
            val sentOff = mutableSetOf<PlayerId>()
            for (event in result.highlights) {
                when (event) {
                    is MatchEvent.Goal -> assertTrue(event.scorerId !in sentOff, "seed $seed: gol de quem saiu")
                    is MatchEvent.Card -> {
                        assertTrue(event.playerId !in sentOff, "seed $seed: cartão em quem saiu")
                        if (event.red) sentOff += event.playerId
                    }
                    is MatchEvent.Injury -> {
                        assertTrue(event.playerId !in sentOff, "seed $seed: lesão em quem saiu")
                        sentOff += event.playerId
                    }
                    else -> error("destaque inesperado: $event")
                }
            }
        }
    }

    @Test
    fun `mata-mata empatado vai aos penaltis e liga nao`() {
        val knockout = setup.copy(knockout = true)
        val draws = (0L until 400L).map { it to quick.simulate(knockout, Random(it)) }
            .filter { (_, r) -> r.homeGoals == r.awayGoals }
        assertTrue(draws.isNotEmpty())
        for ((_, result) in draws) {
            val shootout = assertNotNull(result.shootout)
            assertTrue(shootout.homeScore != shootout.awayScore)
        }
        repeat(200) { assertNull(quick.simulate(setup, Random(it)).shootout) }
    }

    /**
     * O critério estatístico da issue: em 10 mil jogos, média de gols por jogo
     * e taxa de vitória do mandante da rápida ficam perto das da completa.
     *
     * **Tolerância documentada.** As duas engines sorteiam a mesma
     * distribuição (ver KDoc da [QuickMatchSimulator]), então a diferença
     * esperada é só ruído amostral. Com n = 10 000 por lado:
     * - gols/jogo ≈ 3,5 (4,0 com o mandante ofensivo), desvio-padrão ≈ 1,9 →
     *   erro-padrão da DIFERENÇA de médias ≈ 1,9·√(2/n) ≈ 0,027. Tolerância de
     *   **0,10 gol/jogo** (~3,7 σ; ~3 % da média).
     * - vitória do mandante p ≈ 0,45 → erro-padrão da diferença ≈ √(2·p(1-p)/n)
     *   ≈ 0,007. Tolerância de **0,03** (3 p.p., ~4 σ).
     * Medido na implementação: 3,46 × 3,51 gols e 44,2 % × 43,7 % (equilibrada);
     * 4,02 × 4,04 gols e 48,0 % × 48,0 % (ofensiva).
     * Folgado contra falso alarme, apertado o bastante para pegar um erro real
     * de modelo (ex.: esquecer o `homeRatio` ou a taxa de eventos já muda a
     * média de gols em dezenas de por cento). Seeds fixas: o teste é
     * determinístico, a tolerância só blinda contra mudanças futuras de seed.
     *
     * Custo: ~20 mil jogos, dentro da suíte normal (centenas de ms).
     */
    @Test
    fun `em 10 mil jogos a rapida bate a completa em gols e vitoria do mandante`() = runTest {
        val n = 10_000
        val full = MatchSimulator()
        val tactical = setup.copy(
            home = setup.home.copy(tactics = Tactics(posture = Posture.OFFENSIVE)),
        )

        for (s in listOf(setup, tactical)) {
            var fullGoals = 0
            var fullHomeWins = 0
            var quickGoals = 0
            var quickHomeWins = 0
            for (i in 0 until n) {
                val ft = full.simulate(s, Random(i.toLong())).toList().last() as MatchEvent.FullTime
                fullGoals += ft.homeGoals + ft.awayGoals
                if (ft.homeGoals > ft.awayGoals) fullHomeWins++

                // Faixa de seeds diferente: comparar as engines com a MESMA seed
                // não mediria nada — as sequências são consumidas de outro jeito.
                val q = quick.simulate(s, Random(1_000_000L + i))
                quickGoals += q.homeGoals + q.awayGoals
                if (q.homeGoals > q.awayGoals) quickHomeWins++
            }
            val goalDiff = abs(fullGoals.toDouble() / n - quickGoals.toDouble() / n)
            val winDiff = abs(fullHomeWins.toDouble() / n - quickHomeWins.toDouble() / n)
            println(
                "[#129] ${s.home.tactics.posture}: gols/jogo completa=${fullGoals.toDouble() / n} " +
                    "rápida=${quickGoals.toDouble() / n} | vitória mandante completa=${fullHomeWins.toDouble() / n} " +
                    "rápida=${quickHomeWins.toDouble() / n}",
            )
            assertTrue(goalDiff < GOALS_TOLERANCE, "média de gols diverge em $goalDiff (${s.home.tactics.posture})")
            assertTrue(winDiff < HOME_WIN_TOLERANCE, "vitória do mandante diverge em $winDiff (${s.home.tactics.posture})")
        }
    }

    private companion object {
        const val GOALS_TOLERANCE = 0.10
        const val HOME_WIN_TOLERANCE = 0.03
    }
}
