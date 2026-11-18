package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionScorer
import com.carlesso.goalfather.domain.model.FifaWindow
import com.carlesso.goalfather.domain.model.GameWeek
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.NextMatch
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.ScheduleWeek

/*
 * Leituras do mundo para a navegação do técnico (issue #133): o calendário
 * adiante como ele o ENXERGA hoje, as semanas do clube e a artilharia. Funções
 * puras sobre o plano do mundo (`SeasonPlan`) e os clubes — o serviço só junta
 * o que vem dos ports.
 */

/**
 * Os encaixes do plano DEPOIS de [after], com os jogos que já se conhecem.
 *
 * Mesma regra do `SeasonPlan.nextMatchOf`: a liga é conhecida de antemão
 * (Berger); de um mata-mata só se conhece a fase em disputa. Então cada copa
 * aparece no PRIMEIRO encaixe dela adiante e some dos seguintes (a fase
 * seguinte depende de resultados) — e não aparece em nenhum quando a fase
 * corrente ainda está em jogo no encaixe atual ([knockoutsInPlay]). O encaixe
 * continua na lista, sem os jogos desconhecidos: o calendário sabe QUANDO a
 * copa joga, só não sabe quem.
 */
fun SeasonPlan.visibleRoundsAfter(
    after: Matchday,
    clubs: Collection<Club>,
    knockoutsInPlay: Set<CompetitionId> = emptySet(),
): List<Round> {
    val unknown = knockoutsInPlay.toMutableSet()
    // `buildList` monta a lista com `add` e devolve a versão imutável: o
    // conjunto `unknown` cresce encaixe a encaixe, o que um `map` puro não
    // deixaria claro.
    return buildList {
        for (matchday in schedule.matchdays.filter { it > after }) {
            val round = roundAt(matchday, clubs)
            add(round.copy(matches = round.matches.filterNot { it.competition in unknown }))
            unknown += schedule.at(matchday).map { it.competition }.filter { dependsOnResults(it) }
        }
    }
}

/**
 * Os jogos de [competition] dependem de resultados anteriores (mata-mata)?
 * `when` exaustivo sobre o `sealed SeasonCompetition`: um papel novo (a
 * Libertadores da #132) obriga a decidir aqui se os jogos dele se conhecem
 * de antemão — um `is NationalCup` deixaria o novo papel cair no "conhecido"
 * em silêncio.
 */
private fun SeasonPlan.dependsOnResults(competition: CompetitionId): Boolean =
    when (competitions[competition]) {
        is SeasonCompetition.League -> false
        is SeasonCompetition.NationalCup -> true
        null -> false
    }

/**
 * As [count] semanas do clube [clubId] a partir de [from], cada uma com os
 * jogos dele nos [rounds] (em ordem de encaixe) e se ela toca uma data FIFA.
 * Semana sem jogo continua na lista: o calendário mostra a folga e a pausa.
 */
fun clubScheduleWeeks(
    clubId: ClubId,
    from: GameWeek,
    count: Int,
    rounds: List<Round>,
    fifaWindows: List<FifaWindow>,
): List<ScheduleWeek> {
    require(count >= 0) { "Quantidade de semanas negativa: $count" }
    val byWeek = rounds
        .flatMap { round -> round.matches.filter { it.involves(clubId) }.map { NextMatch(round.matchday, it) } }
        .groupBy { it.matchday.week }
    return List(count) { from + it }.map { week ->
        ScheduleWeek(
            week = week,
            fifaWindow = fifaWindows.reserves(week),
            matches = byWeek[week].orEmpty().sortedBy { it.matchday },
        )
    }
}

/**
 * Artilharia dos [clubs]: quem marcou, do que mais marcou para o que menos
 * (empate pelo nome, depois pelo id — ordem total, resposta estável), até
 * [limit]. Os gols são os da temporada do jogador (corte do MVP: o jogo não
 * separa gols por competição).
 */
fun Collection<Club>.topScorers(limit: Int): List<CompetitionScorer> =
    asSequence()
        .flatMap { club ->
            club.squad.asSequence()
                .filter { it.goals > 0 }
                .map { CompetitionScorer(it.id, it.name, club.id, club.name, it.goals) }
        }
        .sortedWith(
            compareByDescending<CompetitionScorer> { it.goals }
                .thenBy { it.playerName }
                .thenBy { it.playerId.value },
        )
        .take(limit)
        .toList()
