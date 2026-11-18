package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.Discipline
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.PlayedMatch
import com.carlesso.goalfather.domain.model.QualificationRule
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.TableZone
import com.carlesso.goalfather.domain.model.Tiebreaker
import kotlin.random.Random

/**
 * Aplica o resultado de um encaixe finalizado à tabela de UMA liga.
 *
 * Função PURA — recebe e devolve `Standings` sem efeitos colaterais.
 * Espelha `applyRoundToStandings` em `frontend/src/mocks/seed.ts`.
 *
 * Regras:
 * - Vitória vale 3 pts, empate 1, derrota 0.
 * - Ordenação pelos [tiebreakers] DA LIGA (issue #131: Brasileirão com
 *   vitórias, confronto direto e cartões; La Liga com confronto direto logo
 *   depois dos pontos…), aplicados por [rankTable] com o histórico de
 *   resultados da própria tabela ([Standings.results]) e os cartões de cada
 *   linha. O sorteio (`DRAWING_OF_LOTS`) usa um `Random` semeado pela
 *   competição + temporada: reprocessar dá a mesma tabela.
 * - Só contam os jogos DESTA liga (`match.competition`) — o mesmo encaixe é
 *   aplicado a todas as tabelas do mundo.
 * - Partidas com `status != Finished` são ignoradas (rodada parcial).
 * - Jogo de COPA não mexe na tabela (issue #61): o `when` sobre o `sealed`
 *   [RoundStage] força a decisão aqui em vez de confiar que o chamador filtre.
 * - A tabela conta a rodada pela `matchday` da LIGA — com a copa no meio, o
 *   encaixe da 3ª rodada do campeonato é outro, e é a rodada que o técnico lê.
 *   Encaixe sem jogo desta tabela devolve a tabela intacta.
 *
 * @param cards cartões de cada clube NESTE encaixe (insumo dos critérios
 *   `FEWER_*_CARDS`); clube ausente = nenhum cartão.
 */
fun applyRoundToStandings(
    round: Round,
    current: Standings,
    tiebreakers: List<Tiebreaker> = Competition.DEFAULT_TIEBREAKERS,
    cards: Map<ClubId, Discipline> = emptyMap(),
): Standings {
    val byId = current.rows.associateBy { it.clubId }.toMutableMap()
    val leagueMatches = round.matches.mapNotNull { match ->
        when (val stage = match.stage) {
            is RoundStage.League -> (match to stage.matchday).takeIf {
                match.competition == current.competitionId && match.homeClubId in byId && match.awayClubId in byId
            }
            // Grupo de continental (issue #132) tem a tabela DELE, dentro da edição.
            is RoundStage.Cup, is RoundStage.Group -> null
        }
    }
    // `maxOfOrNull` + elvis: nenhum jogo de liga desta tabela → nada muda.
    val matchday = leagueMatches.maxOfOrNull { it.second } ?: return current

    val played = mutableListOf<PlayedMatch>()
    for ((match, _) in leagueMatches) {
        if (match.status != RoundStatus.Finished) continue
        val home = byId[match.homeClubId] ?: continue
        val away = byId[match.awayClubId] ?: continue

        byId[match.homeClubId] = home.updatedForMatch(match.homeGoals, match.awayGoals, cards[home.clubId])
        byId[match.awayClubId] = away.updatedForMatch(match.awayGoals, match.homeGoals, cards[away.clubId])
        played += PlayedMatch(match.homeClubId, match.awayClubId, match.homeGoals, match.awayGoals)
    }

    val results = current.results + played
    // A faixa é da POSIÇÃO, não do clube (issue #128): quem sobe na tabela
    // herda a faixa do lugar que ocupou.
    val zoneAt = current.rows.associate { it.position to it.zone }
    return current.copy(
        round = matchday,
        results = results,
        rows = rankTable(
            rows = byId.values.toList(),
            tiebreakers = tiebreakers,
            results = results,
            discipline = byId.values.associate { it.clubId to Discipline(it.yellowCards, it.redCards) },
            lots = Random(lotsSeed(current)),
        ).map { it.copy(zone = zoneAt[it.position] ?: TableZone.NONE) },
    )
}

/**
 * Seed do sorteio da tabela: competição + temporada. `String.hashCode` é
 * definido pela especificação do Java — estável entre execuções.
 */
private fun lotsSeed(table: Standings): Long =
    (table.season.toLong() shl 32) xor table.competitionId.value.hashCode().toLong()

/**
 * Atualiza uma linha após uma partida. Extension function private —
 * encapsula a regra de pontuação perto do tipo que ela manipula.
 */
private fun StandingRow.updatedForMatch(ownGoals: Int, againstGoals: Int, cards: Discipline?): StandingRow {
    val pointsAdded = when {
        ownGoals > againstGoals -> 3
        ownGoals == againstGoals -> 1
        else -> 0
    }
    return copy(
        played = played + 1,
        goalsFor = goalsFor + ownGoals,
        goalsAgainst = goalsAgainst + againstGoals,
        goalDifference = goalDifference + (ownGoals - againstGoals),
        wins = wins + (if (ownGoals > againstGoals) 1 else 0),
        draws = draws + (if (ownGoals == againstGoals) 1 else 0),
        losses = losses + (if (ownGoals < againstGoals) 1 else 0),
        points = points + pointsAdded,
        yellowCards = yellowCards + (cards?.yellowCards ?: 0),
        redCards = redCards + (cards?.redCards ?: 0),
    )
}

/**
 * Tabela zerada da liga [league] na temporada [season]: os clubes do país
 * dela no tier dela, em ordem de id. `null` para liga sem clube — o país não a
 * disputa (o pack pode descrever uma liga que o save não povoou).
 */
fun leagueStandings(league: GameCompetition, season: Int, clubs: Collection<Club>): Standings? {
    val tier = when (val kind = league.kind) {
        is CompetitionKind.League -> kind.tier
        // Copa não tem tabela; a continental guarda as dos grupos na edição (#132).
        CompetitionKind.NationalCup, CompetitionKind.Continental -> return null
    }
    val members = clubs.filter { it.country.code == league.country.code && it.division == tier }
    if (members.isEmpty()) return null
    return Standings(
        competitionId = league.id,
        competitionName = league.competition.name,
        country = league.country,
        season = season,
        seasonLabel = league.seasonOf(season).label,
        round = 0,
        division = tier,
        rows = members.sortedBy { it.id.value }.mapIndexed { i, club ->
            StandingRow(position = i + 1, clubId = club.id, clubName = club.name)
        },
    )
}

/**
 * Tabelas zeradas de todas as ligas de [competitions] na temporada [season],
 * com as faixas das regras de vaga do mundo ([worldQualificationRules]) — o
 * ponto de partida de toda temporada nova de um país e do mundo novo.
 */
fun seasonStandings(season: Int, competitions: List<GameCompetition>, clubs: Collection<Club>): List<Standings> {
    val tables = competitions.mapNotNull { leagueStandings(it, season, clubs) }
    val rules = worldQualificationRules(competitions, tables.associate { it.competitionId to it.rows.size })
    return tables.map { it.withZones(it.competitionId, rules) }
}

/** Os critérios de desempate da liga [id] no catálogo; o padrão do jogo se ela não estiver lá. */
fun List<GameCompetition>.tiebreakersOf(id: CompetitionId): List<Tiebreaker> =
    firstOrNull { it.id == id }?.competition?.tiebreakers ?: Competition.DEFAULT_TIEBREAKERS

/** As regras que valem para as tabelas [tables] (ver [worldQualificationRules]). */
fun List<GameCompetition>.rulesFor(tables: Collection<Standings>): List<QualificationRule> =
    worldQualificationRules(this, tables.associate { it.competitionId to it.rows.size })
