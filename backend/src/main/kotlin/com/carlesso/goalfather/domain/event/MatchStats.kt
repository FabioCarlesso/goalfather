package com.carlesso.goalfather.domain.event

import kotlinx.serialization.Serializable

/**
 * Números de um time na partida (issue #57).
 *
 * Não é estado próprio: é uma PROJEÇÃO do stream de eventos, calculada por
 * [matchStats]. Manter só a derivação evita a classe de bug em que o placar
 * de estatísticas e o feed contam histórias diferentes.
 */
@Serializable
data class TeamStats(
    /** Finalizações: gols + defesas do goleiro adversário + chutes para fora. */
    val shots: Int,
    /** Delas, as que foram no gol: gols + defesas do goleiro adversário. */
    val shotsOnTarget: Int,
    /** Defesas feitas pelo goleiro DESTE time. */
    val saves: Int,
    val yellowCards: Int,
    val redCards: Int,
) {
    companion object {
        val EMPTY = TeamStats(shots = 0, shotsOnTarget = 0, saves = 0, yellowCards = 0, redCards = 0)
    }
}

/** Estatísticas dos dois lados, na mesma orientação do placar. */
@Serializable
data class MatchStats(
    val home: TeamStats,
    val away: TeamStats,
) {
    companion object {
        val EMPTY = MatchStats(TeamStats.EMPTY, TeamStats.EMPTY)
    }
}

/**
 * Agrega o stream em estatísticas de partida.
 *
 * `fold` sobre valores imutáveis (em vez de contadores `var`): o `when`
 * exaustivo sobre o `sealed interface` garante que uma variante nova de
 * `MatchEvent` quebre a compilação AQUI, e não passe despercebida somando
 * zero para sempre.
 */
fun Iterable<MatchEvent>.matchStats(): MatchStats = fold(MatchStats.EMPTY) { stats, event ->
    when (event) {
        is MatchEvent.Goal ->
            stats.onSide(event.home) { it.copy(shots = it.shots + 1, shotsOnTarget = it.shotsOnTarget + 1) }

        is MatchEvent.Miss ->
            stats.onSide(event.home) { it.copy(shots = it.shots + 1) }

        // Uma defesa conta para os DOIS lados: defesa de quem pegou,
        // finalização no gol de quem chutou. `Save.home` é o lado do goleiro.
        is MatchEvent.Save ->
            stats
                .onSide(event.home) { it.copy(saves = it.saves + 1) }
                .onSide(!event.home) { it.copy(shots = it.shots + 1, shotsOnTarget = it.shotsOnTarget + 1) }

        is MatchEvent.Card ->
            stats.onSide(event.home) {
                if (event.red) it.copy(redCards = it.redCards + 1)
                else it.copy(yellowCards = it.yellowCards + 1)
            }

        is MatchEvent.KickOff, is MatchEvent.Injury, is MatchEvent.FullTime -> stats
    }
}

/** Aplica [update] ao lado indicado, devolvendo novas instâncias (nada muta). */
private inline fun MatchStats.onSide(home: Boolean, update: (TeamStats) -> TeamStats): MatchStats =
    if (home) copy(home = update(this.home)) else copy(away = update(this.away))
