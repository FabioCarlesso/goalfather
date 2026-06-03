package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.AvailableClubDto
import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.ExpandStadiumRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.LineupRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.toAvailableDto
import com.carlesso.goalfather.adapter.`in`.web.dto.toDto
import com.carlesso.goalfather.application.port.`in`.ClaimClubUseCase
import com.carlesso.goalfather.application.port.`in`.SaveLineupUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.ClaimResult
import com.carlesso.goalfather.domain.result.LineupResult
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/clubs")
class ClubController(
    private val clubRepo: ClubRepository,
    private val saveLineup: SaveLineupUseCase,
    private val claimClub: ClaimClubUseCase,
) {

    /**
     * Clubes sem dono — fluxo de seleção (issue #19). Mapeado em `/available`
     * (literal): o Spring casa antes de `/{id}`, então não há ambiguidade.
     */
    @GetMapping("/available")
    fun listAvailable(): List<AvailableClubDto> = runBlocking {
        clubRepo.findAvailable().map { it.toAvailableDto() }
    }

    @GetMapping("/{id}")
    fun getClub(@PathVariable id: Long): ResponseEntity<Any> = runBlocking {
        val club = clubRepo.findById(ClubId(id))
        if (club == null) {
            ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $id não encontrado"),
            )
        } else {
            ResponseEntity.ok(club)
        }
    }

    /** Reivindicar clube para o usuário autenticado (issue #19). */
    @PostMapping("/{id}/claim")
    fun claim(
        @PathVariable id: Long,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> = runBlocking {
        when (val result = claimClub.execute(ClubId(id), UserId(userId))) {
            is ClaimResult.Success -> ResponseEntity.ok(result.user.toDto())
            is ClaimResult.ClubNotFound -> ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $id não encontrado"),
            )
            is ClaimResult.AlreadyClaimed -> ResponseEntity.status(409).body(
                ErrorResponse(code = "CLUB_ALREADY_CLAIMED", message = "Clube $id já foi reivindicado"),
            )
        }
    }

    @PostMapping("/{id}/lineup")
    fun saveLineup(
        @PathVariable id: Long,
        @RequestBody req: LineupRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> = runBlocking {
        // Posse é verificada dentro do use case (fetch único) — issue #18/#19.
        when (val result = saveLineup.execute(ClubId(id), userId, req.formation, req.playerIds)) {
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
        }
    }

    @PostMapping("/{id}/stadium/expand")
    fun expandStadium(
        @PathVariable id: Long,
        @RequestBody req: ExpandStadiumRequest,
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Any> = runBlocking {
        val club = clubRepo.findById(ClubId(id))
            ?: return@runBlocking ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $id não encontrado"),
            )
        ownershipError(club, userId)?.let { return@runBlocking it }
        val cost = req.additionalSeats * 100_00L
        if (club.cash < cost) {
            return@runBlocking ResponseEntity.status(402).body(
                ErrorResponse(
                    code = "INSUFFICIENT_FUNDS",
                    message = "Caixa insuficiente para ampliar",
                ),
            )
        }
        val updated: Club = club.copy(
            cash = club.cash - cost,
            stadiumCapacity = club.stadiumCapacity + req.additionalSeats,
        )
        clubRepo.save(updated)
        ResponseEntity.ok(updated)
    }

    /**
     * Garante que o usuário autenticado é dono do clube (issue #18: A não
     * altera dados do clube de B). Devolve a resposta de erro a propagar, ou
     * `null` se está tudo certo. Usado pelo endpoint de estádio (que já carrega
     * o clube); o de escalação faz a checagem dentro do use case.
     */
    private fun ownershipError(club: Club, userId: Long): ResponseEntity<Any>? =
        if (club.ownerId != userId) {
            ResponseEntity.status(403).body(
                ErrorResponse(code = "FORBIDDEN", message = "Você não é dono deste clube"),
            )
        } else {
            null
        }
}
