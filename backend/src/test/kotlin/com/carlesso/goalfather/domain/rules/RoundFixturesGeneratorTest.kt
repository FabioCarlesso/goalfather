package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.test.makeClub
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoundFixturesGeneratorTest {

    private val sixClubs = (1L..6L).map { makeClub(id = it, name = "Club $it") }

    @Test
    fun `6 clubes geram 3 partidas por rodada`() {
        val round = generateRound(roundNumber = 1, season = 2026, clubs = sixClubs)
        assertEquals(3, round.matches.size)
        assertEquals(RoundStatus.Scheduled, round.status)
        assertEquals(1, round.number)
        assertEquals(2026, round.season)
    }

    @Test
    fun `turno de 5 rodadas cobre cada confronto exatamente uma vez`() {
        // Round-robin de turno único: C(6,2) = 15 confrontos em 5 rodadas de 3 jogos.
        val seenPairs = mutableSetOf<Set<Long>>()
        for (n in 1..5) {
            val round = generateRound(roundNumber = n, season = 2026, clubs = sixClubs)
            for (m in round.matches) {
                val pair = setOf(m.homeClubId.value, m.awayClubId.value)
                assertTrue(seenPairs.add(pair), "Par repetido no turno: $pair (rodada $n)")
                assertTrue(m.homeClubId != m.awayClubId, "Clube enfrentando a si mesmo")
            }
        }
        assertEquals(15, seenPairs.size, "Turno deve ter 15 confrontos distintos")
    }

    @Test
    fun `clube de menor id permanece fixo (Berger)`() {
        // O clube 1 aparece em todas as rodadas (nunca folga num turno par).
        for (n in 1..5) {
            val round = generateRound(roundNumber = n, season = 2026, clubs = sixClubs)
            val clubsInRound = round.matches.flatMap { listOf(it.homeClubId.value, it.awayClubId.value) }
            assertTrue(1L in clubsInRound, "Clube 1 deveria jogar na rodada $n")
        }
    }

    @Test
    fun `mesmo input produz exatamente o mesmo schedule (determinismo)`() {
        val a = generateRound(3, 2026, sixClubs)
        val b = generateRound(3, 2026, sixClubs.shuffled()) // ordem de entrada não importa
        assertEquals(a, b)
    }

    @Test
    fun `matchId segue o padrao roundNumber 1000 + indice`() {
        val round = generateRound(2, 2026, sixClubs)
        assertEquals(listOf(2001L, 2002L, 2003L), round.matches.map { it.matchId })
    }

    @Test
    fun `numero impar de clubes e rejeitado`() {
        val fiveClubs = (1L..5L).map { makeClub(id = it) }
        assertThrows<IllegalArgumentException> { generateRound(1, 2026, fiveClubs) }
    }
}
