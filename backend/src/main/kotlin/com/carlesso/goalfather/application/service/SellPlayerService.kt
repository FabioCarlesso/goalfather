package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.SellPlayerUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.TransferResult
import com.carlesso.goalfather.domain.rules.marketListingPrice
import com.carlesso.goalfather.domain.rules.released
import com.carlesso.goalfather.domain.rules.salePayout

/**
 * Implementação de `SellPlayerUseCase`. Os dois preços — o que o clube recebe
 * e o do anúncio no mercado — são funções do `Player`, injetadas como
 * parâmetro para que testes consigam fixar resultados. Os defaults vêm de
 * `domain/rules/TransferRules`, onde o pagamento é sempre menor que o anúncio:
 * comprar e revender o mesmo jogador não gera dinheiro.
 */
class SellPlayerService(
    private val clubRepo: ClubRepository,
    private val marketRepo: MarketRepository,
    private val salePriceCalc: (Player) -> Money = ::salePayout,
    private val listingPriceCalc: (Player) -> Money = ::marketListingPrice,
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
                // O preço sai do jogador AINDA com contrato; anunciado, ele é
                // livre (issue #136) — o mercado não tem clube nem paga salário.
                val listed = player.released()
                marketRepo.add(MarketEntry(player = listed, price = listingPriceCalc(player)))
                TransferResult.Success(club = saved.club, player = listed)
            }
            is ClubSaveResult.Conflict -> TransferResult.Conflict(saved.clubId)
        }
    }
}
