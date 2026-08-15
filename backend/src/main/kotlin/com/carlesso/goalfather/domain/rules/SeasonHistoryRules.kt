package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.CareerCampaign
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubCareer
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.SeasonRecord
import com.carlesso.goalfather.domain.model.SeasonStanding
import com.carlesso.goalfather.domain.model.SeasonTopScorer
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import kotlin.math.roundToInt

/** Pontos que uma vitória vale — o mesmo de `applyRoundToStandings`. */
private const val POINTS_PER_WIN = 3

/**
 * Aproveitamento em pontos percentuais: quanto o clube conquistou do que
 * disputou. Time sem jogo tem 0% (e não uma divisão por zero).
 */
fun StandingRow.pointsPercentage(): Int =
    if (played == 0) 0 else (points * 100.0 / (played * POINTS_PER_WIN)).roundToInt()

/**
 * Monta o snapshot de uma temporada encerrada (issue #60) — função PURA:
 * recebe as tabelas finais e os clubes COMO ESTAVAM no apito final e devolve
 * o record, sem tocar em repositório nenhum.
 *
 * Por isso a ordem importa no chamador: `PlayRoundService` chama esta função
 * ANTES de zerar `goals`/`yellowCards` na virada, senão o artilheiro sairia
 * sempre nulo.
 *
 * @param clubs todos os clubes da liga, com as estatísticas da temporada que
 *   acabou — inclusive os da IA, que também acumulam gols.
 */
fun seasonRecordOf(
    season: Int,
    finalStandings: List<Standings>,
    clubs: Collection<Club>,
): SeasonRecord {
    require(finalStandings.isNotEmpty()) { "Temporada $season sem tabela final" }

    // Elite primeiro, e dentro de cada divisão da 1ª à última posição: a lista
    // já sai na ordem de exibição, sem a UI reordenar nada.
    val standings = finalStandings
        .sortedBy { it.division }
        .flatMap { table ->
            table.rows.sortedBy { it.position }.map { it.toSeasonStanding(table.division) }
        }
    val champion = standings.firstOrNull()
    requireNotNull(champion) { "Temporada $season sem classificação final" }

    return SeasonRecord(
        season = season,
        champion = champion,
        finalStandings = standings,
        topScorer = clubs.topScorer(),
    )
}

/**
 * Carreira do clube ao longo das temporadas registradas, ou `null` se ele
 * ainda não fechou nenhuma — quem nunca terminou uma temporada não tem
 * história, e isso é diferente de ter uma história vazia.
 */
fun careerOf(clubId: ClubId, records: List<SeasonRecord>): ClubCareer? {
    val campaigns = records.mapNotNull { record ->
        record.finalStandings
            .firstOrNull { it.row.clubId == clubId }
            ?.let { CareerCampaign(record.season, it) }
    }
    val latest = campaigns.maxByOrNull { it.season } ?: return null

    return ClubCareer(
        clubId = clubId,
        // Nome da temporada mais recente: clube renomeado não vira dois clubes.
        clubName = latest.standing.row.clubName,
        seasonsPlayed = campaigns.size,
        titles = records.filter { it.champion.row.clubId == clubId }.map { it.season }.sorted(),
        bestCampaign = campaigns.minWithOrNull(BY_BEST_CAMPAIGN),
    )
}

/**
 * "Melhor" é primeiro a divisão (3º na elite vale mais que campeão da segunda),
 * depois a posição, e só então os pontos. O `season` no fim é desempate puro:
 * duas campanhas idênticas devem sempre devolver a mesma — determinismo é
 * requisito aqui como é na engine.
 */
private val BY_BEST_CAMPAIGN =
    compareBy<CareerCampaign>({ it.standing.division }, { it.standing.row.position })
        .thenByDescending { it.standing.row.points }
        .thenBy { it.season }

private fun StandingRow.toSeasonStanding(division: Division) = SeasonStanding(
    division = division,
    row = this,
    pointsPercentage = pointsPercentage(),
)

/**
 * Artilheiro da liga: mais gols entre todos os elencos. Empate resolve pelo
 * menor `playerId` — arbitrário, mas estável, então reprocessar a mesma virada
 * grava o mesmo artilheiro. `null` quando a temporada inteira acabou sem gol.
 */
private fun Collection<Club>.topScorer(): SeasonTopScorer? {
    val best = flatMap { club -> club.squad.map { club to it } }
        .filter { (_, player) -> player.goals > 0 }
        .maxWithOrNull(
            compareBy<Pair<Club, Player>> { it.second.goals }
                .thenByDescending { it.second.id.value },
        ) ?: return null

    val (club, player) = best
    return SeasonTopScorer(
        playerId = player.id,
        playerName = player.name,
        clubId = club.id,
        clubName = club.name,
        goals = player.goals,
    )
}
