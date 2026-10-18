package com.carlesso.goalfather.application.seed

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.rules.salePayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** O seed REAL do jogo (issue #88), testado sem subir Spring. */
class InitialLeagueTest {

    private val league = initialLeague()
    private val aiClubs = AI_CLUBS_DIV1 + AI_CLUBS_DIV2

    @Test
    fun `nenhum jogador tem nome no formato Clube Player N`() {
        val synthetic = Regex(""".* Player \d+$""")
        val offenders = league.clubs.flatMap { it.squad }.map { it.name }.filter { synthetic.matches(it) }
        assertTrue(offenders.isEmpty(), "nomes sintéticos: $offenders")
    }

    @Test
    fun `nomes sao unicos na liga inteira, incluindo mercado`() {
        val names = league.clubs.flatMap { c -> c.squad.map { it.name } } + league.market.map { it.player.name }
        val duplicates = names.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue(duplicates.isEmpty(), "nomes repetidos: $duplicates")
    }

    @Test
    fun `nenhum jogador do mercado inicial pode ser revendido com lucro`() {
        // Os preços do seed são escritos à mão; o pagamento da venda sai de
        // `TransferRules`. Se um preço de seed ficar abaixo do pagamento,
        // volta a arbitragem compra → venda corrigida na revisão do PR #92.
        for (entry in league.market) {
            assertTrue(
                entry.price > salePayout(entry.player),
                "${entry.player.name}: compra ${entry.price}, venda ${salePayout(entry.player)}",
            )
        }
    }

    @Test
    fun `seed e deterministico`() {
        assertEquals(league, initialLeague())
    }

    @Test
    fun `media de overall de cada clube de IA e exatamente a forca configurada`() {
        for ((id, _, strength) in aiClubs) {
            val squad = league.clubs.single { it.id == ClubId(id) }.squad
            assertEquals(11, squad.size)
            assertEquals(strength * squad.size, squad.sumOf { it.overall }, "clube $id")
        }
    }
}
