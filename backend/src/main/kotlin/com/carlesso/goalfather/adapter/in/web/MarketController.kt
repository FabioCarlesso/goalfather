package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.BuyPlayerRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.SellPlayerRequest
import com.carlesso.goalfather.application.metrics.GoalfatherMetrics
import com.carlesso.goalfather.application.port.`in`.BuyPlayerUseCase
import com.carlesso.goalfather.application.port.`in`.SellPlayerUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.result.TransferResult
import io.micrometer.core.instrument.MeterRegistry
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
    private val meterRegistry: MeterRegistry,
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
        val result = buyUseCase.execute(ClubId(req.clubId), PlayerId(req.playerId))
        // Conta a compra dimensionada pelo desfecho (issue #44). `conflict` cobre
        // o `PlayerNotAvailable` — na prática, a perda do lock otimista do mercado
        // (#21) quando dois donos disputam o mesmo jogador.
        countTransfer(result)
        return ResponseEntity.ok(result)
    }

    private fun countTransfer(result: TransferResult) {
        val outcome = when (result) {
            is TransferResult.Success -> "success"
            is TransferResult.InsufficientFunds -> "insufficient_funds"
            is TransferResult.SquadFull -> "squad_full"
            is TransferResult.PlayerNotAvailable -> "conflict"
        }
        meterRegistry.counter(GoalfatherMetrics.MARKET_TRANSFERS, GoalfatherMetrics.TAG_RESULT, outcome)
            .increment()
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
