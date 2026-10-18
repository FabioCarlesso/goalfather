package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.SetTicketPriceUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.result.TicketPriceResult
import com.carlesso.goalfather.domain.rules.MAX_TICKET_PRICE_CENTS
import com.carlesso.goalfather.domain.rules.MIN_TICKET_PRICE_CENTS
import com.carlesso.goalfather.domain.rules.isTicketPriceAllowed

/**
 * Implementação de `SetTicketPriceUseCase` (issue #59). Só orquestra: a curva
 * de demanda vive em `domain/rules/FinanceRules` e roda na virada da rodada
 * (`PlayRoundService`), não aqui — mesma separação do treino com
 * `TrainingRules`.
 *
 * A ordem das checagens importa: dono ANTES de faixa. Responder
 * `PriceOutOfRange` para o clube de outro técnico contaria, de graça, qual é a
 * faixa praticada por um clube que o requisitante nem pode ver.
 *
 * Repetir o preço que já está gravado não persiste nada: o `save` evita uma
 * escrita (e um bump de `@Version`) que não muda nada.
 *
 * **Retry automático no conflito (issue #89).** Definir o preço é uma
 * SUBSTITUIÇÃO idempotente ("o preço passa a ser X"), não um efeito acumulado:
 * se a virada da rodada gravou o clube entre a leitura e o `save`, reler e
 * reaplicar dá exatamente o que o técnico pediu — sem pisar no caixa que a
 * rodada acabou de creditar. Cada tentativa refaz posse e faixa do zero.
 */
class SetTicketPriceService(
    private val clubRepo: ClubRepository,
) : SetTicketPriceUseCase {

    override suspend fun execute(
        clubId: ClubId,
        requesterId: Long,
        ticketPriceCents: Long,
    ): TicketPriceResult =
        retryOnConflict(isConflict = { it is TicketPriceResult.Conflict }) {
            attempt(clubId, requesterId, ticketPriceCents)
        }

    private suspend fun attempt(
        clubId: ClubId,
        requesterId: Long,
        ticketPriceCents: Long,
    ): TicketPriceResult {
        val club = clubRepo.findById(clubId)
            ?: return TicketPriceResult.ClubNotFound(clubId)

        if (club.ownerId != requesterId) {
            return TicketPriceResult.NotOwner(clubId)
        }

        if (!isTicketPriceAllowed(ticketPriceCents)) {
            return TicketPriceResult.PriceOutOfRange(
                price = ticketPriceCents,
                min = MIN_TICKET_PRICE_CENTS,
                max = MAX_TICKET_PRICE_CENTS,
            )
        }

        if (club.ticketPriceCents == ticketPriceCents) {
            return TicketPriceResult.Success(club)
        }

        return when (val saved = clubRepo.save(club.copy(ticketPriceCents = ticketPriceCents))) {
            is ClubSaveResult.Saved -> TicketPriceResult.Success(saved.club)
            is ClubSaveResult.Conflict -> TicketPriceResult.Conflict(saved.clubId)
        }
    }
}
