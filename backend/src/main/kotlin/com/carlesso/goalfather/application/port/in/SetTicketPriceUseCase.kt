package com.carlesso.goalfather.application.port.`in`

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.result.TicketPriceResult

/**
 * Definição do preço do ingresso do estádio (issue #59). O efeito só aparece
 * na bilheteria da próxima rodada em que o clube for mandante — aqui o técnico
 * apenas registra o preço.
 */
interface SetTicketPriceUseCase {
    suspend fun execute(clubId: ClubId, requesterId: Long, ticketPriceCents: Long): TicketPriceResult
}
