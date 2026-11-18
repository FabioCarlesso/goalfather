package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ContinentalRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.ContinentalCup
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.QualificationRule
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.rules.ContinentalDraw
import com.carlesso.goalfather.domain.rules.WorldSeasons
import com.carlesso.goalfather.domain.rules.continentalDrawSeed
import com.carlesso.goalfather.domain.rules.continentalEntrants
import com.carlesso.goalfather.domain.rules.continentalQualifiers
import com.carlesso.goalfather.domain.rules.drawContinental
import com.carlesso.goalfather.domain.rules.recordRound
import com.carlesso.goalfather.domain.rules.seededForContinental
import com.carlesso.goalfather.domain.rules.tiebreakersOf
import kotlin.random.Random

/*
 * Continentais no mundo (issue #132): os pontos em que o encaixe e a virada
 * falam com a Libertadores. Extension functions `suspend` sobre o port, como
 * `CupRepository.currentCups` — o `PlayRoundService` e o `CalendarService`
 * leem as edições do mesmo jeito, e a regra em si mora no domínio
 * (`ContinentalRules.kt`). Assim o `PlayRoundService` só ganha chamadas.
 */

/** A edição corrente de cada continental do [catalog]: a da temporada do país que a hospeda. */
internal suspend fun ContinentalRepository.currentContinentals(
    catalog: List<GameCompetition>,
    seasons: WorldSeasons,
): Map<CompetitionId, ContinentalCup> =
    catalog.filter { it.kind == CompetitionKind.Continental }
        .mapNotNull { game -> seasons[game.country.code]?.let { find(game.id, it) }?.let { game.id to it } }
        .toMap()

/** As edições depois do encaixe + o prêmio de quem passou de fase nele. */
internal data class ContinentalRoundResult(
    val editions: Map<CompetitionId, ContinentalCup>,
    val prizes: Map<ClubId, Money>,
)

/**
 * Registra o encaixe [finished] em cada edição [current] e grava as que
 * mudaram. Antes das finanças, como a copa: o prêmio entra no caixa na mesma
 * gravação da bilheteria.
 */
internal suspend fun ContinentalRepository.recordRound(
    current: Map<CompetitionId, ContinentalCup>,
    finished: Round,
    catalog: List<GameCompetition>,
): ContinentalRoundResult {
    val outcomes = current.mapValues { (id, edition) -> edition.recordRound(finished, catalog.tiebreakersOf(id)) }
    outcomes.forEach { (id, outcome) -> if (outcome.edition != current[id]) save(outcome.edition) }
    return ContinentalRoundResult(
        editions = outcomes.mapValues { (_, outcome) -> outcome.edition },
        prizes = outcomes.values.flatMap { it.prizes.entries }.associate { it.key to it.value },
    )
}

/**
 * Sorteia e grava a edição [nextSeason] de cada continental [hosted] pelo país
 * que acabou de virar (issue #132):
 *
 * - inscritos: a cota de cada país da edição que acabou ([continentalEntrants]),
 *   com os classificados pelas regras de vaga do país ([rules] sobre as tabelas
 *   finais e o campeão da copa nacional) e o campeão da edição anterior;
 * - cabeças de chave: o campeão, depois a força do elenco.
 *
 * Inscritos que não fecham com o formato, ou sorteio impossível, deixam a
 * edição sem sorteio — o país joga só as competições nacionais, em vez de
 * derrubar a virada (mesma postura da copa nacional, `drawNationalCup`).
 */
internal suspend fun ContinentalRepository.drawNextEditions(
    hosted: List<GameCompetition>,
    nextSeason: Int,
    ended: Map<CompetitionId, ContinentalCup>,
    rules: List<QualificationRule>,
    finalStandings: List<Standings>,
    cupChampions: Map<CompetitionId, ClubId>,
    clubs: Collection<Club>,
    leagueCountries: Set<CountryCode>,
) {
    for (game in hosted) {
        val previous = ended[game.id] ?: findLatest(game.id) ?: continue
        val qualified = continentalQualifiers(game.id, rules, finalStandings, cupChampions)
        val titleholder = previous.champion?.id
        val entrants = continentalEntrants(previous, qualified, clubs, leagueCountries, titleholder)
        val seeded = seededForContinental(entrants, titleholder)
        val rng = Random(continentalDrawSeed(nextSeason, game.id))
        when (val draw = drawContinental(nextSeason, game, seeded, previous.finalVenue, rng)) {
            is ContinentalDraw.Drawn -> save(draw.edition)
            is ContinentalDraw.WrongSize, ContinentalDraw.Impossible -> Unit
        }
    }
}
