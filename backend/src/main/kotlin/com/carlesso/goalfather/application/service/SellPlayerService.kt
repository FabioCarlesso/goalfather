package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.SellPlayerUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
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
        clubRepo.save(updatedClub)
        marketRepo.add(MarketEntry(player = player, price = salePrice))

        return TransferResult.Success(club = updatedClub, player = player)
    }
}

/**
 * Default: R$ 100k × overall (em centavos). Mesmo cálculo que o mock
 * MSW do frontend usava — vira regra única quando o backend assumir.
 */
fun defaultSalePrice(player: Player): Long = player.overall * 100_000_00L
