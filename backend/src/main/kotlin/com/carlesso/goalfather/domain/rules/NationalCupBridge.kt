package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.RoundMatch
import kotlin.random.Random

/**
 * Os ÚNICOS pontos em que o mundo (calendário, virada, `startWorld`) fala com o
 * motor da copa (issue #131). O resto do código só conhece estas funções — trocar o motor (a Copa do Brasil 2026 com fases nomeadas, ida e
 * volta e entrantes por fase) é trocar o corpo delas, não caçar chamadas.
 *
 * Hoje implementadas sobre o motor ATUAL (`drawCup` + `cup.phases`, jogo
 * único, chave de até [MAX_CUP_CLUBS] clubes): com mais inscritos que isso a
 * copa não é sorteada (`null`) e o país joga só as ligas.
 */

/**
 * Os inscritos da copa nacional, do mais forte ao mais fraco — quem é mais
 * forte entra nas fases mais tardias no formato real. "Mais forte" = divisão
 * mais alta, depois o elenco ([squadRating]); o id desempata para a ordem ser
 * total (sem reputação no domínio ainda).
 */
fun seededForCup(clubs: Collection<Club>): List<Club> =
    clubs.sortedWith(
        compareBy<Club> { it.division }
            .thenByDescending { it.squadRating() }
            .thenBy { it.id.value },
    )

/**
 * Seed do sorteio da copa [competition] na temporada [season]: a da temporada
 * ([cupDrawSeed]) misturada ao id da copa, para duas copas do mesmo ano não
 * sortearem com a mesma sequência. `String.hashCode` é definido pela
 * especificação do Java — estável entre execuções.
 */
fun nationalCupDrawSeed(season: Int, competition: CompetitionId): Long =
    cupDrawSeed(season) xor competition.value.hashCode().toLong()

/**
 * Sorteia a copa [competition] da temporada [season] entre [clubs] (os clubes
 * das ligas do país). `null` quando o motor não comporta os inscritos.
 */
fun drawNationalCup(season: Int, competition: GameCompetition, clubs: Collection<Club>, rng: Random): Cup? {
    val seeded = seededForCup(clubs)
    // #131 integrador: trocar pela API da frente C —
    // `drawCup(season, competition.competition, seeded, rng)`, sem o `canHoldCup`.
    if (!canHoldCup(seeded)) return null
    return drawCup(season, seeded, rng)
}

/**
 * Quantos encaixes do calendário a copa reserva. Cada um reserva TODOS os
 * clubes das ligas do país (ver `countryPlan`): os confrontos das fases
 * seguintes só existem depois dos resultados.
 */
// #131 integrador: trocar pela API da frente C — `cup.calendarRounds`.
fun cupRounds(cup: Cup): Int = cup.phases.size

/**
 * Os jogos da copa [competition] no encaixe [matchday]: os confrontos em aberto
 * da fase corrente, com a competição do CATÁLOGO na partida (é por ela que a
 * suspensão, o limite de estrangeiros e a tabela distinguem os jogos).
 */
fun nationalCupMatches(matchday: Matchday, cup: Cup, competition: GameCompetition, clubs: Collection<Club>): List<RoundMatch> =
    // #131 integrador: com o motor da frente C a partida já sai com o
    // `competitionId` da copa; o `copy` some.
    cupMatches(matchday, cup, clubs).map {
        it.copy(competition = competition.id, competitionName = competition.competition.name)
    }
