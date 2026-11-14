package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.Standings

/**
 * Consultas puras sobre o catálogo do save e as tabelas do mundo (issue #131).
 * Extension functions sobre `List<GameCompetition>`/`List<Standings>`: leem como
 * método do catálogo (`catalog.competitionOf(id)`) sem que o modelo precise de
 * uma classe "Catálogo" só para hospedar consultas.
 */

/** As regras (nome, disciplina, limite de estrangeiros) da competição [id], ou `null` fora do catálogo. */
fun List<GameCompetition>.competitionOf(id: CompetitionId): Competition? = firstOrNull { it.id == id }?.competition

/**
 * As regras de jogo de uma partida de [id]. Fora do catálogo (partida gravada
 * por um save antigo) vale o padrão: disciplina da CBF, sem limite de
 * estrangeiros — melhor jogar com a regra genérica que travar o encaixe.
 */
fun List<GameCompetition>.matchRulesOf(id: CompetitionId): Competition =
    competitionOf(id) ?: Competition(id, id.value, CompetitionFormat.RoundRobin())

/** As competições do país [code], na ordem do catálogo. */
fun List<GameCompetition>.ofCountry(code: CountryCode): List<GameCompetition> = filter { it.country.code == code }

/**
 * A temporada corrente de cada país, lida das tabelas correntes: o ano de
 * início da edição que as ligas dele estão jogando. As tabelas são o estado
 * persistido da temporada — a virada de um país grava as novas.
 */
fun List<Standings>.worldSeasons(): WorldSeasons =
    groupBy { it.country.code }.mapValues { (_, tables) -> tables.maxOf { it.season } }

/**
 * Ordem de exibição das tabelas do mundo: Brasil primeiro (o país do jogo),
 * os demais pelo código do país, e dentro do país a elite primeiro.
 */
fun List<Standings>.inWorldOrder(): List<Standings> =
    sortedWith(
        compareBy<Standings> { it.country.code.value != HOME_COUNTRY }
            .thenBy { it.country.code.value }
            .thenBy { it.division },
    )

/** O país "da casa": vem primeiro nas listagens do mundo. */
private const val HOME_COUNTRY = "BRA"
