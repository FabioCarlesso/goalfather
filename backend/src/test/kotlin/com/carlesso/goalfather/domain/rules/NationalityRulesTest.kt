package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.test.makeClub
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regras de nacionalidade (issue #123) — puras, sem Spring. */
class NationalityRulesTest {

    private val brazilianClub = makeClub(squadSize = 11)
    private val player = brazilianClub.squad.first()

    @Test
    fun `jogador do mesmo pais do clube nao e estrangeiro`() {
        assertFalse(player.isForeignAt(brazilianClub))
    }

    @Test
    fun `jogador de outro pais e estrangeiro`() {
        assertTrue(player.copy(nationality = Countries.ARGENTINA).isForeignAt(brazilianClub))
    }

    @Test
    fun `segunda nacionalidade do pais do clube tira o jogador da cota de estrangeiro`() {
        val argBra = player.copy(nationality = Countries.ARGENTINA, secondNationality = Countries.BRAZIL)
        assertFalse(argBra.isForeignAt(brazilianClub))
    }

    @Test
    fun `segunda nacionalidade de terceiro pais nao ajuda`() {
        val argIta = player.copy(nationality = Countries.ARGENTINA, secondNationality = Countries.ITALY)
        assertTrue(argIta.isForeignAt(brazilianClub))
        // ...mas conta num clube italiano.
        assertFalse(argIta.isForeignAt(brazilianClub.copy(country = Countries.ITALY)))
    }

    @Test
    fun `segunda nacionalidade igual a primeira e rejeitada`() {
        assertFailsWith<IllegalArgumentException> {
            player.copy(nationality = Countries.BRAZIL, secondNationality = Countries.BRAZIL)
        }
    }

    @Test
    fun `codigo de pais exige trigrama em maiusculas`() {
        assertEquals("BRA", CountryCode("BRA").value)
        assertFailsWith<IllegalArgumentException> { CountryCode("br") }
        assertFailsWith<IllegalArgumentException> { CountryCode("BRAS") }
    }

    @Test
    fun `catalogo resolve pelo codigo e devolve null para desconhecido`() {
        assertEquals(Countries.ENGLAND, Countries.find(CountryCode("ENG")))
        assertNull(Countries.find(CountryCode("XYZ")))
        // Sem código repetido no catálogo.
        assertEquals(Countries.all.size, Countries.all.map { it.code }.toSet().size)
    }
}
