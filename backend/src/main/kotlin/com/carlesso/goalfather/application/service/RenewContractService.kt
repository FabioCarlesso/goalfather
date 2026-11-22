package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.`in`.RenewContractUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.application.port.out.ContractTalksRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.ContractOffer
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.RenewalResult
import com.carlesso.goalfather.domain.rules.PlayerResponse
import com.carlesso.goalfather.domain.rules.isCoolingDown
import com.carlesso.goalfather.domain.rules.leagueStrengthOf
import com.carlesso.goalfather.domain.rules.playerResponse
import com.carlesso.goalfather.domain.rules.renewalCooldown
import com.carlesso.goalfather.domain.rules.stature

/**
 * Implementação de [RenewContractUseCase] (issue #138). A regra — o que o
 * jogador aceita e quando esfria — é do domínio (`playerResponse`,
 * `renewalCooldown`); aqui só se orquestra leitura, data do jogo e gravação.
 *
 * Renovar é trocar o contrato do jogador DENTRO do elenco: o clube é regravado
 * sob o lock otimista (issue #89) e, no conflito, a tentativa é refeita do
 * zero — relê clube e esfriamento e pergunta de novo ao jogador, como a
 * escalação. A resposta é determinística, então repetir não muda o "sim".
 */
class RenewContractService(
    private val clubRepo: ClubRepository,
    private val leagueRepo: LeagueRepository,
    private val catalogRepo: CompetitionCatalogRepository,
    private val talks: ContractTalksRepository,
) : RenewContractUseCase {

    override suspend fun renew(clubId: ClubId, playerId: PlayerId, offer: ContractOffer): RenewalResult =
        retryOnConflict(isConflict = { it is RenewalResult.Conflict }) {
            attempt(clubId, playerId, offer)
        }

    private suspend fun attempt(clubId: ClubId, playerId: PlayerId, offer: ContractOffer): RenewalResult {
        val club = clubRepo.findById(clubId) ?: return RenewalResult.PlayerNotInSquad(playerId)
        val player = club.squad.firstOrNull { it.id == playerId } ?: return RenewalResult.PlayerNotInSquad(playerId)
        val today = leagueRepo.today()

        talks.cooldownUntil(playerId, clubId)
            ?.takeIf { isCoolingDown(it, today) }
            ?.let { return RenewalResult.TalksCoolingDown(playerId, it) }

        // Renovação: o clube que oferece é o atual — sem degrau, e o piso é o
        // salário de hoje (ver `salaryDemand`).
        val stature = club.stature(catalogRepo.findAll().leagueStrengthOf(club))
        when (val response = playerResponse(player, offer, buyer = stature, current = stature)) {
            PlayerResponse.Accepts -> Unit
            is PlayerResponse.Refuses -> {
                val until = renewalCooldown(offer, response, player.salary, today)
                until?.let { talks.startCooldown(playerId, clubId, it) }
                return RenewalResult.PlayerRefused(playerId, response, cooldownUntil = until)
            }
        }

        // O contrato novo SUBSTITUI o antigo e a duração recomeça hoje.
        val renewed = leagueRepo.signAccepted(player, club, offer, today)
        val updated = club.copy(squad = club.squad.map { if (it.id == playerId) renewed else it })
        return when (val saved = clubRepo.save(updated)) {
            is ClubSaveResult.Saved -> RenewalResult.Renewed(saved.club, renewed)
            is ClubSaveResult.Conflict -> RenewalResult.Conflict(saved.clubId)
        }
    }
}
