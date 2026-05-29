package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.ExpandStadiumRequest
import com.carlesso.goalfather.adapter.`in`.web.dto.LineupRequest
import com.carlesso.goalfather.application.port.`in`.SaveLineupUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.result.LineupResult
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
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
) {

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

    @PostMapping("/{id}/lineup")
    fun saveLineup(
        @PathVariable id: Long,
        @RequestBody req: LineupRequest,
    ): ResponseEntity<Any> = runBlocking {
        when (val result = saveLineup.execute(ClubId(id), req.formation, req.playerIds)) {
            is LineupResult.Success -> ResponseEntity.noContent().build()
            is LineupResult.ClubNotFound -> ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube ${result.clubId.value} não encontrado"),
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
    ): ResponseEntity<Any> = runBlocking {
        val club = clubRepo.findById(ClubId(id))
            ?: return@runBlocking ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $id não encontrado"),
            )
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
}
