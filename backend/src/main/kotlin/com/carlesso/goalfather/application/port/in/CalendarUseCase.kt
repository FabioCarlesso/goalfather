package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.CalendarView
import com.carlesso.goalfather.domain.model.ClubId

/**
 * Leitura do relógio do jogo (issue #125): encaixe corrente e próximo jogo do
 * clube. Só leitura — quem anda o relógio é o `PlayRoundUseCase`.
 */
interface CalendarUseCase {
    /** Sem [clubId], só a data corrente; com ele, também o próximo jogo do clube. */
    suspend fun calendar(clubId: ClubId?): CalendarView
}
