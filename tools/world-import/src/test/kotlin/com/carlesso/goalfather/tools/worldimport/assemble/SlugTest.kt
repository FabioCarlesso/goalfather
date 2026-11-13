package com.carlesso.goalfather.tools.worldimport.assemble

import com.carlesso.goalfather.adapter.out.worlddata.WorldPackLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlugTest {

    @Test
    fun `nomes viram ids seguros do pack`() {
        val cases = mapOf(
            "Agustín Rossi" to "agustin-rossi",
            "Giorgian de Arrascaeta" to "giorgian-de-arrascaeta",
            "Martin Ødegaard" to "martin-odegaard",
            "Thomas Müller" to "thomas-muller",
            "Leroy Sané" to "leroy-sane",
            "Nicolò Barella" to "nicolo-barella",
            "Ľubomír Šatka" to "lubomir-satka",
            "Mats Hummels-Groß" to "mats-hummels-gross",
            "  N'Golo Kanté " to "n-golo-kante",
        )

        cases.forEach { (name, slug) ->
            assertEquals(slug, name.slug(), name)
            assertTrue(WorldPackLoader.SAFE_ID.matches(slug))
        }
    }

    @Test
    fun `nome sem letra latina vira vazio e o assembler descarta`() {
        assertEquals("", "李明".slug())
    }
}
