package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.DisciplineRules
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.SeasonCompetitions
import com.carlesso.goalfather.domain.model.Suspension

/**
 * Suspensão por cartões e limite de estrangeiros no apito inicial (issue #130).
 * Funções PURAS, sem Spring: o `PlayRoundService` só as chama.
 */

/** Cartões que UM jogador levou numa partida. */
data class MatchCards(val yellow: Int = 0, val red: Int = 0) {
    init {
        require(yellow >= 0 && red >= 0) { "Cartões negativos: $yellow amarelos, $red vermelhos" }
    }

    /** Vermelho direto ou segundo amarelo: expulsão, gancho automático. */
    val sentOff: Boolean get() = red >= 1 || yellow >= 2
}

/**
 * Efeito disciplinar de UMA partida de [competition] sobre um jogador do clube
 * que a disputou:
 *
 * 1. Quem estava suspenso não jogou (`startingLineup` o deixou fora): cumpre um
 *    jogo do gancho, e o cumprido por inteiro sai da lista.
 * 2. Expulso ([MatchCards.sentOff]) pega [DisciplineRules.matchesPerRedCard]
 *    jogos. Os amarelos DESTA partida não somam ao acumulado — o amarelo que
 *    antecede a expulsão é absorvido por ela, como no regulamento da CBF.
 * 3. Um amarelo soma ao acumulado da competição; ao chegar a
 *    [DisciplineRules.yellowsForSuspension], vira 1 jogo de gancho e zera.
 *
 * Os contadores são DA competição: o jogador suspenso na liga e com 2 amarelos
 * na copa continua com os mesmos 2 amarelos na copa depois deste jogo de liga.
 */
fun Player.afterMatchIn(competition: CompetitionId, rules: DisciplineRules, cards: MatchCards): Player {
    val serving = suspensionIn(competition)
    if (serving != null) {
        val remaining = serving.matchesLeft - 1
        val others = suspensions - serving
        // `takeIf` ANTES do `copy`: construir `Suspension(…, 0)` já violaria o
        // `init` — o gancho cumprido simplesmente não é recriado.
        val rest = remaining.takeIf { it >= 1 }?.let { serving.copy(matchesLeft = it) }
        return copy(suspensions = others + listOfNotNull(rest))
    }

    if (cards.sentOff) return suspendedIn(competition, rules.matchesPerRedCard)
    if (cards.yellow == 0) return this

    val pending = (pendingYellows[competition] ?: 0) + cards.yellow
    return if (pending >= rules.yellowsForSuspension) {
        copy(pendingYellows = pendingYellows - competition).suspendedIn(competition, 1)
    } else {
        copy(pendingYellows = pendingYellows + (competition to pending))
    }
}

private fun Player.suspendedIn(competition: CompetitionId, matches: Int): Player =
    copy(suspensions = suspensions.filterNot { it.competitionId == competition } + Suspension(competition, matches))

/**
 * Aplica [afterMatchIn] ao elenco de um clube que disputou uma partida de
 * [competition]. Clube que não jogou não chama: o gancho conta jogos DO CLUBE
 * naquela competição, não semanas.
 */
fun List<Player>.afterMatchIn(
    competition: CompetitionId,
    rules: DisciplineRules,
    cards: Map<PlayerId, MatchCards>,
): List<Player> = map { it.afterMatchIn(competition, rules, cards[it.id] ?: MatchCards()) }

/**
 * Zera os amarelos pendentes de [competition] — a virada para o mata-mata de
 * competições com [DisciplineRules.resetsYellowsAtKnockout]. Suspensões em
 * curso NÃO são perdoadas: só o acumulado.
 *
 * Ainda sem chamador: nenhuma competição do mundo atual tem fase de grupos
 * antes do mata-mata. Entra com as continentais do pack (#131).
 */
fun List<Player>.enteringKnockoutOf(competition: CompetitionId, rules: DisciplineRules): List<Player> =
    if (!rules.resetsYellowsAtKnockout) this
    else map { if (competition in it.pendingYellows) it.copy(pendingYellows = it.pendingYellows - competition) else it }

/**
 * A competição em que cada clube joga neste encaixe. O calendário nunca põe um
 * clube em dois jogos do mesmo encaixe (ele reserva os clubes por encaixe),
 * então a associação é única.
 */
fun Round.competitionByClub(): Map<ClubId, CompetitionId> =
    matches.flatMap { listOf(it.homeClubId to it.competition, it.awayClubId to it.competition) }.toMap()

/**
 * O time que o clube leva a um jogo de [competition]: sem suspensos dela e
 * dentro do limite de estrangeiros dela. Único ponto que liga o apito inicial
 * às regras da competição — engine, bilheteria e desgaste usam a MESMA conta.
 */
fun Club.startingLineupIn(competition: CompetitionId): Lineup =
    startingLineup(competition, SeasonCompetitions.foreignPlayerLimitOf(competition))

/** Efeito disciplinar de um jogo de [competition], com a regra dela. */
fun List<Player>.afterMatchIn(competition: CompetitionId, cards: Map<PlayerId, MatchCards>): List<Player> =
    afterMatchIn(competition, SeasonCompetitions.disciplineOf(competition), cards)

/** Zera amarelos pendentes e suspensões: a pré-temporada começa limpa. */
fun Player.withCleanRecord(): Player = copy(pendingYellows = emptyMap(), suspensions = emptyList())
