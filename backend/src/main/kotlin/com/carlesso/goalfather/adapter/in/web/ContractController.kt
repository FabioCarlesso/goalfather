package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.ContractOfferDto
import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.INVALID_CONTRACT_OFFER
import com.carlesso.goalfather.adapter.`in`.web.dto.RenewalResultDto
import com.carlesso.goalfather.adapter.`in`.web.dto.toDto
import com.carlesso.goalfather.adapter.`in`.web.dto.toRenewalDto
import com.carlesso.goalfather.application.port.`in`.RenewContractUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.service.today
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.RenewalResult
import com.carlesso.goalfather.domain.rules.squadMarketValue
import kotlinx.serialization.json.Json
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * Renovação de contrato (issue #138). Como no mercado, o desfecho de NEGÓCIO
 * volta no 200 (`RenewalResult`, com o `type` discriminador); só posse,
 * existência e termos malformados viram status HTTP.
 */
@RestController
class ContractController(
    private val renewals: RenewContractUseCase,
    private val clubRepo: ClubRepository,
    // O jogador renovado sai com o valor no clube e o "último ano?" — que
    // precisam do catálogo (força da liga) e da data do jogo.
    private val catalog: CompetitionCatalogRepository,
    private val leagueRepo: LeagueRepository,
    private val json: Json,
) {

    @PostMapping("/api/clubs/{id}/players/{playerId}/renewal")
    suspend fun renew(
        @PathVariable id: Long,
        @PathVariable playerId: Long,
        @RequestBody req: ContractOfferDto,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> {
        val club = clubRepo.findById(ClubId(id)) ?: return ResponseEntity.status(404).body(
            ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $id não encontrado"),
        )
        if (club.ownerId != userId) {
            return ResponseEntity.status(403).body(ErrorResponse(code = "FORBIDDEN", message = "Você não é dono deste clube"))
        }
        val offer = req.toDomain() ?: return ResponseEntity.badRequest().body(INVALID_CONTRACT_OFFER)
        val result = renewals.renew(club.id, PlayerId(playerId), offer)
        // Tipo explícito: escolhe o serializer POLIMÓRFICO, que escreve o `type`.
        return json.okJson<RenewalResultDto>(result.toDto(leagueRepo.today()))
    }

    private suspend fun RenewalResult.toDto(today: LocalDate): RenewalResultDto = when (this) {
        is RenewalResult.Renewed -> {
            val competitions = catalog.findAll()
            RenewalResultDto.Renewed(
                player.toDto(club, competitions, squadMarketValue(player, club, competitions, today), today),
            )
        }
        is RenewalResult.PlayerRefused -> refusal.toRenewalDto(playerId.value, cooldownUntil?.toString())
        is RenewalResult.TalksCoolingDown -> RenewalResultDto.TalksCoolingDown(playerId.value, until.toString())
        is RenewalResult.PlayerNotInSquad -> RenewalResultDto.PlayerNotAvailable(playerId.value)
        is RenewalResult.Conflict -> RenewalResultDto.Conflict(clubId.value)
    }
}
