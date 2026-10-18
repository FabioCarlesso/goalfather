package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.SellPlayerUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.TransferResult

/**
 * Implementação de `SellPlayerUseCase`. O preço de venda é função do
 * `Player` — injetada como parâmetro para que testes consigam fixar
 * resultados, e regras futuras (idade, lesão, mercado em alta) possam
 * trocar o cálculo sem mudar o use case.
 */
class SellPlayerService(
    private val clubRepo: ClubRepository,
    private val marketRepo: MarketRepository,
    private val salePriceCalc: (Player) -> Long = ::defaultSalePrice,
) : SellPlayerUseCase {

    override suspend fun execute(clubId: ClubId, playerId: PlayerId): TransferResult {
        val club = clubRepo.findById(clubId)
            ?: return TransferResult.PlayerNotAvailable(playerId)
        val player = club.squad.firstOrNull { it.id == playerId }
            ?: return TransferResult.PlayerNotAvailable(playerId)

        val salePrice = salePriceCalc(player)
        val updatedClub = club.copy(
            cash = club.cash + salePrice,
            squad = club.squad.filterNot { it.id == playerId },
        )
        // Clube ANTES do mercado: se o `save` perder o lock otimista (issue #89),
        // nada foi gravado e o jogador ainda está no elenco — basta não anunciá-lo.
        // Na ordem inversa, um conflito deixaria o jogador no mercado E no clube.
        return when (val saved = clubRepo.save(updatedClub)) {
            is ClubSaveResult.Saved -> {
                marketRepo.add(MarketEntry(player = player, price = salePrice))
                TransferResult.Success(club = saved.club, player = player)
            }
            is ClubSaveResult.Conflict -> TransferResult.Conflict(saved.clubId)
        }
    }
}

/**
 * Default: R$ 100k × overall (em centavos). Mesmo cálculo que o mock
 * MSW do frontend usava — vira regra única quando o backend assumir.
 */
fun defaultSalePrice(player: Player): Long = player.overall * 100_000_00L
