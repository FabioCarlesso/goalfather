package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.CompetitionLookup
import com.carlesso.goalfather.application.port.`in`.CompetitionMatchday
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.application.port.out.ContinentalRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.ContinentalCup
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Venue
import com.carlesso.goalfather.domain.rules.ContinentalDraw
import com.carlesso.goalfather.domain.rules.drawContinental
import com.carlesso.goalfather.domain.rules.groupRounds
import com.carlesso.goalfather.domain.rules.seededForContinental
import com.carlesso.goalfather.domain.rules.worldPlan
import com.carlesso.goalfather.test.InMemoryCupRepository
import com.carlesso.goalfather.test.brazilCatalog
import com.carlesso.goalfather.test.foreignContinentalClubs
import com.carlesso.goalfather.test.libertadoresGame
import com.carlesso.goalfather.test.mvpClubs
import com.carlesso.goalfather.test.testStandings
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A Libertadores (#132) na navegação pelo mundo (#133): integração das duas
 * issues da onda 5. Sem Spring, ports mockados.
 */
class CompetitionQueryContinentalTest {

    private val libertadores = libertadoresGame()
    private val catalog = brazilCatalog() + libertadores
    private val brazilians = mvpClubs().filter { it.country == Countries.BRAZIL }
    private val clubs = brazilians + foreignContinentalClubs()

    private val edition: ContinentalCup = drawContinental(
        2026,
        libertadores,
        seededForContinental(brazilians.take(6) + foreignContinentalClubs(), null),
        Venue("Estadio Monumental U", 80_093),
        Random(11),
    ).let { assertIs<ContinentalDraw.Drawn>(it).edition }

    private val plan = worldPlan(
        catalog,
        clubs,
        mapOf(Countries.BRAZIL.code to 2026),
        continentals = mapOf(libertadores.id to edition),
    )

    /** Uma edição só; `find` por temporada, como o adapter JPA. */
    private val continentals = object : ContinentalRepository {
        override suspend fun find(competition: CompetitionId, season: Int): ContinentalCup? =
            edition.takeIf { competition == it.competitionId && season == it.season }
        override suspend fun findLatest(competition: CompetitionId): ContinentalCup? = find(competition, edition.season)
        override suspend fun save(edition: ContinentalCup) = Unit
    }

    private val service = CompetitionQueryService(
        mockk<CompetitionCatalogRepository> { coEvery { findAll() } returns catalog },
        mockk<ClubRepository> { coEvery { findAll() } returns clubs },
        mockk<LeagueRepository> {
            coEvery { currentStandings() } returns listOf(
                testStandings(rows = emptyList(), division = Division(1)),
                testStandings(rows = emptyList(), division = Division(2)),
            )
            // O mundo no 1º encaixe da temporada, nada jogado ainda.
            coEvery { findLatest() } returns plan.roundAt(plan.schedule.matchdays.first(), clubs)
            coEvery { findRound(any()) } returns null
        },
        InMemoryCupRepository(),
        continentals,
    )

    @Test
    fun `a Libertadores fecha a lista do Brasil com os 32 da edicao sorteada`() = runTest {
        val last = service.competitions().last()

        assertEquals(libertadores.id, last.game.id)
        assertEquals(CompetitionKind.Continental, last.game.kind)
        assertEquals(32, last.clubCount)
    }

    @Test
    fun `os jogos de grupo aparecem adiante e os do mata-mata nao, porque dependem dos grupos`() = runTest {
        val days = assertIs<CompetitionLookup.Found<List<CompetitionMatchday>>>(
            service.matches(libertadores.id),
        ).value

        val (group, knockout) = days.partition { it.matches.isNotEmpty() }
        assertEquals(edition.groupRounds, group.size)
        assertTrue(group.all { day -> day.matches.size == 16 }, "8 grupos × 2 jogos por rodada")
        assertTrue(knockout.isNotEmpty() && knockout.all { it.matchday > group.last().matchday })
    }

    @Test
    fun `sem mata-mata sorteado a chave da continental ainda nao existe`() = runTest {
        assertEquals(CompetitionLookup.NotAvailable, service.bracket(libertadores.id))
    }
}
