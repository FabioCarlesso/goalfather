package com.carlesso.goalfather.adapter.out.worlddata

import com.carlesso.goalfather.application.port.out.PackLoadError
import com.carlesso.goalfather.application.port.out.PackLoadResult
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.world.WorldClubId
import com.carlesso.goalfather.domain.model.world.WorldPlayerId
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Validação do pack (issue #126): cada pack inválido devolve o erro
 * ESPECÍFICO como valor, sem exception.
 *
 * Em vez de uma pasta de fixture por caso, o teste lê a fixture válida para um
 * `Map<recurso, texto>` e estraga UM recurso por teste — o `read` passado ao
 * loader é só `map::get`. É o mesmo truque que um adapter HTTP usaria: o
 * loader não sabe de onde vem o texto.
 */
class WorldPackLoaderTest {

    private val valid: Map<String, String> = FileWorldDataProviderTest.worldSamplePath().let { root ->
        // `use` é o try-with-resources do Kotlin: fecha o Stream do `Files.walk`.
        Files.walk(root).use { paths ->
            paths.toList().filter { it.isRegularFile() }
                .associate { it.relativeTo(root).toString().replace('\\', '/') to it.readText() }
        }
    }

    /** Carrega o pack depois de aplicar [changes] (recurso → texto novo; `null` apaga). */
    private suspend fun load(vararg changes: Pair<String, String?>): PackLoadResult {
        val resources = valid.toMutableMap()
        changes.forEach { (resource, text) -> if (text == null) resources.remove(resource) else resources[resource] = text }
        return WorldPackLoader().load(read = resources::get, calendarYears = listOf(2026))
    }

    private suspend fun errorsOf(vararg changes: Pair<String, String?>): List<PackLoadError> =
        assertIs<PackLoadResult.Invalid>(load(*changes)).errors

    private fun envelope(data: String, version: Int = 1) =
        """{"schemaVersion": $version, "generatedAt": "2026-11-11T00:00:00Z", "source": "teste", "data": $data}"""

    private fun squad(vararg players: String) = envelope("""{"players": [${players.joinToString()}]}""")

    private fun player(id: String, position: String, nationality: String = "BRA") =
        """{"id": "$id", "name": "Jogador $id", "birthDate": "1999-01-01", "nationality": "$nationality", "position": "$position"}"""

    @Test
    fun `fixture valida carrega`() = runTest {
        assertIs<PackLoadResult.Loaded>(load())
    }

    @Test
    fun `versao de schema desconhecida`() = runTest {
        val errors = errorsOf("countries.json" to envelope("[]", version = 2))
        assertEquals(listOf(PackLoadError.UnsupportedSchemaVersion("countries.json", 2, setOf(1))), errors)
    }

    @Test
    fun `versao desconhecida e detectada mesmo com data em formato novo`() = runTest {
        // Um v2 pode mudar a forma do `data`; o erro tem que ser de VERSÃO, não de formato.
        val errors = errorsOf("clubs/tst-beta/squad.json" to envelope("""{"elenco": "outro formato"}""", version = 2))
        assertEquals(listOf(PackLoadError.UnsupportedSchemaVersion("clubs/tst-beta/squad.json", 2, setOf(1))), errors)
    }

    @Test
    fun `referencia para clube inexistente`() = runTest {
        val errors = errorsOf(
            "competitions/tst-copa/clubs.json" to envelope("""[{"id": "tst-alfa"}, {"id": "tst-fantasma"}]"""),
        )
        assertEquals(listOf(PackLoadError.UnknownClubReference(CompetitionId("tst-copa"), WorldClubId("tst-fantasma"))), errors)
    }

    @Test
    fun `elenco sem goleiro`() = runTest {
        val errors = errorsOf("clubs/tst-beta/squad.json" to squad(player("tst-x-1", "MF"), player("tst-x-2", "FW")))
        assertEquals(listOf(PackLoadError.SquadWithoutGoalkeeper(WorldClubId("tst-beta"))), errors)
    }

    @Test
    fun `elenco ausente`() = runTest {
        val errors = errorsOf("clubs/tst-beta/squad.json" to null)
        assertEquals(listOf(PackLoadError.MissingResource("clubs/tst-beta/squad.json")), errors)
    }

    @Test
    fun `nacionalidade fora de countries json`() = runTest {
        val errors = errorsOf(
            "clubs/tst-beta/squad.json" to squad(player("tst-x-1", "GK"), player("tst-x-2", "MF", nationality = "URU")),
        )
        assertEquals(listOf(PackLoadError.UnknownCountry("clubs/tst-beta/squad.json", CountryCode("URU"))), errors)
    }

    @Test
    fun `mesmo jogador em dois elencos`() = runTest {
        val errors = errorsOf("clubs/tst-beta/squad.json" to squad(player("tst-p-001", "GK")))
        assertEquals(
            listOf(PackLoadError.DuplicatePlayer(WorldPlayerId("tst-p-001"), listOf(WorldClubId("tst-alfa"), WorldClubId("tst-beta")))),
            errors,
        )
    }

    @Test
    fun `clube definido de dois jeitos`() = runTest {
        val errors = errorsOf(
            "competitions/tst-copa/clubs.json" to envelope(
                """[{"id": "tst-alfa", "name": "Outro Nome", "country": "BRA", "reputation": 80,
                    "stadium": {"name": "Estádio Alfa", "capacity": 40000}}, {"id": "tst-beta"}]""",
            ),
        )
        assertEquals(listOf(PackLoadError.ConflictingClubDefinition(WorldClubId("tst-alfa"))), errors)
    }

    @Test
    fun `json quebrado vira MalformedResource`() = runTest {
        val error = errorsOf("competitions.json" to """{"schemaVersion": 1, "data": [""").single()
        assertIs<PackLoadError.MalformedResource>(error)
        assertEquals("competitions.json", error.resource)
    }

    @Test
    fun `formato de competicao desconhecido vira MalformedResource`() = runTest {
        val competitions = valid.getValue("competitions.json").replace("\"roundRobin\"", "\"ladder\"")
        val error = errorsOf("competitions.json" to competitions).single()
        assertIs<PackLoadError.MalformedResource>(error)
    }

    @Test
    fun `valores que o dominio recusa viram InvalidValue`() = runTest {
        val clubs = valid.getValue("competitions/tst-liga/clubs.json").replace("\"capacity\": 15000", "\"capacity\": -1")
        val players = squad(player("tst-x-1", "GK"), player("tst-x-2", "LIBERO"))
        val errors = errorsOf("competitions/tst-liga/clubs.json" to clubs, "clubs/tst-alfa/squad.json" to players)
        assertEquals(2, errors.size, "$errors")
        assertTrue(errors.all { it is PackLoadError.InvalidValue }, "$errors")
    }

    @Test
    fun `id com caminho relativo e recusado antes de virar caminho de arquivo`() = runTest {
        val errors = errorsOf(
            "competitions/tst-copa/clubs.json" to envelope("""[{"id": "tst-alfa"}, {"id": "../../etc"}]"""),
        )
        assertIs<PackLoadError.InvalidValue>(errors.single())
    }

    @Test
    fun `competicao com dois donos`() = runTest {
        val competitions = valid.getValue("competitions.json")
            .replace("\"country\": \"BRA\",\n      \"tier\"", "\"country\": \"BRA\", \"confederation\": \"CONMEBOL\",\n      \"tier\"")
        val errors = errorsOf("competitions.json" to competitions)
        assertIs<PackLoadError.InvalidValue>(errors.single(), "$errors")
    }

    @Test
    fun `calendario com ano trocado`() = runTest {
        val calendar = valid.getValue("calendar/2026.json").replace("\"year\": 2026", "\"year\": 2025")
        assertIs<PackLoadError.InvalidValue>(errorsOf("calendar/2026.json" to calendar).single())
    }

    @Test
    fun `erros independentes sao acumulados`() = runTest {
        val errors = errorsOf(
            "clubs/tst-beta/squad.json" to squad(player("tst-x-2", "MF")),
            "competitions/tst-copa/clubs.json" to envelope("""[{"id": "tst-alfa"}, {"id": "tst-fantasma"}]"""),
        )
        assertEquals(
            setOf(
                PackLoadError.SquadWithoutGoalkeeper(WorldClubId("tst-beta")),
                PackLoadError.UnknownClubReference(CompetitionId("tst-copa"), WorldClubId("tst-fantasma")),
            ),
            errors.toSet(),
        )
    }
}
