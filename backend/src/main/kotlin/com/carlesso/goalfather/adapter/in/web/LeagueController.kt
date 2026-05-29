package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.PlayRoundResponse
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.RoundStatus
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/league")
class LeagueController(
    private val leagueRepo: LeagueRepository,
) {

    @GetMapping("/standings")
    fun getStandings() = runBlocking { leagueRepo.currentStandings() }

    @GetMapping("/round/current")
    fun getCurrentRound(): ResponseEntity<Any> = runBlocking {
        // Encontra a rodada mais recente nao terminada, ou a maior numerada.
        // Implementacao simples: findRound(1) ate falhar. Para escalar,
        // adicionar findLatest no port.
        var n = 1
        var current: com.carlesso.goalfather.domain.model.Round? = null
        while (true) {
            val r = leagueRepo.findRound(n) ?: break
            current = r
            if (r.status != RoundStatus.Finished) break
            n++
        }
        if (current == null) {
            ResponseEntity.status(404).body(
                ErrorResponse(code = "ROUND_NOT_FOUND", message = "Nenhuma rodada disponivel"),
            )
        } else {
            ResponseEntity.ok(current)
        }
    }

    @PostMapping("/round/play")
    fun playRound(): ResponseEntity<Any> = runBlocking {
        var n = 1
        var current: com.carlesso.goalfather.domain.model.Round? = null
        while (true) {
            val r = leagueRepo.findRound(n) ?: break
            current = r
            if (r.status != RoundStatus.Finished) break
            n++
        }
        if (current == null) {
            ResponseEntity.status(404).body(
                ErrorResponse(code = "ROUND_NOT_FOUND", message = "Nenhuma rodada disponivel"),
            )
        } else if (current.status == RoundStatus.Finished) {
            ResponseEntity.status(409).body(
                ErrorResponse(
                    code = "ROUND_ALREADY_FINISHED",
                    message = "Rodada ${current.number} já encerrada",
                ),
            )
        } else {
            // Marca como InProgress; cliente conecta no WS para acompanhar.
            leagueRepo.saveRound(current.copy(status = RoundStatus.InProgress))
            ResponseEntity.ok(PlayRoundResponse(roundNumber = current.number))
        }
    }
}
