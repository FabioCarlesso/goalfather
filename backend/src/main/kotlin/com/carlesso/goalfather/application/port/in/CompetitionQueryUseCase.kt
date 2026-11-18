package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.ClubSchedule
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionScorer
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.Standings

/**
 * Leituras da navegação pelo mundo (issue #133): competições, a tabela, os
 * jogos, a artilharia e a chave de cada uma, e o calendário do clube. Só
 * leitura — quem anda o relógio é o `PlayRoundUseCase`.
 */
interface CompetitionQueryUseCase {
    suspend fun competitions(): List<CompetitionOverview>

    suspend fun competition(id: CompetitionId): CompetitionLookup<CompetitionOverview>

    suspend fun standings(id: CompetitionId): CompetitionLookup<Standings>

    suspend fun matches(id: CompetitionId): CompetitionLookup<List<CompetitionMatchday>>

    suspend fun scorers(id: CompetitionId): CompetitionLookup<List<CompetitionScorer>>

    suspend fun bracket(id: CompetitionId): CompetitionLookup<Cup>

    /** `null` quando o clube não existe. */
    suspend fun clubSchedule(clubId: ClubId, weeks: Int): ClubSchedule?
}

/**
 * Uma competição do catálogo na temporada corrente: [season] é o ano de início
 * da edição que o país dela está jogando (`null` se ainda não começou) e
 * [clubCount], quantos clubes a disputam.
 */
data class CompetitionOverview(
    val game: GameCompetition,
    val season: Int?,
    val clubCount: Int,
) {
    val seasonLabel: String? get() = season?.let { game.seasonOf(it).label }
}

/** Um encaixe da temporada de uma competição e os jogos DELA nele. */
data class CompetitionMatchday(val matchday: Matchday, val matches: List<RoundMatch>)

/**
 * Resultado de uma leitura por competição. Três desfechos, e o controller
 * precisa distinguir os dois "não": a competição não existe (404
 * `COMPETITION_NOT_FOUND`) ou existe mas não tem o que se pediu — tabela num
 * mata-mata, chave numa liga (404 com outro código).
 *
 * `out T` (covariância): um `CompetitionLookup<Standings>` é usável onde se
 * espera `CompetitionLookup<Any>`. É o que deixa os dois `data object` serem
 * `CompetitionLookup<Nothing>` — `Nothing` é subtipo de tudo, então UM objeto
 * serve a qualquer `T`, sem um `Unknown<T>()` alocado por chamada.
 */
sealed interface CompetitionLookup<out T> {
    data class Found<out T>(val value: T) : CompetitionLookup<T>

    data object UnknownCompetition : CompetitionLookup<Nothing>

    data object NotAvailable : CompetitionLookup<Nothing>
}
