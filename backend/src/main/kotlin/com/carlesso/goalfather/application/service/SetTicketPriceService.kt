package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.SetTicketPriceUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
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
 * Repetir o preço que já está gravado não persiste nada: evita uma
 * escrita (e um bump de `@Version`) que não muda nada.
 *
 * **Sem conflito possível (issue #89, revisão do PR #92).** Definir o preço é
 * uma SUBSTITUIÇÃO de uma coluna ("o preço passa a ser X"), não um efeito
 * acumulado — então não precisa de ler-modificar-gravar o agregado. O
 * `updateTicketPrice` grava só essa coluna num UPDATE atômico: não pisa no
 * caixa que a rodada acabou de creditar e não perde para nenhuma escrita
 * concorrente (antes, com retry, a martelada contínua ainda esgotava as
 * tentativas). A versão sobe mesmo assim, e quem leu o clube antes e grava via
 * `save` recebe `Conflict`.
 */
class SetTicketPriceService(
    private val clubRepo: ClubRepository,
) : SetTicketPriceUseCase {

    override suspend fun execute(
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

        // `null` só se o clube sumir entre a leitura e o UPDATE (clubes não são
        // apagados hoje); o `?.let`/`?:` trata a ausência no tipo, sem `!!`.
        return clubRepo.updateTicketPrice(clubId, ticketPriceCents)
            ?.let { TicketPriceResult.Success(it) }
            ?: TicketPriceResult.ClubNotFound(clubId)
    }
}
