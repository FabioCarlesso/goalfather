package com.carlesso.goalfather.tools.worldimport.wikidata

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Cache e parsing do cliente SPARQL — sem rede (modo OFFLINE). */
class SparqlClientTest {

    @Test
    fun `parse simplifica uris ordena e remove linhas repetidas`() {
        val body = """
            {"head":{"vars":["club","rank","n"]},"results":{"bindings":[
              {"club":{"type":"uri","value":"http://www.wikidata.org/entity/Q2"},"n":{"type":"literal","value":"2"}},
              {"club":{"type":"uri","value":"http://www.wikidata.org/entity/Q1"},
               "rank":{"type":"uri","value":"http://wikiba.se/ontology#PreferredRank"}},
              {"club":{"type":"uri","value":"http://www.wikidata.org/entity/Q2"},"n":{"type":"literal","value":"2"}}
            ]}}
        """.trimIndent()

        assertEquals(
            listOf(mapOf("club" to "Q1", "rank" to "PreferredRank"), mapOf("club" to "Q2", "n" to "2")),
            SparqlClient.parse(body),
        )
    }

    @Test
    fun `offline le o cache da mesma consulta e recusa consulta diferente`() {
        val dir = Files.createTempDirectory("sparql-cache")
        dir.resolve("clubes.json").writeText(
            """{"query":"SELECT 1","retrievedAt":"2026-11-13T10:00:00Z","rows":[{"club":"Q1"}]}""",
        )
        val client = SparqlClient(dir, CacheMode.OFFLINE)

        assertEquals(listOf(mapOf("club" to "Q1")), client.select("clubes", "SELECT 1"))
        assertEquals("2026-11-13T10:00:00Z", client.retrievals.single().toString())

        val mudou = assertFailsWith<IllegalStateException> { client.select("clubes", "SELECT 2") }
        assertTrue("outra consulta" in mudou.message.orEmpty())
        assertFailsWith<IllegalStateException> { client.select("ausente", "SELECT 1") }
    }
}
