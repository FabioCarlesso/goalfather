package com.carlesso.goalfather.application.seed

import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.Tiebreaker
import com.carlesso.goalfather.domain.model.WorldQualificationRules
import com.carlesso.goalfather.domain.rules.cupFormat

/**
 * Catálogo INTERINO do seed antigo (12 clubes brasileiros em 2 divisões): as
 * competições do Brasil com os ids e as regras do pack — Série A, Série B e
 * Copa do Brasil.
 *
 * #131 integrador: apagar quando a frente A entrar — o catálogo passa a vir do
 * pack (`WorldDataProvider`) e a ser gravado no save (`CompetitionCatalogRepository`).
 */
fun interimBrazilCatalog(): List<GameCompetition> {
    fun league(id: CompetitionId, name: String, tier: Int) = GameCompetition(
        competition = Competition(
            id = id,
            name = name,
            format = CompetitionFormat.RoundRobin(legs = 2),
            tiebreakers = BRASILEIRAO_TIEBREAKERS,
            foreignPlayerLimit = BRAZIL_FOREIGN_PLAYER_LIMIT,
        ),
        country = Countries.BRAZIL,
        kind = CompetitionKind.League(Division(tier)),
        span = SeasonSpan.CALENDAR_YEAR,
        pausesOnFifaWindows = false,
    )
    return listOf(
        league(WorldQualificationRules.BRA_SERIE_A, "Campeonato Brasileiro Série A", 1),
        league(WorldQualificationRules.BRA_SERIE_B, "Campeonato Brasileiro Série B", 2),
        GameCompetition(
            competition = Competition(
                id = WorldQualificationRules.BRA_COPA_DO_BRASIL,
                name = "Copa do Brasil",
                format = cupFormat(12),
                foreignPlayerLimit = BRAZIL_FOREIGN_PLAYER_LIMIT,
            ),
            country = Countries.BRAZIL,
            kind = CompetitionKind.NationalCup,
            span = SeasonSpan.CALENDAR_YEAR,
            pausesOnFifaWindows = true,
        ),
    )
}

/** Brasileirão 2026: 9 estrangeiros relacionados por jogo (CBF). */
private const val BRAZIL_FOREIGN_PLAYER_LIMIT = 9

private val BRASILEIRAO_TIEBREAKERS = listOf(
    Tiebreaker.POINTS,
    Tiebreaker.WINS,
    Tiebreaker.GOAL_DIFFERENCE,
    Tiebreaker.GOALS_FOR,
    Tiebreaker.HEAD_TO_HEAD,
    Tiebreaker.FEWER_RED_CARDS,
    Tiebreaker.FEWER_YELLOW_CARDS,
    Tiebreaker.DRAWING_OF_LOTS,
)
