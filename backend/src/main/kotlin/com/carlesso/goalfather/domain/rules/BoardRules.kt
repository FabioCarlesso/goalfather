package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.BoardExpectation
import com.carlesso.goalfather.domain.model.BoardMood
import com.carlesso.goalfather.domain.model.BoardObjective
import com.carlesso.goalfather.domain.model.BoardReview
import com.carlesso.goalfather.domain.model.BoardVerdict
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Standings

/**
 * Regras da diretoria (issue #62). Funções PURAS e determinísticas: a meta sai
 * só da composição da liga (elencos + divisões), a confiança só da posição na
 * tabela. Nada de `Random` — a diretoria não sorteia, avalia.
 */

/** Confiança com que todo técnico começa a temporada. */
const val INITIAL_BOARD_CONFIDENCE: Int = 50

/**
 * Limiar da demissão: quem perde a meta com a confiança neste nível ou abaixo
 * é demitido. É também o que pinta o painel de [BoardMood.AT_RISK].
 */
const val FIRING_CONFIDENCE: Int = 25

/** A partir daqui a diretoria está satisfeita ([BoardMood.CONFIDENT]). */
const val CONFIDENT_CONFIDENCE: Int = 65

/** Ganho por rodada de quem está exatamente na meta; cada posição acima soma mais. */
private const val ON_TARGET_GAIN = 3
private const val GAIN_PER_POSITION_ABOVE = 2
private const val MAX_ROUND_GAIN = 8

/** Perda por posição abaixo da meta, por rodada, com teto. */
private const val LOSS_PER_POSITION_BELOW = 4
private const val MAX_ROUND_LOSS = 12

/** Fatia da divisão que a diretoria trata como candidata ao título (3 em 20). */
private const val TITLE_CONTENDERS_PER_20 = 3

/** Elenco considerado na força: um time titular. */
private const val SQUAD_RATING_SIZE = 11

/**
 * Força do ELENCO para a diretoria: soma do `overall` dos 11 melhores dividida
 * por 11 — vaga sem jogador conta zero, então um elenco desmontado (2 craques
 * e mais ninguém) é fraco, não "forte na média".
 *
 * Diferente de `Lineup.teamStrength()` de propósito: aquela é a força da
 * PARTIDA (escalação escolhida, fadiga descontada); a diretoria julga o elenco
 * que o clube tem, não a escalação da semana nem o cansaço da última rodada.
 * Assim a meta não depende de quando é calculada dentro da temporada.
 */
fun Club.squadRating(): Double =
    squad.map { it.overall }.sortedDescending().take(SQUAD_RATING_SIZE).sum().toDouble() / SQUAD_RATING_SIZE

/**
 * Objetivo de quem tem o [strengthRank]-ésimo elenco (1 = mais forte) numa
 * divisão de [divisionSize] clubes. Os candidatos ao título são ~15% da
 * divisão (3 numa liga de 20, 1 numa de 6), a metade de cima mira a metade de
 * cima e o resto, o meio da tabela.
 */
fun objectiveFor(strengthRank: Int, divisionSize: Int): BoardObjective {
    require(divisionSize >= 1) { "divisionSize deve ser >= 1: $divisionSize" }
    require(strengthRank in 1..divisionSize) { "strengthRank fora de 1..$divisionSize: $strengthRank" }
    val titleContenders = maxOf(1, divisionSize * TITLE_CONTENDERS_PER_20 / 20)
    return when {
        strengthRank <= titleContenders -> BoardObjective.TITLE
        strengthRank <= divisionSize / 2 -> BoardObjective.TOP_HALF
        else -> BoardObjective.MID_TABLE
    }
}

/**
 * Posição que materializa o objetivo numa divisão de [divisionSize] clubes:
 * terminar nela ou acima cumpre a meta.
 *
 * O meio da tabela é "escapar do último quarto" — mas nunca abaixo da metade
 * mais um, para que numa divisão pequena a meta modesta não vire a mesma da
 * metade de cima.
 */
fun BoardObjective.targetPosition(divisionSize: Int): Int = when (this) {
    BoardObjective.TITLE -> 1
    BoardObjective.TOP_HALF -> maxOf(1, divisionSize / 2)
    BoardObjective.MID_TABLE -> maxOf(divisionSize / 2 + 1, divisionSize - maxOf(1, divisionSize / 4))
}.coerceIn(1, maxOf(1, divisionSize))

/**
 * Metas da temporada [season] para TODOS os clubes da liga, divisão por
 * divisão: cada clube é ranqueado pela [squadRating] contra os da própria
 * divisão e recebe o objetivo do seu ranking, com a confiança inicial.
 *
 * Determinística dada a composição da liga — critério de aceite da issue.
 * Empate de força desempata pelo menor id: arbitrário, mas estável.
 */
fun boardExpectations(season: Int, clubs: Collection<Club>): Map<ClubId, BoardExpectation> =
    clubs.groupBy { it.division }.values.flatMap { divisionClubs ->
        val ranked = divisionClubs.sortedWith(
            compareByDescending<Club> { it.squadRating() }.thenBy { it.id.value },
        )
        ranked.mapIndexed { index, club ->
            val objective = objectiveFor(strengthRank = index + 1, divisionSize = ranked.size)
            club.id to BoardExpectation(
                season = season,
                objective = objective,
                targetPosition = objective.targetPosition(ranked.size),
            )
        }
    }.toMap()

/**
 * Quanto a confiança anda numa rodada, em função da distância entre a posição
 * atual e a meta. Na meta ou acima, sobe (mais quanto mais acima); abaixo, cai
 * — e cai mais rápido do que sobe: diretoria tem memória curta para vitória.
 * Os tetos impedem que uma rodada só decida a temporada.
 */
fun confidenceDelta(targetPosition: Int, position: Int): Int {
    val positionsAbove = targetPosition - position
    return if (positionsAbove >= 0) {
        minOf(MAX_ROUND_GAIN, ON_TARGET_GAIN + GAIN_PER_POSITION_ABOVE * positionsAbove)
    } else {
        -minOf(MAX_ROUND_LOSS, LOSS_PER_POSITION_BELOW * -positionsAbove)
    }
}

/** A expectativa depois de uma rodada de liga terminada em [position]. */
fun BoardExpectation.afterRound(position: Int): BoardExpectation =
    copy(
        confidence = (confidence + confidenceDelta(targetPosition, position))
            .coerceIn(BoardExpectation.CONFIDENCE_RANGE),
    )

/**
 * Humor do painel. Extension *property*: lê como atributo (`expectation.mood`),
 * mas é derivada — não há campo para divergir da confiança.
 */
val BoardExpectation.mood: BoardMood
    get() = when {
        confidence <= FIRING_CONFIDENCE -> BoardMood.AT_RISK
        confidence >= CONFIDENT_CONFIDENCE -> BoardMood.CONFIDENT
        else -> BoardMood.WARY
    }

/**
 * Decisão de fim de temporada. Cumpriu a meta → bônus do objetivo. Perdeu →
 * a confiança decide: quem ficou muito abaixo por muito tempo chega ao fim com
 * ela no chão e é demitido; um tropeço tardio com a diretoria satisfeita não
 * derruba ninguém. Por isso a avaliação usa a confiança JÁ com a última rodada.
 */
fun BoardExpectation.verdict(finalPosition: Int): BoardVerdict = when {
    finalPosition <= targetPosition -> BoardVerdict.TargetMet(objective.bonusCents)
    confidence <= FIRING_CONFIDENCE -> BoardVerdict.Fired
    else -> BoardVerdict.TargetMissed
}

/**
 * Balanço da diretoria de cada clube na temporada [season] que acabou.
 * Clube sem meta desta temporada (save anterior à issue) ou fora das tabelas
 * finais não tem balanço — ausência real, não um veredito inventado.
 */
fun seasonBoardReviews(
    season: Int,
    finalStandings: List<Standings>,
    clubs: Collection<Club>,
): List<BoardReview> {
    val finalPositions = finalStandings.flatMap { it.rows }.associate { it.clubId to it.position }
    return clubs.sortedBy { it.id.value }.mapNotNull { club ->
        val expectation = club.board?.takeIf { it.season == season } ?: return@mapNotNull null
        val finalPosition = finalPositions[club.id] ?: return@mapNotNull null
        BoardReview(
            clubId = club.id,
            clubName = club.name,
            season = season,
            objective = expectation.objective,
            targetPosition = expectation.targetPosition,
            finalPosition = finalPosition,
            confidence = expectation.confidence,
            verdict = expectation.verdict(finalPosition),
        )
    }
}
