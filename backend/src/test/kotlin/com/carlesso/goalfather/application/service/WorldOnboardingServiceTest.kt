package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.KnockoutStage
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.test.makeClub
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Onboarding país → liga → clube (issue #131) sobre o catálogo do save. Sem Spring. */
class WorldOnboardingServiceTest {

    private fun league(id: String, country: Country, tier: Int) = GameCompetition(
        competition = Competition(CompetitionId(id), id, CompetitionFormat.RoundRobin(2)),
        country = country,
        kind = CompetitionKind.League(Division(tier)),
        span = SeasonSpan.of(country.confederation),
        pausesOnFifaWindows = true,
    )

    private val serieA = league("bra-serie-a", Countries.BRAZIL, 1)
    private val serieB = league("bra-serie-b", Countries.BRAZIL, 2)
    private val premier = league("eng-premier-league", Countries.ENGLAND, 1)
    private val copa = GameCompetition(
        competition = Competition(
            CompetitionId("bra-copa-do-brasil"),
            "Copa do Brasil",
            CompetitionFormat.Knockout(listOf(KnockoutStage("Final", 2, newEntrants = 2))),
        ),
        country = Countries.BRAZIL,
        kind = CompetitionKind.NationalCup,
        span = SeasonSpan.CALENDAR_YEAR,
        pausesOnFifaWindows = true,
    )

    private fun club(id: Long, name: String, country: Country, division: Int, overall: Int, ownerId: Long? = null): Club =
        makeClub(id = id, name = name, overall = overall, ownerId = ownerId, division = Division(division)).copy(country = country)

    private val clubs = listOf(
        club(1, "Flamengo", Countries.BRAZIL, 1, overall = 74),
        club(2, "Palmeiras", Countries.BRAZIL, 1, overall = 76, ownerId = 99),
        club(3, "Avaí", Countries.BRAZIL, 2, overall = 62),
        club(4, "Arsenal", Countries.ENGLAND, 1, overall = 82),
        club(5, "Chelsea", Countries.ENGLAND, 1, overall = 82),
    )

    private val catalog = mockk<CompetitionCatalogRepository> {
        // Ordem do pack de propósito embaralhada: a resposta ordena por nome e tier.
        coEvery { findAll() } returns listOf(premier, serieB, serieA, copa)
    }
    private val clubRepo = mockk<ClubRepository> {
        coEvery { findAll() } returns clubs
        coEvery { findAvailable() } returns clubs.filter { it.ownerId == null }
    }
    private val leagueRepo = mockk<LeagueRepository> {
        coEvery { currentStandings() } returns listOf(Standings(season = 2026, round = 0, rows = emptyList()))
    }
    private val service = WorldOnboardingService(catalog, clubRepo, leagueRepo)

    @Test
    fun `paises por nome, ligas por tier, contagem de clubes e livres, temporada de cada liga`() = runTest {
        val countries = service.countries()

        assertEquals(listOf("Brasil", "Inglaterra"), countries.map { it.country.name })
        val brazil = countries.first()
        // A copa não entra: o clube é escolhido pela liga.
        assertEquals(listOf("bra-serie-a", "bra-serie-b"), brazil.leagues.map { it.league.id.value })
        assertEquals(listOf(2, 1), brazil.leagues.map { it.clubCount })
        assertEquals(listOf(1, 1), brazil.leagues.map { it.availableClubCount })
        assertEquals("2026", brazil.leagues.first().seasonLabel)
        assertEquals("2026/27", countries[1].leagues.single().seasonLabel)
    }

    @Test
    fun `clubes livres por forca media desc e depois nome, com a liga de cada um`() = runTest {
        val available = service.availableClubs()

        assertEquals(listOf("Arsenal", "Chelsea", "Flamengo", "Avaí"), available.map { it.club.name })
        assertEquals(listOf(premier, premier, serieA, serieB), available.map { it.league })
    }

    @Test
    fun `filtro por liga - e liga inexistente devolve lista vazia`() = runTest {
        assertEquals(listOf("Flamengo"), service.availableClubs(CompetitionId("bra-serie-a")).map { it.club.name })
        assertEquals(emptyList(), service.availableClubs(CompetitionId("xyz-liga")))
        // A copa não é liga de ninguém.
        assertEquals(emptyList(), service.availableClubs(CompetitionId("bra-copa-do-brasil")))
    }
}
