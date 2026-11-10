package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.domain.model.Countries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CountryMapperTest {

    @Test
    fun `codigo do catalogo resolve o pais`() {
        assertEquals(Countries.ARGENTINA, "ARG".toCountry())
        assertEquals(Countries.ITALY, "ITA".toCountryOrNull())
    }

    @Test
    fun `pais obrigatorio fora do catalogo cai no default`() {
        assertEquals(Countries.DEFAULT, "NED".toCountry())
    }

    @Test
    fun `segunda nacionalidade fora do catalogo vira ausencia e nao o default`() {
        // Cair no Brasil inventaria uma nacionalidade e tornaria o estrangeiro "nacional".
        assertNull("NED".toCountryOrNull())
        assertNull("xx".toCountryOrNull())
        assertNull(null.toCountryOrNull())
    }
}
