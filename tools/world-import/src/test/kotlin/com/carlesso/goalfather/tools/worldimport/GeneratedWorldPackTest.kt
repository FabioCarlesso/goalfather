package com.carlesso.goalfather.tools.worldimport

import com.carlesso.goalfather.adapter.out.worlddata.FileWorldDataProvider
import com.carlesso.goalfather.application.port.out.PackLoadResult
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.SeasonCompetitions
import com.carlesso.goalfather.domain.model.world.WorldPack
import com.carlesso.goalfather.tools.worldimport.wikidata.CacheMode
import com.carlesso.goalfather.tools.worldimport.wikidata.SparqlClient
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integração do pack GERADO (issue #127) com o adapter da #126: o que está
 * commitado em `data/world/v1/` carrega sem erro e cumpre os critérios de
 * aceite. Roda com diretório de trabalho em `backend/`.
 */
class GeneratedWorldPackTest {

    private val packDir = Path.of("../data/world/v1")

    private suspend fun pack(): WorldPack {
        val result = FileWorldDataProvider(packDir).load()
        assertIs<PackLoadResult.Loaded>(result, "pack inválido: $result")
        return result.pack
    }

    @Test
    fun `tem as 7 ligas do MVP e a fase de grupos da Libertadores`() = runTest {
        val pack = pack()
        val sizes = pack.competitions.associate { it.id.value to it.clubIds.size }

        assertEquals(
            mapOf(
                "bra-serie-a" to 20, "bra-serie-b" to 20, "eng-premier-league" to 20, "esp-la-liga" to 20,
                "ita-serie-a" to 20, "ger-bundesliga" to 18, "fra-ligue-1" to 18, "conmebol-libertadores" to 32,
            ),
            sizes,
        )
        // Os brasileiros da Libertadores são os MESMOS clubes da Série A (referência, não cópia).
        val libertadores = pack.competitions.single { it.id == CompetitionId("conmebol-libertadores") }
        val serieA = pack.competitions.single { it.id == CompetitionId("bra-serie-a") }
        assertEquals(6, libertadores.clubIds.count { it in serieA.clubIds })
        assertEquals(20 * 5 + 18 * 2 + 26, pack.clubs.size)
    }

    @Test
    fun `todo elenco tem ao menos 22 jogadores e 2 goleiros`() = runTest {
        pack().clubs.forEach { club ->
            assertTrue(club.squad.size >= 22, "${club.id}: ${club.squad.size} jogadores")
            assertTrue(club.squad.count { it.position == Position.GK } >= 2, "${club.id}: menos de 2 goleiros")
        }
    }

    /**
     * Limite de estrangeiros (issue #130): com mais estrangeiros que o limite e
     * sem nacionais suficientes, a escalação automática entraria com menos de
     * 11 — correto pela regra, mas um clube da IA jogaria desfalcado toda
     * rodada. O pack garante que nenhum clube brasileiro chegue nesse estado.
     */
    @Test
    fun `todo clube brasileiro escala 11 dentro do limite de estrangeiros`() = runTest {
        val pack = pack()
        val limit = SeasonCompetitions.NATIONAL_FOREIGN_PLAYER_LIMIT
        val brazilian = pack.competitions
            .filter { it.id.value.startsWith("bra-") }
            .flatMap { it.clubIds }
            .toSet()
        brazilian.map { id -> pack.clubs.single { it.id == id } }.forEach { club ->
            // `partition` separa numa passada só: (estrangeiros, nacionais).
            val (foreigners, nationals) = club.squad.partition {
                it.nationality != club.country && it.secondNationality != club.country
            }
            val eligible = nationals.size + minOf(foreigners.size, limit)
            assertTrue(eligible >= 11, "${club.id}: só $eligible aptos (${foreigners.size} estrangeiros, limite $limit)")
        }
    }

    @Test
    fun `nenhum arquivo traz atributo salario ou valor`() {
        val forbidden = Regex("\"(overall|rating|pace|shooting|passing|defending|salary|wage|marketValue|value|contract)\"\\s*:")
        val files = Files.walk(packDir).use { paths -> paths.filter { it.isRegularFile() }.toList() }
        assertTrue(files.size > 100)
        files.forEach { file -> assertTrue(!forbidden.containsMatchIn(file.readText()), "campo proibido em $file") }
    }

    /**
     * Critério de aceite "duas execuções geram arquivos idênticos": roda o
     * importador OFFLINE (só o cache commitado) num diretório temporário e
     * compara byte a byte com o pack commitado.
     */
    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `reimportar do cache gera exatamente o pack commitado`() {
        val cache = Path.of("../tools/world-import/cache")
        if (!cache.exists()) return // cache fora do checkout: nada a comparar
        val out = Files.createTempDirectory("world-v1")
        // O calendário é curado à mão e o importador não o toca: copia para o validador final achar.
        packDir.resolve("calendar").copyToRecursively(out.resolve("calendar"), followLinks = false)

        val ok = WorldImport(
            worldDir = Path.of("../data/world"),
            packDir = out,
            client = SparqlClient(cache, CacheMode.OFFLINE, log = {}),
            log = {},
        ).run()

        assertTrue(ok)
        assertEquals(snapshotOf(packDir), snapshotOf(out))
    }

    @OptIn(ExperimentalPathApi::class)
    private fun snapshotOf(root: Path): Map<String, String> =
        root.walk().filter { it.isRegularFile() }.associate { it.relativeTo(root).toString() to it.readText() }.toSortedMap()
}
