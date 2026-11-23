package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.Employment
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.test.makePlayer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** O pool de livres (issue #139): envelhecimento e busca, sem Spring. */
class MarketPoolRulesTest {

    private fun free(
        id: Long,
        name: String = "Livre $id",
        position: Position = Position.MF,
        overall: Int = 70,
        age: Int = 25,
        price: Long = 0,
        nationality: com.carlesso.goalfather.domain.model.Country = Countries.BRAZIL,
    ) = MarketEntry(
        player = makePlayer(id, position = position, overall = overall, age = age)
            .copy(name = name, employment = Employment.FreeAgent, nationality = nationality),
        price = Money(price),
    )

    // ─── Envelhecimento ────────────────────────────────────────────────────

    @Test
    fun `livre ganha um ano e conta mais uma temporada sem clube`() {
        val aging = free(1, age = 24).agedInPool(Random(1), retireAfter = 3)

        val listed = assertIs<PoolAging.StillListed>(aging)
        assertEquals(25, listed.entry.player.age)
        assertEquals(1, listed.entry.seasonsWithoutClub)
    }

    @Test
    fun `quem chega ao limite sem clube se aposenta mesmo jovem`() {
        val young = free(1, age = 22)

        assertIs<PoolAging.Retired>(young.copy(seasonsWithoutClub = 2).agedInPool(Random(1), retireAfter = 3))
        assertIs<PoolAging.StillListed>(young.copy(seasonsWithoutClub = 1).agedInPool(Random(1), retireAfter = 3))
        // No jogo, a primeira virada inteira sem clube já aposenta.
        assertIs<PoolAging.Retired>(young.agedInPool(Random(1)))
    }

    @Test
    fun `veterano fraco se aposenta pela idade no pool`() {
        assertIs<PoolAging.Retired>(free(1, overall = 55, age = 39).agedInPool(Random(1)))
    }

    // ─── Busca ─────────────────────────────────────────────────────────────

    private val pool = listOf(
        free(1, "José Muñoz", Position.GK, overall = 60, age = 19, nationality = Countries.SPAIN),
        free(2, "Ana Lima", Position.CB, overall = 75, age = 27),
        free(3, "Bruno Costa", Position.CB, overall = 68, age = 33),
        free(4, "Carlos Dias", Position.FW, overall = 80, age = 24, price = 5_000_000),
        free(5, "Davi Rocha", Position.MF, overall = 72, age = 21),
    )

    @Test
    fun `sem filtro lista tudo por preco e depois nome`() {
        val page = pool.search(MarketFilter(), page = 0, size = 10)

        assertEquals(listOf(2L, 3L, 5L, 1L, 4L), page.items.map { it.player.id.value })
        assertEquals(5, page.totalItems)
        assertEquals(1, page.totalPages)
    }

    @Test
    fun `filtra por posicao nacionalidade e faixa de idade`() {
        assertEquals(listOf(2L, 3L), pool.search(MarketFilter(position = Position.CB), 0, 10).ids())
        assertEquals(listOf(1L), pool.search(MarketFilter(nationality = CountryCode("ESP")), 0, 10).ids())
        assertEquals(listOf(2L, 5L, 4L), pool.search(MarketFilter(minAge = 20, maxAge = 30), 0, 10).ids())
    }

    @Test
    fun `filtra pela faixa de valor de referencia e pelo preco`() {
        val values = pool.associate { it.player.id.value to marketReferenceValue(it.player) }
        val min = values.getValue(5L) // o meia de 72 e 21 anos
        val filtered = pool.search(MarketFilter(minValue = min), 0, 10)

        assertTrue(filtered.items.all { marketReferenceValue(it.player) >= min })
        assertTrue(5L in filtered.ids() && 1L !in filtered.ids(), "${filtered.ids()}")
        assertEquals(listOf(2L, 3L, 5L, 1L), pool.search(MarketFilter(maxPrice = Money.ZERO), 0, 10).ids())
    }

    @Test
    fun `busca por nome ignora acento e caixa`() {
        assertEquals(listOf(1L), pool.search(MarketFilter(query = "munoz"), 0, 10).ids())
        assertEquals(listOf(1L), pool.search(MarketFilter(query = "  JOSÉ "), 0, 10).ids())
    }

    @Test
    fun `ordena por overall decrescente com desempate estavel`() {
        val page = pool.search(MarketFilter(sort = MarketSort.OVERALL, descending = true), 0, 10)
        assertEquals(listOf(4L, 2L, 5L, 3L, 1L), page.ids())
    }

    @Test
    fun `pagina sem repetir nem perder jogador`() {
        val first = pool.search(MarketFilter(sort = MarketSort.AGE), page = 0, size = 2)
        val second = pool.search(MarketFilter(sort = MarketSort.AGE), page = 1, size = 2)
        val third = pool.search(MarketFilter(sort = MarketSort.AGE), page = 2, size = 2)

        assertEquals(listOf(1L, 5L), first.ids())
        assertEquals(listOf(4L, 2L), second.ids())
        assertEquals(listOf(3L), third.ids())
        assertEquals(3, first.totalPages)
        // Página além do fim vem vazia, com o total certo.
        assertEquals(emptyList(), pool.search(MarketFilter(), page = 9, size = 2).ids())
        assertEquals(1, emptyList<MarketEntry>().search(MarketFilter(), 0, 20).totalPages)
    }

    @Test
    fun `pagina e tamanho invalidos sao recusados`() {
        assertFailsWith<IllegalArgumentException> { pool.search(MarketFilter(), page = -1, size = 10) }
        assertFailsWith<IllegalArgumentException> { pool.search(MarketFilter(), page = 0, size = 0) }
    }

    private fun MarketPage.ids(): List<Long> = items.map { it.player.id.value }
}
