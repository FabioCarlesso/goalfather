package com.carlesso.goalfather.domain.model

/**
 * Uma competição do mundo DO SAVE (issue #131): o formato e as regras
 * ([competition]) + onde e quando ela se joga. É o que substitui o catálogo
 * interino de antes da #131 (`liga-d1`, `copa`): as competições passam a
 * vir do pack do mundo, são gravadas no save quando o jogo nasce e, dali em
 * diante, são lidas do save — mudar o pack não mexe num jogo em andamento.
 *
 * [country] é o país dono da competição — e, numa continental (issue #132), o
 * país cujo CALENDÁRIO a hospeda: a Libertadores segue a temporada do Brasil
 * (o único país da CONMEBOL com liga no save), é agendada junto com o
 * Brasileirão e a Copa do Brasil (é isso que impede um clube brasileiro de ter
 * dois jogos no mesmo encaixe) e fecha junto com a virada do Brasil. A
 * confederação sai do próprio país (`country.confederation`). Trocar por um
 * `CompetitionOwner` de verdade fica para quando duas confederações tiverem
 * continental no save (#145): hoje mudaria todo consumidor do catálogo para
 * nada.
 *
 * [pausesOnFifaWindows] e [span] ficam aqui, e não em [Competition], pelo
 * mesmo motivo de [CompetitionSeason]: são regras de calendário, não de formato.
 */
data class GameCompetition(
    val competition: Competition,
    val country: Country,
    val kind: CompetitionKind,
    val span: SeasonSpan,
    val pausesOnFifaWindows: Boolean,
) {
    val id: CompetitionId get() = competition.id

    /** A edição que começa em [year], com a janela do [span]. */
    fun seasonOf(year: Int): CompetitionSeason =
        CompetitionSeason.of(competition, span, year, pausesOnFifaWindows)
}

/**
 * O papel da competição no mundo. `sealed` porque cada papel liga o clube à
 * competição de um jeito diferente, e os pontos de consumo (calendário,
 * tabela, virada) precisam decidir caso a caso — um `when` exaustivo aponta
 * cada um deles quando entrar um papel novo (continental, #132).
 */
sealed interface CompetitionKind {
    /**
     * Liga de pontos corridos do país. O clube disputa a liga do seu país
     * (`Club.country`) cujo [tier] é a sua divisão (`Club.division`): Série A =
     * (BRA, 1), Série B = (BRA, 2), Premier League = (ENG, 1).
     */
    data class League(val tier: Division) : CompetitionKind

    /** Copa nacional mata-mata (Copa do Brasil): os clubes das ligas do país. */
    data object NationalCup : CompetitionKind

    /**
     * Competição continental com fase de grupos e mata-mata (Libertadores,
     * issue #132). Os participantes não saem de liga nenhuma: a edição sorteada
     * (`ContinentalCup`) é que diz quem joga.
     */
    data object Continental : CompetitionKind
}

/** A liga que [club] disputa entre [competitions], ou `null` se nenhuma é dele. */
fun List<GameCompetition>.leagueOf(club: Club): GameCompetition? = firstOrNull {
    val kind = it.kind
    kind is CompetitionKind.League && it.country.code == club.country.code && kind.tier == club.division
}
