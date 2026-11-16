package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.AvailableClub
import com.carlesso.goalfather.application.port.`in`.OnboardingCountry
import com.carlesso.goalfather.application.port.`in`.OnboardingLeague
import com.carlesso.goalfather.application.port.`in`.WorldOnboardingUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.leagueOf
import java.text.Collator
import java.util.Locale

/**
 * Leituras do onboarding (issue #131) sobre o catálogo do save e os clubes.
 * A liga de um clube é a do catálogo para (país, divisão) do clube
 * (`leagueOf`) — a mesma regra que o calendário usa, então o card mostra a
 * liga que o clube vai de fato disputar.
 */
class WorldOnboardingService(
    private val catalog: CompetitionCatalogRepository,
    private val clubs: ClubRepository,
    private val league: LeagueRepository,
) : WorldOnboardingUseCase {

    override suspend fun countries(): List<OnboardingCountry> {
        val competitions = catalog.findAll()
        val leagues = competitions.filter { it.kind is CompetitionKind.League }
        if (leagues.isEmpty()) return emptyList()
        val labels = seasonLabels(leagues)
        // Liga de cada clube calculada uma vez; `groupingBy { }.eachCount()`
        // conta por chave sem montar as listas intermediárias.
        val all = clubs.findAll()
        val leagueIdOf = all.associate { it.id to competitions.leagueOf(it)?.id }
        val clubCount = all.groupingBy { leagueIdOf[it.id] }.eachCount()
        val freeCount = all.filter { it.ownerId == null }.groupingBy { leagueIdOf[it.id] }.eachCount()

        return leagues
            .filter { it.id in labels }
            .groupBy { it.country }
            .map { (country, countryLeagues) ->
                OnboardingCountry(
                    country = country,
                    leagues = countryLeagues
                        // `as` seguro: só ligas chegaram até aqui (filtro acima).
                        .sortedBy { (it.kind as CompetitionKind.League).tier }
                        .map {
                            OnboardingLeague(
                                league = it,
                                clubCount = clubCount[it.id] ?: 0,
                                availableClubCount = freeCount[it.id] ?: 0,
                                seasonLabel = labels.getValue(it.id),
                            )
                        },
                )
            }
            // Collator em pt-BR: "Espanha" antes de "França", acentos sem pular
            // para o fim da lista como na ordem crua de `String.compareTo`.
            .sortedWith(compareBy(PT_BR) { it.country.name })
    }

    override suspend fun availableClubs(competitionId: CompetitionId?): List<AvailableClub> {
        val competitions = catalog.findAll()
        return clubs.findAvailable()
            // `mapNotNull` + `?.let`: clube sem liga no catálogo (não acontece
            // num mundo do pack) simplesmente não entra.
            .mapNotNull { club -> competitions.leagueOf(club)?.let { AvailableClub(club, it) } }
            .filter { competitionId == null || it.league.id == competitionId }
            .sortedWith(compareByDescending<AvailableClub> { it.averageOverall }.thenBy(PT_BR) { it.club.name })
    }

    /**
     * Rótulo da temporada corrente de cada liga (`2026`, `2026/27`). Cada liga
     * está na SUA temporada (a europeia de 2026/27 convive com a brasileira de
     * 2027), e a tabela corrente dela já traz o rótulo. Liga sem tabela (save
     * no meio do seed) cai no ano do encaixe corrente; sem nenhum dos dois, a
     * liga fica fora do onboarding.
     */
    private suspend fun seasonLabels(leagues: List<GameCompetition>): Map<CompetitionId, String> {
        val fromTables = league.currentStandings().associate { it.competitionId to it.seasonLabel }
        val year = league.findLatest()?.season
        return leagues.mapNotNull { game ->
            (fromTables[game.id] ?: year?.let { game.seasonOf(it).label })?.let { game.id to it }
        }.toMap()
    }

    private companion object {
        /** `Collator` é um `Comparator<Any>` do JDK: ordem alfabética do português. */
        val PT_BR: Collator = Collator.getInstance(Locale.of("pt", "BR"))
    }
}
