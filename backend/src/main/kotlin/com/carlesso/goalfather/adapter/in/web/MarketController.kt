package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.BuyPlayerRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.SellPlayerRequest
import com.carlesso.goalfather.application.port.`in`.BuyPlayerUseCase
import com.carlesso.goalfather.application.port.`in`.SellPlayerUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/market")
class MarketController(
    private val marketRepo: MarketRepository,
    private val clubRepo: ClubRepository,
    private val buyUseCase: BuyPlayerUseCase,
    private val sellUseCase: SellPlayerUseCase,
) {

    @GetMapping
    suspend fun listMarket(
        @RequestParam(required = false) position: Position?,
        @RequestParam(required = false) maxPrice: Long?,
    ) = marketRepo.findAll(position, maxPrice)

    @PostMapping("/buy")
    suspend fun buy(
        @RequestBody req: BuyPlayerRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> {
        ownershipError(req.clubId, userId)?.let { return it }
        return ResponseEntity.ok(buyUseCase.execute(ClubId(req.clubId), PlayerId(req.playerId)))
    }

    @PostMapping("/sell")
    suspend fun sell(
        @RequestBody req: SellPlayerRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> {
        ownershipError(req.clubId, userId)?.let { return it }
        return ResponseEntity.ok(sellUseCase.execute(ClubId(req.clubId), PlayerId(req.playerId)))
    }

    /** 403 se o usuário não é dono do clube alvo da operação (issue #18). */
    private suspend fun ownershipError(clubId: Long, userId: Long): ResponseEntity<Any>? {
        val club = clubRepo.findById(ClubId(clubId)) ?: return ResponseEntity.status(404).body(
            ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $clubId não encontrado"),
        )
        return if (club.ownerId != userId) {
            ResponseEntity.status(403).body(
                ErrorResponse(code = "FORBIDDEN", message = "Você não é dono deste clube"),
            )
        } else {
            null
        }
    }
}
