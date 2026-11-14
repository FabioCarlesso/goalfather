package com.carlesso.goalfather.tools.worldimport.e2e

import com.carlesso.goalfather.adapter.out.worlddata.FileWorldDataProvider
import com.carlesso.goalfather.application.port.out.PackLoadResult
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.world.ClubProfile
import com.carlesso.goalfather.domain.model.world.WorldClubId
import com.carlesso.goalfather.domain.model.world.WorldPack
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Pack reduzido do profile `e2e` (issue #131): o commitado em
 * `data/world/e2e/v1` é EXATAMENTE o recorte do pack completo, e tem a forma
 * combinada com o mundo dos mocks do frontend. Roda em `backend/`.
 */
class E2ePackTest {

    private val fullDir = Path.of("../data/world/v1")
    private val e2eDir = Path.of("../data/world/e2e/v1")

    private suspend fun load(dir: Path): WorldPack {
        val result = FileWorldDataProvider(dir).load()
        assertIs<PackLoadResult.Loaded>(result, "pack inválido em $dir: $result")
        return result.pack
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `recortar o pack completo gera exatamente o pack e2e commitado`() {
        val out = Files.createTempDirectory("world-e2e")
        E2ePack.write(fullDir, out)
        assertEquals(snapshotOf(e2eDir), snapshotOf(out))
    }

    @Test
    fun `forma do pack e2e - 6 da Serie A, 6 da B, copa com os 12 e 4 da Premier`() = runTest {
        val full = load(fullDir)
        val e2e = load(e2eDir)
        assertEquals(
            listOf("bra-serie-a" to 6, "bra-serie-b" to 6, "bra-copa-do-brasil" to 12, "eng-premier-league" to 4),
            e2e.competitions.map { it.id.value to it.clubIds.size },
        )
        // Os de maior reputação de cada liga (empate: id), na ordem do pack completo.
        for ((league, size) in E2ePack.LEAGUE_CUTS) {
            val fullLeague = full.competitions.single { it.id == CompetitionId(league) }
            val expected = fullLeague.clubIds
                .map { requireNotNull(full.club(it)) }
                .sortedWith(compareByDescending<ClubProfile> { it.reputation.value }.thenBy { it.id.value })
                .take(size)
                .map { it.id }
                .toSet()
            assertEquals(fullLeague.clubIds.filter { it in expected }, e2e.competitions.single { it.id == CompetitionId(league) }.clubIds)
        }
        val copa = e2e.competitions.single { it.id == CompetitionId("bra-copa-do-brasil") }
        val brazilians: List<WorldClubId> = listOf("bra-serie-a", "bra-serie-b")
            .flatMap { id -> e2e.competitions.single { it.id == CompetitionId(id) }.clubIds }
        assertEquals(brazilians, copa.clubIds)
        val format = assertIs<CompetitionFormat.Knockout>(copa.competition.format)
        assertEquals(listOf(8, 8, 4, 2), format.stages.map { it.teams })
        assertEquals(listOf(8, 4, 0, 0), format.stages.map { it.newEntrants })
        assertEquals(listOf(1, 2, 2, 1), format.stages.map { it.legs.matches })
        // Elencos copiados, não regerados.
        e2e.clubs.forEach { club -> assertEquals(full.club(club.id), club) }
        assertEquals(full.calendars, e2e.calendars)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun snapshotOf(root: Path): Map<String, String> =
        root.walk().filter { it.isRegularFile() }.associate { it.relativeTo(root).toString() to it.readText() }.toSortedMap()
}
