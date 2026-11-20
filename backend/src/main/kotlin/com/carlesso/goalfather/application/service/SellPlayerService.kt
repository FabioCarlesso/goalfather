package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.SellPlayerUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.TransferResult
import com.carlesso.goalfather.domain.rules.marketListingPrice
import com.carlesso.goalfather.domain.rules.marketReferenceValue
import com.carlesso.goalfather.domain.rules.released
import com.carlesso.goalfather.domain.rules.salePayout
import com.carlesso.goalfather.domain.rules.squadMarketValue

/**
 * Implementação de `SellPlayerUseCase`. Os dois preços saem do valor de
 * mercado (issue #135): o pagamento, do valor do jogador NO CLUBE (contrato e
 * liga dele, ver `squadMarketValue`); o anúncio, do valor de referência do
 * mercado. As funções de preço continuam injetáveis para os testes fixarem
 * resultados; os defaults vêm de `domain/rules/TransferRules`, onde o
 * pagamento é sempre menor que o anúncio — comprar e revender o mesmo jogador
 * não gera dinheiro.
 */
class SellPlayerService(
    private val clubRepo: ClubRepository,
    private val marketRepo: MarketRepository,
    // Catálogo (força da liga) e encaixe corrente (o "hoje" do contrato).
    private val catalogRepo: CompetitionCatalogRepository,
    private val leagueRepo: LeagueRepository,
    private val salePriceCalc: (Money) -> Money = ::salePayout,
    private val listingPriceCalc: (Money) -> Money = ::marketListingPrice,
    // Venda rápida só dentro da janela do vendedor (issue #137).
    private val windows: TransferWindowGuard = TransferWindowGuard.UNRESTRICTED,
) : SellPlayerUseCase {

    override suspend fun execute(clubId: ClubId, playerId: PlayerId): TransferResult {
        val club = clubRepo.findById(clubId)
            ?: return TransferResult.PlayerNotAvailable(playerId)
        val player = club.squad.firstOrNull { it.id == playerId }
            ?: return TransferResult.PlayerNotAvailable(playerId)
        windows.saleBlocker(club)?.let { return it.toTransferResult(playerId) }

        val salePrice = salePriceCalc(squadMarketValue(player, club, catalogRepo.findAll(), leagueRepo.today()))
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
                // O anúncio usa o valor de REFERÊNCIA (#135), que não depende
                // do contrato: vender e recomprar continua dando prejuízo.
                val listed = player.released()
                marketRepo.add(MarketEntry(player = listed, price = listingPriceCalc(marketReferenceValue(player))))
                TransferResult.Success(club = saved.club, player = listed)
            }
            is ClubSaveResult.Conflict -> TransferResult.Conflict(saved.clubId)
        }
    }
}
