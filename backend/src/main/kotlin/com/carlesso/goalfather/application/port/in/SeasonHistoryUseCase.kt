package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubCareer
import com.carlesso.goalfather.domain.model.ClubId
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
    /** Todas as temporadas encerradas, da mais recente para a mais antiga. */
    suspend fun history(): List<SeasonRecord>

    /** Uma temporada específica, ou `null` se ela nunca foi encerrada. */
    suspend fun season(season: Int): SeasonRecord?

    /** Perfil do técnico do clube, ou `null` se ele ainda não fechou temporada. */
    suspend fun career(clubId: ClubId): ClubCareer?
}
