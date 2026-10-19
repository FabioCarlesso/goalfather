package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.CreateOfferRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.TransferOfferDto
import com.carlesso.goalfather.adapter.`in`.web.dto.TransferOfferResultDto
import com.carlesso.goalfather.adapter.`in`.web.dto.toDto
import com.carlesso.goalfather.application.port.`in`.TransferOfferUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.OfferId
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.result.OfferResult
import kotlinx.serialization.json.Json
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Propostas de transferência entre técnicos humanos (issue #63). Como o
 * mercado, os erros de NEGÓCIO voltam no 200 (`TransferOfferResult`, com o
 * `type` discriminador); só posse e existência viram status HTTP.
 */
@RestController
class OfferController(
    private val offers: TransferOfferUseCase,
    private val clubRepo: ClubRepository,
    // `TransferOfferResultDto` é sealed: sai pelo kotlinx (`okJson`) para levar
    // o `type` do contrato — com `ResponseEntity<Any>` o Spring usaria o Jackson.
    private val json: Json,
) {

    /**
     * Propostas recebidas e enviadas pelo clube — o que a Dashboard consulta
     * (com polling) para avisar o técnico. Só o dono lê: as propostas de um
     * clube são negociação em andamento, não informação pública da liga.
     */
    @GetMapping("/api/clubs/{id}/offers")
    suspend fun listOffers(
        @PathVariable id: Long,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> {
        val club = clubRepo.findById(ClubId(id)) ?: return clubNotFound(ClubId(id))
        if (club.ownerId != userId) return forbidden()
        return json.okJson<List<TransferOfferDto>>(offers.offersOf(club.id).map { it.toDto() })
    }

    @PostMapping("/api/offers")
    suspend fun create(
        @RequestBody req: CreateOfferRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> = respond(
        offers.create(
            buyerClubId = ClubId(req.buyerClubId),
            sellerClubId = ClubId(req.sellerClubId),
            playerId = PlayerId(req.playerId),
            amount = req.amount,
            requesterId = userId,
        ),
    )

    @PostMapping("/api/offers/{id}/accept")
    suspend fun accept(@PathVariable id: Long, @AuthenticationPrincipal userId: Long): ResponseEntity<Any> =
        respond(offers.accept(OfferId(id), userId))

    @PostMapping("/api/offers/{id}/reject")
    suspend fun reject(@PathVariable id: Long, @AuthenticationPrincipal userId: Long): ResponseEntity<Any> =
        respond(offers.reject(OfferId(id), userId))

    @PostMapping("/api/offers/{id}/withdraw")
    suspend fun withdraw(@PathVariable id: Long, @AuthenticationPrincipal userId: Long): ResponseEntity<Any> =
        respond(offers.withdraw(OfferId(id), userId))

    /**
     * `when` exaustivo sem `else`: `is OfferResult.Rejection` cobre de uma vez
     * todos os erros de negócio, e o compilador sabe disso porque `Rejection`
     * é ela mesma `sealed`. O tipo explícito em `okJson<TransferOfferResultDto>`
     * escolhe o serializer POLIMÓRFICO — o da subclasse `Success` não escreveria
     * o `type`.
     */
    private fun respond(result: OfferResult): ResponseEntity<Any> = when (result) {
        is OfferResult.Success -> json.okJson<TransferOfferResultDto>(TransferOfferResultDto.Success(result.offer.toDto()))
        is OfferResult.Rejection -> json.okJson(result.toDto())
        is OfferResult.ClubNotFound -> clubNotFound(result.clubId)
        is OfferResult.OfferNotFound -> ResponseEntity.status(404).body(
            ErrorResponse(code = "OFFER_NOT_FOUND", message = "Proposta ${result.offerId.value} não encontrada"),
        )
        OfferResult.NotOwner -> forbidden()
    }
}

private fun clubNotFound(clubId: ClubId): ResponseEntity<Any> =
    ResponseEntity.status(404).body(
        ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube ${clubId.value} não encontrado"),
    )

private fun forbidden(): ResponseEntity<Any> =
    ResponseEntity.status(403).body(
        ErrorResponse(code = "FORBIDDEN", message = "Você não é dono do clube desta operação"),
    )
