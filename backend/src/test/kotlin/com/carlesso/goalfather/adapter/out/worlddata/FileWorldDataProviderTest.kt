package com.carlesso.goalfather.adapter.out.worlddata

import com.carlesso.goalfather.application.port.out.PackLoadError
import com.carlesso.goalfather.application.port.out.PackLoadResult
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Confederation
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.FifaWindow
import com.carlesso.goalfather.domain.model.Legs
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Tiebreaker
import com.carlesso.goalfather.domain.model.world.CompetitionOwner
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.domain.model.world.Reputation
import com.carlesso.goalfather.domain.model.world.Stadium
import com.carlesso.goalfather.domain.model.world.WorldClubId
import com.carlesso.goalfather.domain.model.world.WorldPack
import com.carlesso.goalfather.domain.model.world.WorldPlayerId
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adapter de arquivo contra a fixture `src/test/resources/world-sample/`
 * (issue #126). Sem Spring: o adapter é uma classe comum que recebe o `Path`.
 */
class FileWorldDataProviderTest {

    private suspend fun loadedPack(root: Path): WorldPack {
        val result = FileWorldDataProvider(root).load()
        // `assertIs` faz smart cast: depois dele o compilador sabe que é `Loaded`.
        assertIs<PackLoadResult.Loaded>(result, "esperava Loaded, veio $result")
        return result.pack
    }

    @Test
    fun `le a fixture e devolve o dominio correto`() = runTest {
        val pack = loadedPack(worldSamplePath())

        assertEquals(listOf("BRA", "ARG", "ENG"), pack.countries.map { it.code.value })
        assertEquals(Confederation.UEFA, pack.countries.single { it.code == CountryCode("ENG") }.confederation)

        val liga = pack.competitions.single { it.id == CompetitionId("tst-liga") }
        assertEquals(CompetitionFormat.RoundRobin(legs = 2), liga.competition.format)
        assertEquals(
            listOf(Tiebreaker.POINTS, Tiebreaker.WINS, Tiebreaker.GOAL_DIFFERENCE, Tiebreaker.GOALS_FOR),
            liga.competition.tiebreakers,
        )
        assertEquals(1, liga.tier)
        assertEquals(LeagueStrength(70), liga.strength)
        assertEquals(CountryCode("BRA"), assertIs<CompetitionOwner.National>(liga.owner).country.code)
        assertEquals(listOf(WorldClubId("tst-alfa"), WorldClubId("tst-beta")), liga.clubIds)
        assertTrue(liga.isLeague)

        // A copa só REFERENCIA os clubes da liga — resolvidos, sem duplicar.
        val copa = pack.competitions.single { it.id == CompetitionId("tst-copa") }
        val final = assertIs<CompetitionFormat.Knockout>(copa.competition.format).stages.single()
        assertEquals(Legs.TWO_LEGGED, final.legs)
        assertNull(copa.strength)
        assertEquals(liga.clubIds, copa.clubIds)
        assertEquals(2, pack.clubs.size)

        val alfa = requireNotNull(pack.club(WorldClubId("tst-alfa")))
        assertEquals("Alfa FC", alfa.name)
        assertEquals(Stadium("Estádio Alfa", 40_000), alfa.stadium)
        assertEquals(Reputation(80), alfa.reputation)
        assertEquals(LeagueStrength(70), pack.leagueStrengthOf(alfa.id))

        val zagueiro = alfa.squad.single { it.id == WorldPlayerId("tst-p-002") }
        assertEquals(Position.CB, zagueiro.position, "DF do pack vira CB no domínio")
        assertEquals("Zagueiro Simulado", zagueiro.shownName)
        assertEquals("Zagueiro Real", zagueiro.name)
        assertEquals(CountryCode("ARG"), zagueiro.nationality.code)
        assertEquals(CountryCode("BRA"), zagueiro.secondNationality?.code)
        assertEquals(LocalDate.of(2001, 1, 31), zagueiro.birthDate)
        assertEquals(25, zagueiro.ageOn(LocalDate.of(2026, 11, 11)))

        val calendar = requireNotNull(pack.calendar(2026))
        assertEquals(
            listOf(
                FifaWindow(LocalDate.of(2026, 3, 23), LocalDate.of(2026, 3, 31)),
                FifaWindow(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 17)),
            ),
            calendar.fifaWindows,
        )
        assertEquals(2, calendar.transferWindowsOf(alfa.country).size)
        assertTrue(LocalDate.of(2026, 8, 1) in calendar.transferWindowsOf(alfa.country)[1])
    }

    @Test
    fun `pack de exemplo versionado em data-world-v1 carrega sem erro`() = runTest {
        // `./gradlew test` roda com diretório de trabalho em `backend/`.
        val pack = loadedPack(Path.of("../data/world/v1"))
        assertTrue(pack.clubs.isNotEmpty())
        assertTrue(pack.clubs.all { it.hasGoalkeeper })
    }

    @Test
    fun `diretorio vazio vira MissingResource sem exception`() = runTest {
        val result = FileWorldDataProvider(Files.createTempDirectory("world-vazio")).load()
        val invalid = assertIs<PackLoadResult.Invalid>(result)
        assertEquals(
            listOf(
                PackLoadError.MissingResource("countries.json"),
                PackLoadError.MissingResource("competitions.json"),
            ),
            invalid.errors,
        )
    }

    companion object {
        fun worldSamplePath(): Path =
            Path.of(requireNotNull(FileWorldDataProviderTest::class.java.getResource("/world-sample")).toURI())
    }
}
