package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.KnockoutPhase
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStage

/**
 * Calendário da temporada (issue #61): a sequência de datas, cada uma rodada
 * de liga ou fase de copa. Função PURA — mesmo nº de rodadas e de fases dá
 * sempre o mesmo calendário, e por isso ele nunca precisa ser persistido: o
 * serviço o recalcula a cada fim de data a partir dos clubes e da copa.
 *
 * As fases de copa são espalhadas por igual entre as rodadas de liga: a fase
 * `i` (1..C) entra logo depois da rodada `⌊i·L/(C+1)⌋`. Com 15 rodadas e 4
 * fases isso dá "a cada 3 rodadas, 1 fase de copa" (depois das rodadas 3, 6,
 * 9 e 12), que é o exemplo da issue; com as 5 rodadas do seed, uma fase
 * depois de cada uma das 4 primeiras.
 *
 * Duas garantias saem da fórmula: a data 1 é sempre rodada de liga (a virada
 * de temporada continua gerando só `generateRound(1, ...)`), e a final cai
 * ANTES da última rodada de liga sempre que há rodadas para isso — a copa
 * termina dentro da temporada e o campeão chega à virada. O `coerceIn` só
 * atua em ligas minúsculas (1 rodada), onde a copa inteira vem depois dela.
 *
 * Sem copa, o calendário é só a liga: `League(1)..League(L)`, exatamente o
 * que existia antes da issue.
 */
fun seasonCalendar(leagueMatchdays: Int, cupPhases: List<KnockoutPhase> = emptyList()): List<RoundStage> {
    require(leagueMatchdays >= 1) { "Temporada precisa de ao menos 1 rodada de liga: $leagueMatchdays" }

    val gaps = cupPhases.size + 1
    // `groupBy` com seletor de chave E de valor: rodada de liga → fases que vêm depois dela.
    val cupAfter: Map<Int, List<KnockoutPhase>> = cupPhases.withIndex().groupBy(
        keySelector = { (i, _) -> ((i + 1) * leagueMatchdays / gaps).coerceIn(1, leagueMatchdays) },
        valueTransform = { it.value },
    )

    return (1..leagueMatchdays).flatMap { matchday ->
        listOf<RoundStage>(RoundStage.League(matchday)) +
            cupAfter[matchday].orEmpty().map { RoundStage.Cup(it) }
    }
}

/** Calendário da temporada corrente: liga dos [clubs] + fases da [cup], se houver. */
fun seasonCalendar(clubs: List<Club>, cup: Cup?): List<RoundStage> =
    seasonCalendar(seasonRounds(clubs), cup?.phases.orEmpty())

/**
 * Monta a rodada da data [roundNumber] conforme o que o calendário reserva
 * para ela. O `when` é exaustivo sobre [RoundStage]: uma terceira competição
 * (supercopa, playoff) vira erro de compilação aqui, não uma data jogada como
 * se fosse liga.
 */
fun scheduleRound(
    roundNumber: Int,
    season: Int,
    stage: RoundStage,
    clubs: List<Club>,
    cup: Cup?,
): Round = when (stage) {
    is RoundStage.League -> generateRound(roundNumber, season, clubs, matchday = stage.matchday)
    is RoundStage.Cup -> cupRound(
        roundNumber = roundNumber,
        season = season,
        phase = stage.phase,
        // O calendário só reserva datas de copa quando ela existe; um `null`
        // aqui é bug de quem montou o calendário, não estado de jogo.
        cup = requireNotNull(cup) { "Data $roundNumber reservada para a copa, mas não há copa em $season" },
        clubs = clubs,
    )
}
