package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.BuyPlayerRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.SellPlayerRequest
import com.carlesso.goalfather.application.port.`in`.BuyPlayerUseCase
import com.carlesso.goalfather.application.port.`in`.SellPlayerUseCase
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.result.TransferResult
import kotlinx.coroutines.runBlocking
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
    private val buyUseCase: BuyPlayerUseCase,
    private val sellUseCase: SellPlayerUseCase,
) {

    @GetMapping
    fun listMarket(
        @RequestParam(required = false) position: Position?,
        @RequestParam(required = false) maxPrice: Long?,
    ) = runBlocking { marketRepo.findAll(position, maxPrice) }

    @PostMapping("/buy")
    fun buy(@RequestBody req: BuyPlayerRequest): TransferResult = runBlocking {
        buyUseCase.execute(ClubId(req.clubId), PlayerId(req.playerId))
    }

    @PostMapping("/sell")
    fun sell(@RequestBody req: SellPlayerRequest): TransferResult = runBlocking {
        sellUseCase.execute(ClubId(req.clubId), PlayerId(req.playerId))
    }
}
