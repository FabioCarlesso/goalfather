package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.PlayRoundResponse
import com.carlesso.goalfather.application.port.`in`.RoundReadinessUseCase
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.StartRoundResult
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/league")
class LeagueController(
    private val leagueRepo: LeagueRepository,
    private val readiness: RoundReadinessUseCase,
) {

    @GetMapping("/standings")
    fun getStandings(@RequestParam(required = false) season: Int?): ResponseEntity<Any> = runBlocking {
        if (season == null) {
            ResponseEntity.ok(leagueRepo.currentStandings())
        } else {
            // Histórico: tabela final de uma temporada já encerrada (issue #11).
            val standings = leagueRepo.findStandings(season)
            if (standings == null) {
                ResponseEntity.status(404).body(
                    ErrorResponse(code = "STANDINGS_NOT_FOUND", message = "Temporada $season sem tabela"),
                )
            } else {
                ResponseEntity.ok(standings)
            }
        }
    }

    @GetMapping("/round/current")
    fun getCurrentRound(): ResponseEntity<Any> = runBlocking {
        // A rodada "atual" é sempre a de maior número: após cada RoundFinished
        // o PlayRoundService já cria a próxima (Scheduled), então a mais recente
        // é a que está em aberto.
        val current = leagueRepo.findLatest()
        if (current == null) {
            ResponseEntity.status(404).body(
                ErrorResponse(code = "ROUND_NOT_FOUND", message = "Nenhuma rodada disponivel"),
            )
        } else {
            ResponseEntity.ok(current)
        }
    }

    /**
     * Estado de prontidão da rodada corrente — alimenta o card de lobby da UI
     * (issue #20). Leitura barata; o cliente faz refetch após sinalizar pronto.
     */
    @GetMapping("/round/readiness")
    fun getReadiness(): ResponseEntity<Any> = runBlocking {
        ResponseEntity.ok(readiness.status())
    }

    /** Técnico autenticado sinaliza que está pronto para a rodada (issue #20). */
    @PostMapping("/round/ready")
    fun markReady(@AuthenticationPrincipal userId: Long): ResponseEntity<Any> = runBlocking {
        ResponseEntity.ok(readiness.markReady(UserId(userId)))
    }

    /**
     * Inicia a simulação da rodada. Em liga compartilhada só destrava quando
     * TODOS os técnicos humanos sinalizaram prontos (issue #20); caso contrário
     * retorna 409 com quem ainda falta.
     */
    @PostMapping("/round/play")
    fun playRound(): ResponseEntity<Any> = runBlocking {
        when (val result = readiness.start()) {
            is StartRoundResult.Started ->
                ResponseEntity.ok(PlayRoundResponse(roundNumber = result.roundNumber))

            is StartRoundResult.NoRound -> ResponseEntity.status(404).body(
                ErrorResponse(code = "ROUND_NOT_FOUND", message = "Nenhuma rodada disponivel"),
            )

            is StartRoundResult.AlreadyFinished -> ResponseEntity.status(409).body(
                ErrorResponse(
                    code = "ROUND_ALREADY_FINISHED",
                    message = "Rodada ${result.roundNumber} já encerrada",
                ),
            )

            is StartRoundResult.NotReady -> ResponseEntity.status(409).body(
                ErrorResponse(
                    code = "ROUND_NOT_READY",
                    message = "Aguardando técnicos: ${result.status.pendingUsernames.joinToString(", ")}",
                    details = mapOf(
                        "ready" to result.status.readyCount.toString(),
                        "total" to result.status.totalCount.toString(),
                        "pending" to result.status.pendingUsernames.joinToString(","),
                    ),
                ),
            )
        }
    }
}
