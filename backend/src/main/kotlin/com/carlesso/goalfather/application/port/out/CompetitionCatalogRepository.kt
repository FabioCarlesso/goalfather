package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.GameCompetition

/**
 * As competições do mundo do save (issue #131). Gravadas uma vez, quando o
 * jogo nasce a partir do pack (`WorldDataProvider`), e lidas a cada encaixe
 * pelo calendário. Não é o `WorldDataProvider`: o pack é a FONTE para um jogo
 * novo; o catálogo é o que este save está jogando.
 */
interface CompetitionCatalogRepository {
    /** Todas as competições do save, na ordem em que foram gravadas. Vazia antes do seed. */
    suspend fun findAll(): List<GameCompetition>

    /** Substitui o catálogo inteiro (seed de um jogo novo e reset do E2E). */
    suspend fun replaceAll(competitions: List<GameCompetition>)
}
