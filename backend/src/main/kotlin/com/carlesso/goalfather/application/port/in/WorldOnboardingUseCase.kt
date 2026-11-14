package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.GameCompetition

/**
 * Onboarding do técnico (issue #131): país → liga → clube. Leituras do mundo
 * do save — o catálogo de competições e os clubes —, sem efeito colateral. A
 * escolha em si é o claim (`ClaimClubUseCase`), que já recusa clube com dono:
 * no multiplayer, cada técnico fica com um clube livre.
 */
interface WorldOnboardingUseCase {
    /**
     * Países com pelo menos uma liga jogável, por nome; as ligas de cada um da
     * elite para baixo. Copas não entram: o clube é escolhido pela liga.
     */
    suspend fun countries(): List<OnboardingCountry>

    /**
     * Clubes sem dono, só da liga [competitionId] quando informada (liga
     * inexistente = lista vazia). Ordem: força média decrescente, depois nome.
     */
    suspend fun availableClubs(competitionId: CompetitionId? = null): List<AvailableClub>
}

/** Um país do onboarding com as suas ligas. */
data class OnboardingCountry(val country: Country, val leagues: List<OnboardingLeague>)

/**
 * Uma liga do onboarding. [seasonLabel] é a temporada corrente DELA (`2026`
 * no Brasil, `2026/27` na Europa).
 */
data class OnboardingLeague(
    val league: GameCompetition,
    val clubCount: Int,
    val availableClubCount: Int,
    val seasonLabel: String,
)

/** Um clube livre e a liga que ele disputa — o card da escolha. */
data class AvailableClub(val club: Club, val league: GameCompetition) {
    /** Média do `overall` do elenco inteiro (0 se vazio): a "força" do card. */
    val averageOverall: Int get() = if (club.squad.isEmpty()) 0 else club.squad.sumOf { it.overall } / club.squad.size
}
