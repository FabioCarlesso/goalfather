package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.BuyPlayerUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.ContractOffer
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.TransferResult
import com.carlesso.goalfather.domain.rules.MAX_SQUAD_SIZE
import com.carlesso.goalfather.domain.rules.PlayerResponse
import com.carlesso.goalfather.domain.rules.leagueStrengthOf
import com.carlesso.goalfather.domain.rules.playerResponse
import com.carlesso.goalfather.domain.rules.stature

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
    // Força da liga do comprador (issues #135/#138): a exigência salarial sai
    // do valor do jogador NA liga em que ele vai jogar.
    private val catalogRepo: CompetitionCatalogRepository,
    private val squadMaxSize: Int = DEFAULT_MAX_SQUAD,
    // Janela de transferência do comprador (issue #137).
    private val windows: TransferWindowGuard = TransferWindowGuard.UNRESTRICTED,
) : BuyPlayerUseCase {

    override suspend fun execute(clubId: ClubId, playerId: PlayerId, offer: ContractOffer): TransferResult {
        val club = clubRepo.findById(clubId)
            ?: return TransferResult.PlayerNotAvailable(playerId)
        val entry = marketRepo.findEntry(playerId)
            ?: return TransferResult.PlayerNotAvailable(playerId)
        // Janela e limite de registros (issue #137). Do mercado não há clube de
        // origem (`origin = null`): a janela só doméstica também aceita.
        windows.registrationBlocker(club, origin = null, playerId = playerId)
            ?.let { return it.toTransferResult(playerId) }

        if (club.cash < entry.price) {
            return TransferResult.InsufficientFunds(
                available = club.cash,
                required = entry.price,
            )
        }
        if (club.squad.size >= squadMaxSize) {
            return TransferResult.SquadFull(maxSize = squadMaxSize)
        }

        // Termos pessoais (issue #138): o livre só assina se aceitar o salário
        // e a duração. Do mercado ele não tem clube atual (`current = null`),
        // então não há degrau para baixo. Perguntado ANTES do claim: recusa não
        // tira ninguém do mercado.
        val strength = catalogRepo.findAll().leagueStrengthOf(club)
        when (val response = playerResponse(entry.player, offer, club.stature(strength), current = null)) {
            PlayerResponse.Accepts -> Unit
            is PlayerResponse.Refuses -> return TransferResult.PlayerRefused(playerId, response)
        }

        // Claim é o passo decisivo e disputado (issue #21): removê-lo do mercado
        // sob lock otimista. Fazemos ANTES de mexer no clube, então o perdedor
        // da corrida nunca debita caixa nem ganha o jogador — só o vencedor
        // segue para o `save`. O outro recebe `PlayerNotAvailable` graciosamente.
        if (!marketRepo.claim(playerId)) {
            return TransferResult.PlayerNotAvailable(playerId)
        }

        // Livre do mercado → contratado com os termos que ele aceitou (#138).
        val signed = leagueRepo.signAccepted(entry.player, club, offer, leagueRepo.today())
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
            is ClubSaveResult.Saved -> {
                windows.recordRegistration(entry.player.id, club.id)
                TransferResult.Success(club = saved.club, player = signed)
            }
            is ClubSaveResult.Conflict -> {
                marketRepo.add(MarketEntry(player = entry.player, price = entry.price))
                TransferResult.Conflict(saved.clubId)
            }
        }
    }

    companion object {
        // Mesmo teto das propostas entre técnicos (issue #63): uma fonte só.
        const val DEFAULT_MAX_SQUAD: Int = MAX_SQUAD_SIZE
    }
}
