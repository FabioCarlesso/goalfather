package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ContractOffer
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.rules.ContractResult
import com.carlesso.goalfather.domain.rules.contractSpan
import com.carlesso.goalfather.domain.rules.seasonOn
import com.carlesso.goalfather.domain.rules.signContract
import com.carlesso.goalfather.domain.rules.signed
import com.carlesso.goalfather.domain.rules.worldSeasons
import java.time.LocalDate

/*
 * Assinatura de um contrato que o jogador JÁ aceitou (issue #138) — comum à
 * compra no mercado, à proposta aceita entre técnicos e à renovação. Fica na
 * aplicação porque precisa da temporada corrente, que sai do repositório.
 */

/**
 * A temporada corrente de [club] em [today]: a das tabelas do país dele (o
 * Brasil de 2027 convive com a Europa de 2026/27); clube sem liga cai na
 * temporada da data na janela dele.
 */
suspend fun LeagueRepository.contractSeasonOf(club: Club, today: LocalDate): Int {
    val span = club.contractSpan
    return currentStandings().worldSeasons()[club.country.code]
        ?.takeIf { !span.end(it).isBefore(today) }
        ?: span.seasonOn(today)
}

/**
 * [player] com o contrato de [offer] assinado com [club] a partir de [today].
 * Os termos são os NEGOCIADOS: o salário é o oferecido, não o derivado do
 * valor (`pricedFor`, que continua valendo para quem nasce com o mundo).
 */
suspend fun LeagueRepository.signAccepted(player: Player, club: Club, offer: ContractOffer, today: LocalDate): Player {
    val season = contractSeasonOf(club, today)
    return when (
        val result = signContract(player, club.id, offer.salary, today, season, club.contractSpan, offer.years, offer.releaseClause)
    ) {
        is ContractResult.Signed -> player.signed(result.contract)
        // `playerResponse` só aceita durações dentro de `allowedContractSeasons`:
        // chegar aqui é bug de quem chamou sem perguntar ao jogador.
        is ContractResult.Rejected -> error("contrato aceito pelo jogador ${player.id} recusado pela regra: ${result.reason}")
    }
}
