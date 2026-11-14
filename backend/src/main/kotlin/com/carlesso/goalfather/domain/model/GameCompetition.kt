package com.carlesso.goalfather.domain.model

/**
 * Uma competição do mundo DO SAVE (issue #131): o formato e as regras
 * ([competition]) + onde e quando ela se joga. É o que substitui o catálogo
 * interino [SeasonCompetitions] (`liga-d1`, `copa`): as competições passam a
 * vir do pack do mundo, são gravadas no save quando o jogo nasce e, dali em
 * diante, são lidas do save — mudar o pack não mexe num jogo em andamento.
 *
 * O MVP só tem competições NACIONAIS (a Libertadores entra com a #132), por
 * isso [country] não é nulo. Quando vier a primeira continental, o dono vira
 * um `CompetitionOwner` (já existe em `world/WorldPack.kt`).
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
}

/** A liga que [club] disputa entre [competitions], ou `null` se nenhuma é dele. */
fun List<GameCompetition>.leagueOf(club: Club): GameCompetition? = firstOrNull {
    val kind = it.kind
    kind is CompetitionKind.League && it.country.code == club.country.code && kind.tier == club.division
}
