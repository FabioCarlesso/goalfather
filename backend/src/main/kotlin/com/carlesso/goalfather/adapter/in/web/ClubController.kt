package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AvailableClubDto
import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.ExpandStadiumRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.LineupRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.TicketPriceRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.TrainingFocusRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.toAvailableDto
import com.carlesso.goalfather.adapter.`in`.web.dto.toDto
import com.carlesso.goalfather.application.port.`in`.ClaimClubUseCase
import com.carlesso.goalfather.application.port.`in`.ExpandStadiumUseCase
import com.carlesso.goalfather.application.port.`in`.SaveLineupUseCase
import com.carlesso.goalfather.application.port.`in`.SetTicketPriceUseCase
import com.carlesso.goalfather.application.port.`in`.SetTrainingFocusUseCase
import com.carlesso.goalfather.application.port.`in`.TreatSquadUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Tactics
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.ClaimResult
import com.carlesso.goalfather.domain.result.ExpandStadiumResult
import com.carlesso.goalfather.domain.result.LineupResult
import com.carlesso.goalfather.domain.result.MedicalResult
import com.carlesso.goalfather.domain.result.TicketPriceResult
import com.carlesso.goalfather.domain.result.TrainingFocusResult
import kotlinx.serialization.json.Json
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/clubs")
class ClubController(
    private val clubRepo: ClubRepository,
    private val saveLineup: SaveLineupUseCase,
    private val claimClub: ClaimClubUseCase,
    private val treatSquad: TreatSquadUseCase,
    private val setTrainingFocus: SetTrainingFocusUseCase,
    private val setTicketPrice: SetTicketPriceUseCase,
    private val expandStadium: ExpandStadiumUseCase,
    // `ClubDto` carrega o `availability` (sealed) de cada jogador: as respostas
    // de sucesso saem pelo kotlinx via `okJson` para manter o `type` do
    // contrato — `ResponseEntity<Any>` sozinho cairia no Jackson.
    private val json: Json,
) {

    /**
     * Clubes sem dono — fluxo de seleção (issue #19). Mapeado em `/available`
     * (literal): o Spring casa antes de `/{id}`, então não há ambiguidade.
     */
    @GetMapping("/available")
    suspend fun listAvailable(): List<AvailableClubDto> =
        clubRepo.findAvailable().map { it.toAvailableDto() }

    @GetMapping("/{id}")
    suspend fun getClub(@PathVariable id: Long): ResponseEntity<Any> {
        val club = clubRepo.findById(ClubId(id))
        return if (club == null) {
            ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $id não encontrado"),
            )
        } else {
            json.okJson(club.toDto())
        }
    }

    /** Reivindicar clube para o usuário autenticado (issue #19). */
    @PostMapping("/{id}/claim")
    suspend fun claim(
        @PathVariable id: Long,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> =
        when (val result = claimClub.execute(ClubId(id), UserId(userId))) {
            is ClaimResult.Success -> ResponseEntity.ok(result.user.toDto())
            is ClaimResult.ClubNotFound -> ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $id não encontrado"),
            )
            is ClaimResult.AlreadyClaimed -> ResponseEntity.status(409).body(
                ErrorResponse(code = "CLUB_ALREADY_CLAIMED", message = "Clube $id já foi reivindicado"),
            )
            // Principal do JWT não mapeia um usuário real (issue #29) → 401,
            // mesmo tratamento de `/me` em AuthController.
            is ClaimResult.UserNotFound -> ResponseEntity.status(401).body(
                ErrorResponse(code = "UNAUTHORIZED", message = "Usuário não encontrado"),
            )
        }

    @PostMapping("/{id}/lineup")
    suspend fun saveLineup(
        @PathVariable id: Long,
        @RequestBody req: LineupRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> =
        // Posse é verificada dentro do use case (fetch único) — issue #18/#19.
        when (
            val result =
                saveLineup.execute(ClubId(id), userId, req.formation, req.playerIds, Tactics(req.posture))
        ) {
            is LineupResult.Success -> ResponseEntity.noContent().build()
            is LineupResult.ClubNotFound -> ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube ${result.clubId.value} não encontrado"),
            )
            is LineupResult.NotOwner -> ResponseEntity.status(403).body(
                ErrorResponse(code = "FORBIDDEN", message = "Você não é dono deste clube"),
            )
            is LineupResult.IncompleteLineup -> ResponseEntity.badRequest().body(
                ErrorResponse(
                    code = "INCOMPLETE_LINEUP",
                    message = "Escalação esperava ${result.expected}, recebeu ${result.actual}",
                ),
            )
            is LineupResult.PlayersNotInSquad -> ResponseEntity.badRequest().body(
                ErrorResponse(
                    code = "INVALID_LINEUP",
                    message = "Jogadores fora do elenco: ${result.missingIds}",
                ),
            )
            is LineupResult.InjuredPlayers -> ResponseEntity.badRequest().body(
                ErrorResponse(
                    code = "INJURED_PLAYERS",
                    message = "Jogadores lesionados não podem ser escalados: ${result.playerIds}",
                    details = playerIdsDetail(result.playerIds),
                ),
            )
            is LineupResult.DuplicatePlayers -> ResponseEntity.badRequest().body(
                ErrorResponse(
                    code = "DUPLICATE_PLAYERS",
                    message = "Jogadores repetidos na escalação: ${result.playerIds}",
                    details = playerIdsDetail(result.playerIds),
                ),
            )
            is LineupResult.Conflict -> clubWriteConflict(result.clubId)
        }

    /**
     * Departamento médico (issue #54): debita a taxa fixa e acelera a
     * recuperação do elenco (stamina + rodadas de lesão).
     */
    @PostMapping("/{id}/medical")
    suspend fun treatSquad(
        @PathVariable id: Long,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> =
        when (val result = treatSquad.execute(ClubId(id), userId)) {
            is MedicalResult.Success -> json.okJson(result.club.toDto())
            is MedicalResult.ClubNotFound -> ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $id não encontrado"),
            )
            is MedicalResult.NotOwner -> ResponseEntity.status(403).body(
                ErrorResponse(code = "FORBIDDEN", message = "Você não é dono deste clube"),
            )
            // 402 é o mesmo status já usado pela ampliação de estádio para
            // "caixa não cobre" — mantém a UI com um só caminho de erro.
            is MedicalResult.InsufficientFunds -> ResponseEntity.status(402).body(
                ErrorResponse(
                    code = "INSUFFICIENT_FUNDS",
                    message = "Caixa insuficiente para o departamento médico " +
                        "(necessário ${result.required}, disponível ${result.available})",
                ),
            )
            is MedicalResult.Conflict -> clubWriteConflict(result.clubId)
        }

    /**
     * Foco de treino da semana (issue #58). Só registra a decisão — o efeito
     * (evolução, lesão, recuperação) entra na virada da rodada, dentro do
     * claim que garante uma aplicação por rodada.
     */
    @PostMapping("/{id}/training")
    suspend fun setTrainingFocus(
        @PathVariable id: Long,
        @RequestBody req: TrainingFocusRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> =
        when (val result = setTrainingFocus.execute(ClubId(id), userId, req.focus)) {
            is TrainingFocusResult.Success -> json.okJson(result.club.toDto())
            is TrainingFocusResult.ClubNotFound -> ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $id não encontrado"),
            )
            is TrainingFocusResult.NotOwner -> ResponseEntity.status(403).body(
                ErrorResponse(code = "FORBIDDEN", message = "Você não é dono deste clube"),
            )
            is TrainingFocusResult.Conflict -> clubWriteConflict(result.clubId)
        }

    /**
     * Preço do ingresso do estádio (issue #59). `PUT`, e não `POST` como os
     * comandos vizinhos, porque aqui não há AÇÃO com efeito acumulado (treinar,
     * tratar, ampliar): é a substituição idempotente de um ajuste do clube —
     * mandar o mesmo preço duas vezes deixa o mundo igual.
     */
    @PutMapping("/{id}/ticket-price")
    suspend fun setTicketPrice(
        @PathVariable id: Long,
        @RequestBody req: TicketPriceRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> =
        when (val result = setTicketPrice.execute(ClubId(id), userId, req.ticketPriceCents)) {
            is TicketPriceResult.Success -> json.okJson(result.club.toDto())
            is TicketPriceResult.ClubNotFound -> ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $id não encontrado"),
            )
            is TicketPriceResult.NotOwner -> ResponseEntity.status(403).body(
                ErrorResponse(code = "FORBIDDEN", message = "Você não é dono deste clube"),
            )
            is TicketPriceResult.PriceOutOfRange -> ResponseEntity.badRequest().body(
                ErrorResponse(
                    code = "TICKET_PRICE_OUT_OF_RANGE",
                    message = "Preço ${result.price} fora da faixa permitida " +
                        "(${result.min}..${result.max} centavos)",
                ),
            )
            is TicketPriceResult.Conflict -> clubWriteConflict(result.clubId)
        }

    /**
     * Ampliação do estádio (issue #79). Custo, piso de assentos e posse vivem
     * no use case — aqui só se traduz cada desfecho em HTTP.
     */
    @PostMapping("/{id}/stadium/expand")
    suspend fun expandStadium(
        @PathVariable id: Long,
        @RequestBody req: ExpandStadiumRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> =
        when (val result = expandStadium.execute(ClubId(id), userId, req.additionalSeats)) {
            is ExpandStadiumResult.Success -> json.okJson(result.club.toDto())
            is ExpandStadiumResult.ClubNotFound -> ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $id não encontrado"),
            )
            is ExpandStadiumResult.NotOwner -> ResponseEntity.status(403).body(
                ErrorResponse(code = "FORBIDDEN", message = "Você não é dono deste clube"),
            )
            is ExpandStadiumResult.BelowMinimumSeats -> ResponseEntity.badRequest().body(
                ErrorResponse(
                    code = "INVALID_STADIUM_EXPANSION",
                    message = "Ampliação mínima de ${result.minimum} assentos " +
                        "(pedido: ${result.requested})",
                ),
            )
            is ExpandStadiumResult.InsufficientFunds -> ResponseEntity.status(402).body(
                ErrorResponse(
                    code = "INSUFFICIENT_FUNDS",
                    message = "Caixa insuficiente para ampliar " +
                        "(necessário ${result.required}, disponível ${result.available})",
                ),
            )
            is ExpandStadiumResult.Conflict -> clubWriteConflict(result.clubId)
        }
}

/**
 * `details` é `Map<String, String>` no contrato: os ids vão como CSV ("1,7"),
 * legíveis por máquina sem o cliente ter de parsear a `message`.
 * `private` no nível do arquivo = visível só neste arquivo (não há `static`).
 */
private fun playerIdsDetail(playerIds: List<Long>): Map<String, String> =
    mapOf("playerIds" to playerIds.joinToString(","))

/**
 * 409 do lock otimista do clube (issue #89): outra escrita alterou o clube
 * entre a leitura e a gravação, e nada foi aplicado. É o mesmo status do claim
 * disputado (`CLUB_ALREADY_CLAIMED`), com um `code` próprio porque a reação da
 * UI é outra — recarregar o clube e deixar o técnico decidir de novo.
 */
private fun clubWriteConflict(clubId: ClubId): ResponseEntity<Any> =
    ResponseEntity.status(409).body(
        ErrorResponse(
            code = "CLUB_WRITE_CONFLICT",
            message = "Clube ${clubId.value} foi alterado por outra operação; recarregue e tente de novo",
        ),
    )
