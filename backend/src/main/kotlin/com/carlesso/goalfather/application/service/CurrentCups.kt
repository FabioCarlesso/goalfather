package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.CupRepository
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.rules.WorldSeasons

/**
 * A copa corrente de cada copa nacional do [catalog] (issue #131): a da
 * temporada corrente do país dela. Extension `suspend` sobre o port: os dois
 * serviços que montam o plano do mundo (rodada e calendário) leem as copas do
 * mesmo jeito, sem uma classe só para isso.
 */
// #131 integrador: o `CupRepository` é chaveado só por temporada — basta com
// UMA copa nacional (a Copa do Brasil). Com mais de uma, chavear por
// (competição, temporada).
internal suspend fun CupRepository.currentCups(catalog: List<GameCompetition>, seasons: WorldSeasons): Map<CompetitionId, Cup> =
    catalog.filter { it.kind == CompetitionKind.NationalCup }
        .mapNotNull { game -> seasons[game.country.code]?.let { findBySeason(it) }?.let { game.id to it } }
        .toMap()
