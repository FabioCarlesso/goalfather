package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.SeasonCompetitions

/**
 * Regras de nacionalidade (issue #123). Funções PURAS, sem Spring.
 */

/**
 * O jogador conta como estrangeiro em [country]? Só quando NENHUMA das
 * nacionalidades dele é desse país. Base do limite de estrangeiros por
 * partida (issue #130) e das transferências internacionais (issue #142).
 *
 * Extension function: a regra se lê como frase (`player.isForeignIn(país)`)
 * sem que `Player` precise conhecer regra de competição — é o "método
 * utilitário estático" do Java, mas chamado como se fosse membro.
 */
fun Player.isForeignIn(country: Country): Boolean =
    nationality != country && secondNationality != country

/** O jogador é estrangeiro no [club]? Atalho para o país do clube. */
fun Player.isForeignAt(club: Club): Boolean = isForeignIn(club.country)

/** Os estrangeiros de [players] no [club], na ordem dada (issue #130). */
fun Club.foreignersAmong(players: List<Player>): List<Player> = players.filter { it.isForeignAt(this) }

/**
 * Limite de estrangeiros que vale para a escalação SALVA do clube (issue #130):
 * o da liga dele. A escalação não é por competição — o técnico salva uma só —,
 * e no mundo atual todas as competições do clube são nacionais, com o mesmo
 * limite. No apito inicial cada jogo reaplica o limite da própria competição
 * (`startingLineupIn`).
 */
fun Club.foreignPlayerLimit(): Int? =
    SeasonCompetitions.foreignPlayerLimitOf(SeasonCompetitions.leagueId(division))
