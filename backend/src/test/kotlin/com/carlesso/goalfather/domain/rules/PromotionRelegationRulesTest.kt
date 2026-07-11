package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regra de promoção/rebaixamento (issue #47) — teste PURO de domínio, sem
 * Spring, com tabelas finais conhecidas (critério de aceite da issue).
 */
class PromotionRelegationRulesTest {

    /** Tabela com clubes já na ordem final (posição 1..N). */
    private fun table(division: Int, vararg clubIds: Long) = Standings(
        season = 2026,
        round = 5,
        division = Division(division),
        rows = clubIds.mapIndexed { i, id ->
            StandingRow(position = i + 1, clubId = ClubId(id), clubName = "Club $id")
        },
    )

    @Test
    fun `ultimos da divisao de cima trocam com os primeiros da de baixo`() {
        val next = applyPromotionRelegation(
            listOf(
                table(1, 1, 2, 3, 4, 5, 6),
                table(2, 7, 8, 9, 10, 11, 12),
            ),
        )

        // 5 e 6 caem; 7 e 8 sobem; os demais ficam onde estão.
        assertEquals(Division(2), next[ClubId(5)])
        assertEquals(Division(2), next[ClubId(6)])
        assertEquals(Division(1), next[ClubId(7)])
        assertEquals(Division(1), next[ClubId(8)])
        assertEquals(Division(1), next[ClubId(1)])
        assertEquals(Division(2), next[ClubId(9)])
        assertEquals(Division(2), next[ClubId(12)])

        // Balanceado: cada divisão continua com 6 clubes.
        assertEquals(6, next.values.count { it == Division(1) })
        assertEquals(6, next.values.count { it == Division(2) })
    }

    @Test
    fun `tres divisoes encadeiam as trocas entre pares adjacentes`() {
        val next = applyPromotionRelegation(
            listOf(
                table(1, 1, 2, 3, 4),
                table(2, 5, 6, 7, 8),
                table(3, 9, 10, 11, 12),
            ),
        )

        assertEquals(Division(2), next[ClubId(3)]) // caiu da 1ª
        assertEquals(Division(2), next[ClubId(4)])
        assertEquals(Division(1), next[ClubId(5)]) // subiu para a 1ª
        assertEquals(Division(1), next[ClubId(6)])
        assertEquals(Division(3), next[ClubId(7)]) // caiu da 2ª
        assertEquals(Division(3), next[ClubId(8)])
        assertEquals(Division(2), next[ClubId(9)]) // subiu para a 2ª
        assertEquals(Division(2), next[ClubId(10)])
        assertEquals(Division(3), next[ClubId(11)])
    }

    @Test
    fun `divisao unica nao move ninguem`() {
        val next = applyPromotionRelegation(listOf(table(1, 1, 2, 3, 4)))
        assertEquals(setOf(Division.FIRST), next.values.toSet())
    }

    @Test
    fun `mesma tabela final produz sempre a mesma recomposicao (determinismo)`() {
        val tables = listOf(table(1, 1, 2, 3, 4, 5, 6), table(2, 7, 8, 9, 10, 11, 12))
        // Ordem de entrada das tabelas não importa.
        assertEquals(applyPromotionRelegation(tables), applyPromotionRelegation(tables.reversed()))
    }

    @Test
    fun `divisao duplicada e rejeitada`() {
        assertThrows<IllegalArgumentException> {
            applyPromotionRelegation(listOf(table(1, 1, 2), table(1, 3, 4)))
        }
    }

    @Test
    fun `buraco na piramide (tiers nao contiguos) e rejeitado`() {
        // Divisões 1 e 3 sem a 2: erro de seed — a regra não deve "pontear".
        assertThrows<IllegalArgumentException> {
            applyPromotionRelegation(listOf(table(1, 1, 2), table(3, 3, 4)))
        }
    }

    @Test
    fun `vagas da zona seguem a posicao da divisao na piramide`() {
        assertEquals(0, promotionSpotsFor(Division(1)))
        assertEquals(PROMOTION_RELEGATION_SPOTS, promotionSpotsFor(Division(2)))
        assertEquals(PROMOTION_RELEGATION_SPOTS, relegationSpotsFor(Division(1), divisionCount = 2))
        assertEquals(0, relegationSpotsFor(Division(2), divisionCount = 2))
        // Divisão única: sem zona nenhuma.
        assertEquals(0, relegationSpotsFor(Division(1), divisionCount = 1))
    }
}
