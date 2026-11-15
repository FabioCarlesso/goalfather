package com.carlesso.goalfather.adapter.out.persistence.mapper

import com.carlesso.goalfather.domain.model.Confederation
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.CountryCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Os códigos "fora do catálogo" daqui (`XYZ`, `QQQ`) não existem em país
 * nenhum, nem no pack: [SavedCountries] é global no JVM e um teste Spring que
 * rodou antes pode tê-lo enchido com os países do pack (issue #131).
 */
class CountryMapperTest {

    @Test
    fun `codigo do catalogo resolve o pais`() {
        assertEquals(Countries.ARGENTINA, "ARG".toCountry())
        assertEquals(Countries.ITALY, "ITA".toCountryOrNull())
    }

    @Test
    fun `pais obrigatorio fora do catalogo cai no default`() {
        assertEquals(Countries.DEFAULT, "XYZ".toCountry())
    }

    @Test
    fun `segunda nacionalidade fora do catalogo vira ausencia e nao o default`() {
        // Cair no Brasil inventaria uma nacionalidade e tornaria o estrangeiro "nacional".
        assertNull("XYZ".toCountryOrNull())
        assertNull("xx".toCountryOrNull())
        assertNull(null.toCountryOrNull())
    }

    @Test
    fun `pais do save resolve codigo que o catalogo fixo nao conhece (issue 131)`() {
        val qatarLike = Country(CountryCode("QQQ"), "Paísteste", Confederation.AFC, "QQ")
        SavedCountries.replace(listOf(qatarLike))
        try {
            assertEquals(qatarLike, "QQQ".toCountry())
            assertEquals(qatarLike, "QQQ".toCountryOrNull())
            // O catálogo fixo continua valendo para o que o save não traz.
            assertEquals(Countries.ARGENTINA, "ARG".toCountry())
        } finally {
            SavedCountries.replace(emptyList())
        }
    }
}
