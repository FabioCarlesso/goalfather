package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.BuyPlayerUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.TransferResult

/**
 * Implementação de `BuyPlayerUseCase`. Orquestra dois ports
 * (Club + Market) e retorna um `TransferResult` sealed — sem
 * exceptions para fluxo de controle.
 *
 * Cada falha de negócio (caixa, elenco cheio, jogador indisponível)
 * vira uma variante. O caller usa `when (result) is ...` exaustivo;
 * o compilador garante cobertura.
 */
class BuyPlayerService(
    private val clubRepo: ClubRepository,
    private val marketRepo: MarketRepository,
    private val squadMaxSize: Int = DEFAULT_MAX_SQUAD,
) : BuyPlayerUseCase {

    override suspend fun execute(clubId: ClubId, playerId: PlayerId): TransferResult {
        val club = clubRepo.findById(clubId)
            ?: return TransferResult.PlayerNotAvailable(playerId)
        val entry = marketRepo.findEntry(playerId)
            ?: return TransferResult.PlayerNotAvailable(playerId)

        if (club.cash < entry.price) {
            return TransferResult.InsufficientFunds(
                available = club.cash,
                required = entry.price,
            )
        }
        if (club.squad.size >= squadMaxSize) {
            return TransferResult.SquadFull(maxSize = squadMaxSize)
        }

        val updatedClub = club.copy(
            cash = club.cash - entry.price,
            squad = club.squad + entry.player,
        )
        clubRepo.save(updatedClub)
        marketRepo.remove(playerId)

        return TransferResult.Success(club = updatedClub, player = entry.player)
    }

    companion object {
        const val DEFAULT_MAX_SQUAD: Int = 25
    }
}
