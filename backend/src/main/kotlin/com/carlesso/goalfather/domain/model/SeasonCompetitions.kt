package com.carlesso.goalfather.domain.model

/**
 * Ids e nomes das competições do mundo ATUAL do jogo (issue #125): uma liga de
 * pontos corridos por divisão + a copa. Com o calendário semanal cada partida
 * diz a que competição pertence, e estes são os ids que o seed usa.
 *
 * **Interino** (mesmo padrão de `Countries`): quando o mundo do MVP sair do
 * pack (issue #131), as competições vêm do `WorldDataProvider` com os ids do
 * pack (`bra-serie-a`, `bra-copa-do-brasil`…) e este objeto deixa de existir.
 *
 * `object` porque é um catálogo sem estado — o singleton do Kotlin, sem a
 * classe com construtor privado + `static` do Java.
 */
object SeasonCompetitions {
    /** A copa da temporada (issue #61). */
    val CUP_ID: CompetitionId = CompetitionId("copa")
    const val CUP_NAME: String = "Copa"

    /** A liga da [division]: `liga-d1`, `liga-d2`… */
    fun leagueId(division: Division): CompetitionId = CompetitionId("$LEAGUE_PREFIX${division.value}")

    fun leagueName(division: Division): String = "Liga — Divisão ${division.value}"

    /**
     * Limite de estrangeiros por partida das competições nacionais (issue #130).
     *
     * Fonte: CBF — Brasileirão 2026 permite 9 estrangeiros RELACIONADOS por
     * jogo; em set/2026 os clubes aprovaram a redução gradual para 8 (2027),
     * 7 (2028), 6 (2029) e 5 (2030). O jogo não tem banco de reservas, então o
     * limite vale para os 11 em campo. Quando as competições vierem do pack do
     * mundo (#131), o número passa a ser dado do `Competition` de cada uma.
     */
    const val NATIONAL_FOREIGN_PLAYER_LIMIT: Int = 9

    /**
     * Regra disciplinar da competição [id]. Liga e copa do mundo atual são
     * ambas da CBF: 3 amarelos ou 1 vermelho = 1 jogo, sem zerar no mata-mata.
     */
    @Suppress("UNUSED_PARAMETER")
    fun disciplineOf(id: CompetitionId): DisciplineRules = DisciplineRules.DEFAULT

    /** Limite de estrangeiros da competição [id] — todas as do mundo atual são nacionais. */
    @Suppress("UNUSED_PARAMETER")
    fun foreignPlayerLimitOf(id: CompetitionId): Int? = NATIONAL_FOREIGN_PLAYER_LIMIT

    /** Nome de exibição de [id], ou `null` se não é uma competição do mundo atual. */
    fun nameOf(id: CompetitionId): String? = when {
        id == CUP_ID -> CUP_NAME
        else -> id.value.removePrefix(LEAGUE_PREFIX).takeIf { it != id.value }
            ?.toIntOrNull()?.takeIf { it >= 1 }?.let { leagueName(Division(it)) }
    }

    private const val LEAGUE_PREFIX = "liga-d"
}
