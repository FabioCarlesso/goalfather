package com.carlesso.goalfather.application.port.out

import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.ContinentalCup

/**
 * Port de saída das edições das competições continentais (issue #132): uma por
 * (competição, temporada). Mesma postura do `CupRepository`: só quem venceu o
 * claim do encaixe (issue #46) grava, então o `save` é uma substituição simples.
 */
interface ContinentalRepository {
    /** A edição [season] de [competition], ou `null` se ela não foi sorteada. */
    suspend fun find(competition: CompetitionId, season: Int): ContinentalCup?

    /** A edição mais recente de [competition] — a em disputa, ou a que acabou de acabar. */
    suspend fun findLatest(competition: CompetitionId): ContinentalCup?

    /** Grava a edição (cria ou substitui a da mesma competição + temporada). */
    suspend fun save(edition: ContinentalCup)

    /**
     * Mundo sem continental: o default dos serviços montados à mão nos testes
     * escritos antes da #132. `companion object` de interface — o lugar natural
     * para um valor "nulo" do próprio tipo, sem classe utilitária.
     */
    companion object {
        val NONE: ContinentalRepository = object : ContinentalRepository {
            override suspend fun find(competition: CompetitionId, season: Int): ContinentalCup? = null
            override suspend fun findLatest(competition: CompetitionId): ContinentalCup? = null
            override suspend fun save(edition: ContinentalCup) = Unit
        }
    }
}
