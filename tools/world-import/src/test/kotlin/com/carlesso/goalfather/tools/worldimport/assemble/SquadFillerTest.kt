package com.carlesso.goalfather.tools.worldimport.assemble

import com.carlesso.goalfather.adapter.out.worlddata.dto.PlayerDto
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SquadFillerTest {

    private val filler = SquadFiller(seed = 127, referenceDate = LocalDate.of(2026, 7, 1), minSize = 22, minGoalkeepers = 2)

    private fun real(id: String, position: String) =
        PlayerDto(id = id, name = "Real $id", birthDate = "2000-01-01", nationality = "BRA", position = position)

    @Test
    fun `elenco vazio vira o elenco-modelo de 22 com 3 goleiros`() {
        val generated = filler.fill("bra-teste", "BRA", emptyList(), emptySet())

        assertEquals(22, generated.size)
        assertEquals(mapOf("GK" to 3, "DF" to 7, "MF" to 7, "FW" to 5), generated.groupingBy { it.position }.eachCount())
        assertTrue(generated.all { it.generated == true && it.nationality == "BRA" })
        assertEquals("bra-teste-gen-01", generated.first().id)
        assertEquals(22, generated.map { it.name }.toSet().size, "nomes distintos no clube")
    }

    @Test
    fun `completa goleiros mesmo quando o elenco ja tem 22`() {
        val squad = List(22) { real("p$it", "MF") }

        val generated = filler.fill("bra-teste", "BRA", squad, emptySet())

        assertEquals(listOf("GK", "GK"), generated.map { it.position })
    }

    @Test
    fun `elenco completo nao recebe gerados`() {
        val squad = listOf(real("g1", "GK"), real("g2", "GK")) + List(20) { real("p$it", "DF") }

        assertEquals(emptyList(), filler.fill("bra-teste", "BRA", squad, emptySet()))
    }

    @Test
    fun `preenche a posicao mais longe do modelo`() {
        // 2 GK, 7 DF, 0 MF, 5 FW → faltam 1 GK (modelo 3) e 7 MF; os 8 que faltam até 22.
        val squad = List(2) { real("g$it", "GK") } + List(7) { real("d$it", "DF") } + List(5) { real("f$it", "FW") }

        val added = filler.positionsToAdd(squad.map { it.position })

        assertEquals(8, added.size)
        assertEquals(7, added.count { it == "MF" })
        assertEquals(1, added.count { it == "GK" })
    }

    @Test
    fun `mesma seed gera o mesmo elenco e nunca usa nome tomado`() {
        val taken = setOf("Bruno Silva")
        val first = filler.fill("eng-teste", "ENG", emptyList(), taken)
        val second = filler.fill("eng-teste", "ENG", emptyList(), taken)

        assertEquals(first, second)
        assertTrue(first.none { it.name in taken })
        assertTrue(first.all { LocalDate.parse(it.birthDate) in LocalDate.of(1993, 7, 1)..LocalDate.of(2008, 7, 1) })
    }

    @Test
    fun `clubes diferentes recebem gerados diferentes`() {
        val a = filler.fill("bra-a", "BRA", emptyList(), emptySet()).map { it.name }
        val b = filler.fill("bra-b", "BRA", emptyList(), emptySet()).map { it.name }

        assertTrue(a != b)
    }
}
