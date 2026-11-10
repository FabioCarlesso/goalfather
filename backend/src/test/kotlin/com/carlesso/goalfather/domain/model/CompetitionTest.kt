package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.rules.cupFormat
import com.carlesso.goalfather.domain.rules.cupDrawSeed
import com.carlesso.goalfather.domain.rules.drawCup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CompetitionTest {

    private val a = ClubId(1)
    private val b = ClubId(2)

    // ─── Ida e volta ──────────────────────────────────────────────────

    @Test
    fun `agregado decide o classificado de ida e volta`() {
        // Ida A 2×1 B, volta B 2×0 A: agregado A 2 × 3 B.
        val tie = TwoLeggedTieResult(a, b, LegScore(2, 1), LegScore(2, 0))
        assertEquals(2, tie.aggregateFirst)
        assertEquals(3, tie.aggregateSecond)
        assertEquals(b, tie.winner)
    }

    @Test
    fun `agregado empatado decide nos penaltis cobrados na volta`() {
        // Agregado 2×2; pênaltis na orientação da volta: B (mandante) 3 × 4 A.
        val tie = TwoLeggedTieResult(a, b, LegScore(1, 1), LegScore(1, 1), penalties = PenaltyScore(home = 3, away = 4))
        assertEquals(a, tie.winner)
    }

    @Test
    fun `agregado empatado sem penaltis e penaltis com agregado decidido sao irrepresentaveis`() {
        assertFailsWith<IllegalArgumentException> { TwoLeggedTieResult(a, b, LegScore(1, 0), LegScore(1, 0)) }
        assertFailsWith<IllegalArgumentException> {
            TwoLeggedTieResult(a, b, LegScore(2, 0), LegScore(0, 0), penalties = PenaltyScore(4, 3))
        }
    }

    // ─── Validação dos formatos ───────────────────────────────────────

    @Test
    fun `mata-mata com entrantes no meio fecha a conta fase a fase, como a Copa do Brasil`() {
        // 8 clubes jogam a 1ª fase em jogo único; os 4 vencedores + 12 da "Série A" fazem as oitavas
        // em ida e volta; final única.
        val format = CompetitionFormat.Knockout(
            listOf(
                KnockoutStage("1ª fase", 8, Legs.SINGLE, newEntrants = 8),
                KnockoutStage("Oitavas", 16, Legs.TWO_LEGGED, newEntrants = 12),
                KnockoutStage("Quartas", 8, Legs.TWO_LEGGED),
                KnockoutStage("Semi", 4, Legs.TWO_LEGGED),
                KnockoutStage("Final", 2, Legs.SINGLE),
            ),
        )
        assertEquals(20, format.entrants)
    }

    @Test
    fun `mata-mata cuja conta nao fecha e recusado`() {
        assertFailsWith<IllegalArgumentException> {
            CompetitionFormat.Knockout(
                listOf(KnockoutStage("Semi", 4, newEntrants = 4), KnockoutStage("Final", 2, newEntrants = 1)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CompetitionFormat.Knockout(listOf(KnockoutStage("Quartas", 8, newEntrants = 8), KnockoutStage("Semi", 4)))
        }
    }

    @Test
    fun `fase de grupos exige que os classificados formem a 1a fase do mata-mata`() {
        val knockout = CompetitionFormat.Knockout(listOf(KnockoutStage("Semi", 4, newEntrants = 4), KnockoutStage("Final", 2)))
        CompetitionFormat.GroupsThenKnockout(groups = 2, perGroup = 4, qualifiersPerGroup = 2, knockout = knockout)
        assertFailsWith<IllegalArgumentException> {
            CompetitionFormat.GroupsThenKnockout(groups = 4, perGroup = 4, qualifiersPerGroup = 2, knockout = knockout)
        }
    }

    @Test
    fun `fase suica exige jogos pares e menos jogos que adversarios`() {
        assertFailsWith<IllegalArgumentException> { CompetitionFormat.SwissLeague(36, 7, 8, 9..24) }
        assertFailsWith<IllegalArgumentException> { CompetitionFormat.SwissLeague(6, 6, 2, 3..4) }
    }

    @Test
    fun `competicao comeca a tabela por pontos e nao repete criterio`() {
        val format = CompetitionFormat.RoundRobin(legs = 2)
        assertFailsWith<IllegalArgumentException> {
            Competition(CompetitionId("x"), "X", format, listOf(Tiebreaker.WINS, Tiebreaker.POINTS))
        }
        assertFailsWith<IllegalArgumentException> {
            Competition(CompetitionId("x"), "X", format, listOf(Tiebreaker.POINTS, Tiebreaker.WINS, Tiebreaker.WINS))
        }
    }

    // ─── A copa atual no formato genérico ─────────────────────────────

    @Test
    fun `cupFormat descreve a mesma chave que o drawCup sorteia`() {
        for (count in 4..32) {
            val clubs = (1..count).map {
                Club(ClubId(it.toLong()), "C$it", cash = Money.ZERO, stadiumCapacity = 0, squad = emptyList())
            }
            val drawn = drawCup(2026, clubs, Random(cupDrawSeed(2026))).stages.single()
            val format = cupFormat(count)

            val fixtures = drawn.ties.count { it is CupTie.Fixture }
            val byes = drawn.ties.count { it is CupTie.Bye }
            assertEquals(count, format.entrants, "$count clubes")
            assertEquals(fixtures, format.stages.first().ties, "$count clubes: jogos da 1ª fase")
            assertEquals(byes, format.stages.getOrNull(1)?.newEntrants ?: 0, "$count clubes: folgas")
            assertEquals(drawn.phase.teams / 2, format.stages[1].teams, "$count clubes: 2ª fase")
        }
    }
}
