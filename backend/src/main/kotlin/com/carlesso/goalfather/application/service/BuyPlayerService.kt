package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.BuyPlayerUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.MarketEntry
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

        // Claim é o passo decisivo e disputado (issue #21): removê-lo do mercado
        // sob lock otimista. Fazemos ANTES de mexer no clube, então o perdedor
        // da corrida nunca debita caixa nem ganha o jogador — só o vencedor
        // segue para o `save`. O outro recebe `PlayerNotAvailable` graciosamente.
        if (!marketRepo.claim(playerId)) {
            return TransferResult.PlayerNotAvailable(playerId)
        }

        val updatedClub = club.copy(
            cash = club.cash - entry.price,
            squad = club.squad + entry.player,
        )

        // O clube pode ter mudado desde a leitura (issue #89) — ex.: a virada da
        // rodada creditou bilheteria. Aí o `save` não grava nada, mas o jogador
        // JÁ saiu do mercado no claim acima: devolvê-lo com o mesmo preço é a
        // compensação que impede o jogador de sumir (fora do mercado e fora de
        // qualquer elenco). Sem retry automático: o débito é sobre o caixa que
        // o técnico viu, então a decisão de tentar de novo volta para ele.
        return when (val saved = clubRepo.save(updatedClub)) {
            is ClubSaveResult.Saved -> TransferResult.Success(club = saved.club, player = entry.player)
            is ClubSaveResult.Conflict -> {
                marketRepo.add(MarketEntry(player = entry.player, price = entry.price))
                TransferResult.Conflict(saved.clubId)
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_SQUAD: Int = 25
    }
}
