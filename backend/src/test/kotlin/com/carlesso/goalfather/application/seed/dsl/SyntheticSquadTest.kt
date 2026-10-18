package com.carlesso.goalfather.application.seed.dsl

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Gerador de elencos sintéticos (issue #88). Puro — sem Spring. */
class SyntheticSquadTest {

    @Test
    fun `mesma seed gera o mesmo elenco`() {
        assertEquals(Random(4).syntheticSquad(strength = 70), Random(4).syntheticSquad(strength = 70))
    }

    @Test
    fun `seeds diferentes geram nomes diferentes`() {
        val a = Random(2).syntheticSquad(strength = 70).map { it.name }
        val b = Random(3).syntheticSquad(strength = 70).map { it.name }
        assertTrue(a != b)
    }

    @Test
    fun `nomes sao unicos no elenco e no formato Prenome Sobrenome`() {
        // Muitas seeds para exercitar o descarte de repetidos do `distinct()`.
        val format = Regex("""^\p{L}+ \p{L}+$""")
        for (seed in 1L..200L) {
            val names = Random(seed).syntheticSquad(strength = 70).map { it.name }
            assertEquals(11, names.size)
            assertEquals(names.size, names.toSet().size, "nome repetido na seed $seed: $names")
            names.forEach { assertTrue(format.matches(it), "formato inesperado: $it") }
        }
    }

    @Test
    fun `nomes em taken nao sao reutilizados`() {
        val first = Random(5).syntheticSquad(strength = 70).map { it.name }.toSet()
        // Mesma seed: sem `taken` repetiria tudo; com `taken`, nada se repete.
        val second = Random(5).syntheticSquad(strength = 70, taken = first).map { it.name }
        assertTrue(second.none { it in first })
    }

    @Test
    fun `overall varia mas a media e exatamente a forca`() {
        for (seed in 1L..200L) {
            val squad = Random(seed).syntheticSquad(strength = 70, maxSpread = 3)
            assertEquals(70 * squad.size, squad.sumOf { it.overall })
            assertTrue(squad.all { it.overall in 67..73 })
            assertTrue(squad.map { it.overall }.toSet().size > 1, "sem variação na seed $seed")
        }
    }

    @Test
    fun `deltas balanceados somam zero para qualquer tamanho`() {
        for (count in 0..20) {
            val deltas = Random(count).balancedDeltas(count, maxSpread = 2)
            assertEquals(count, deltas.size)
            assertEquals(0, deltas.sum())
            assertTrue(deltas.all { it in -2..2 })
        }
    }

    @Test
    fun `forca que estouraria a faixa de overall falha cedo`() {
        assertFailsWith<IllegalArgumentException> { Random(1).syntheticSquad(strength = 98, maxSpread = 3) }
        assertFailsWith<IllegalArgumentException> { Random(1).syntheticSquad(strength = 1, maxSpread = 3) }
    }

    @Test
    fun `pedir mais nomes do que as listas formam falha cedo em vez de travar`() {
        // Sem o guard, `take(n)` sobre a sequência `distinct()` giraria para
        // sempre esperando um nome inédito.
        val taken = (1..MAX_DISTINCT_PLAYER_NAMES).map { "Nome $it" }.toSet()
        assertFailsWith<IllegalArgumentException> { Random(1).syntheticSquad(strength = 70, taken = taken) }
    }
}
