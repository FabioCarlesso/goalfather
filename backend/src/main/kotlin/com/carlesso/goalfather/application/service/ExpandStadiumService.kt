package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.ExpandStadiumUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.result.ExpandStadiumResult
import com.carlesso.goalfather.domain.rules.MIN_STADIUM_EXPANSION_SEATS
import com.carlesso.goalfather.domain.rules.isStadiumExpansionAllowed
import com.carlesso.goalfather.domain.rules.stadiumExpansionCost
import com.carlesso.goalfather.domain.rules.withExpandedStadium

/**
 * Implementação de `ExpandStadiumUseCase` (issue #79). Só orquestra: custo e
 * piso vivem em `domain/rules/StadiumRules`.
 *
 * Ordem das checagens: dono → mínimo → caixa. Posse primeiro pelo mesmo motivo
 * de `SetTicketPriceService` (nada de responder regra sobre clube alheio);
 * mínimo antes do caixa porque um pedido negativo tem "custo" negativo e
 * passaria pela checagem de caixa — era exatamente o exploit da issue.
 */
class ExpandStadiumService(
    private val clubRepo: ClubRepository,
) : ExpandStadiumUseCase {

    override suspend fun execute(
        clubId: ClubId,
        requesterId: Long,
        additionalSeats: Int,
    ): ExpandStadiumResult {
        val club = clubRepo.findById(clubId)
            ?: return ExpandStadiumResult.ClubNotFound(clubId)

        if (club.ownerId != requesterId) {
            return ExpandStadiumResult.NotOwner(clubId)
        }

        if (!isStadiumExpansionAllowed(additionalSeats)) {
            return ExpandStadiumResult.BelowMinimumSeats(
                requested = additionalSeats,
                minimum = MIN_STADIUM_EXPANSION_SEATS,
            )
        }

        val cost = stadiumExpansionCost(additionalSeats)
        if (club.cash < cost) {
            return ExpandStadiumResult.InsufficientFunds(available = club.cash, required = cost)
        }

        // Sem retry automático (issue #89): ampliar tem efeito ACUMULADO sobre o
        // caixa que o técnico viu na tela. Se a rodada mexeu no clube no meio do
        // caminho, devolvemos o conflito para ele decidir com o caixa novo.
        return when (val saved = clubRepo.save(club.withExpandedStadium(additionalSeats))) {
            is ClubSaveResult.Saved -> ExpandStadiumResult.Success(saved.club)
            is ClubSaveResult.Conflict -> ExpandStadiumResult.Conflict(saved.clubId)
        }
    }
}
