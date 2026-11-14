package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubCareer
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.SeasonRecord

/**
 * Leitura do histórico de temporadas (issue #60) — a memória da carreira que
 * a virada de temporada apagava.
 *
 * Porta de ENTRADA e não o repositório direto no controller porque a carreira
 * (`career`) é uma projeção de regra de domínio: quem conta títulos e elege a
 * melhor campanha é `domain/rules/SeasonHistoryRules.kt`, não o adapter web.
 */
interface SeasonHistoryUseCase {
    /**
     * Todas as temporadas encerradas, da mais recente para a mais antiga; na
     * mesma temporada, o Brasil primeiro e os demais países pelo código.
     */
    suspend fun history(): List<SeasonRecord>

    /**
     * A temporada [season] de [country] (issue #131), ou `null` se ela nunca
     * foi encerrada. Sem país, a primeira encerrada naquele ano na ordem de
     * [history] — o Brasil, quando ele já virou.
     */
    suspend fun season(season: Int, country: CountryCode? = null): SeasonRecord?

    /** Perfil do técnico do clube, ou `null` se ele ainda não fechou temporada. */
    suspend fun career(clubId: ClubId): ClubCareer?
}
