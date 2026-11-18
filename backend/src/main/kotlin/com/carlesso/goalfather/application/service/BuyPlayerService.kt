package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.BuyPlayerUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.TransferResult
import com.carlesso.goalfather.domain.rules.ContractResult
import com.carlesso.goalfather.domain.rules.DEFAULT_SIGNING_SEASONS
import com.carlesso.goalfather.domain.rules.MAX_SQUAD_SIZE
import com.carlesso.goalfather.domain.rules.NEUTRAL_LEAGUE_STRENGTH
import com.carlesso.goalfather.domain.rules.allowedContractSeasons
import com.carlesso.goalfather.domain.rules.contractSpan
import com.carlesso.goalfather.domain.rules.pricedFor
import com.carlesso.goalfather.domain.rules.seasonOn
import com.carlesso.goalfather.domain.rules.signContract
import com.carlesso.goalfather.domain.rules.signed
import com.carlesso.goalfather.domain.rules.worldSeasons
import java.time.LocalDate

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
    // A data do jogo (issue #136): o contrato assinado na compra começa nela e
    // conta a temporada corrente da liga do clube como a primeira.
    private val leagueRepo: LeagueRepository,
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

        // Livre do mercado → contratado (issue #136): contrato padrão de
        // [DEFAULT_SIGNING_SEASONS] temporadas, salário derivado do valor. Os
        // termos negociados com o jogador são a #138.
        val signed = signFromMarket(entry.player, club)
        val updatedClub = club.copy(
            cash = club.cash - entry.price,
            squad = club.squad + signed,
        )

        // O clube pode ter mudado desde a leitura (issue #89) — ex.: a virada da
        // rodada creditou bilheteria. Aí o `save` não grava nada, mas o jogador
        // JÁ saiu do mercado no claim acima: devolvê-lo com o mesmo preço é a
        // compensação que impede o jogador de sumir (fora do mercado e fora de
        // qualquer elenco). Sem retry automático: o débito é sobre o caixa que
        // o técnico viu, então a decisão de tentar de novo volta para ele.
        return when (val saved = clubRepo.save(updatedClub)) {
            is ClubSaveResult.Saved -> TransferResult.Success(club = saved.club, player = signed)
            is ClubSaveResult.Conflict -> {
                marketRepo.add(MarketEntry(player = entry.player, price = entry.price))
                TransferResult.Conflict(saved.clubId)
            }
        }
    }

    /**
     * O contrato da compra. A temporada corrente do país do clube sai das
     * tabelas (o Brasil de 2027 convive com a Europa de 2026/27); clube sem
     * liga cai na temporada da data na janela dele. Sem rodada nenhuma (save
     * vazio), a data é a de hoje do sistema — só para não travar a compra.
     */
    private suspend fun signFromMarket(player: Player, club: Club): Player {
        val today = leagueRepo.findLatest()?.matchday?.date ?: LocalDate.now()
        val span = club.contractSpan
        val season = leagueRepo.currentStandings().worldSeasons()[club.country.code]
            ?.takeIf { !span.end(it).isBefore(today) }
            ?: span.seasonOn(today)
        val seasons = DEFAULT_SIGNING_SEASONS.coerceIn(allowedContractSeasons(player.age))
        return when (val result = signContract(player, club.id, Money.ZERO, today, season, span, seasons)) {
            is ContractResult.Signed -> player.signed(result.contract.pricedFor(player, NEUTRAL_LEAGUE_STRENGTH, today))
            // A duração foi cortada na faixa da idade logo acima: não acontece.
            is ContractResult.Rejected -> error("contrato padrão recusado para ${player.id}: ${result.reason}")
        }
    }

    companion object {
        // Mesmo teto das propostas entre técnicos (issue #63): uma fonte só.
        const val DEFAULT_MAX_SQUAD: Int = MAX_SQUAD_SIZE
    }
}
