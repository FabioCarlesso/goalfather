package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.PlayRoundResponse
import com.carlesso.goalfather.application.port.`in`.RoundReadinessUseCase
import com.carlesso.goalfather.application.port.`in`.SeasonHistoryUseCase
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.StartRoundResult
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/league")
class LeagueController(
    private val leagueRepo: LeagueRepository,
    private val readiness: RoundReadinessUseCase,
    private val history: SeasonHistoryUseCase,
) {

    /**
     * Temporadas encerradas, da mais recente para a mais antiga (issue #60).
     * Lista vazia enquanto a primeira temporada não virou — "sem história" é
     * estado normal do jogo novo, não erro.
     */
    @GetMapping("/history")
    suspend fun getHistory(): ResponseEntity<Any> =
        ResponseEntity.ok(history.history())

    /** Uma temporada do histórico, com a classificação final de cada divisão. */
    @GetMapping("/history/{season}")
    suspend fun getSeasonRecord(@PathVariable season: Int): ResponseEntity<Any> {
        val record = history.season(season)
        return if (record == null) {
            ResponseEntity.status(404).body(
                ErrorResponse(
                    code = "SEASON_RECORD_NOT_FOUND",
                    message = "Temporada $season não encerrada",
                ),
            )
        } else {
            ResponseEntity.ok(record)
        }
    }

    /**
     * Perfil do técnico: temporadas disputadas, títulos e melhor campanha do
     * clube. 404 enquanto o clube não fechou nenhuma temporada — quem nunca
     * terminou um campeonato não tem carreira, e isso é diferente de uma
     * carreira zerada.
     */
    @GetMapping("/history/club/{clubId}")
    suspend fun getClubCareer(@PathVariable clubId: Long): ResponseEntity<Any> {
        val career = history.career(ClubId(clubId))
        return if (career == null) {
            ResponseEntity.status(404).body(
                ErrorResponse(
                    code = "CAREER_NOT_FOUND",
                    message = "Clube $clubId sem temporada encerrada",
                ),
            )
        } else {
            ResponseEntity.ok(career)
        }
    }

    @GetMapping("/standings")
    suspend fun getStandings(@RequestParam(required = false) season: Int?): ResponseEntity<Any> {
        if (season == null) {
            // Uma tabela por divisão, da elite para baixo (issue #47).
            return ResponseEntity.ok(leagueRepo.currentStandings())
        }
        // Histórico: tabelas finais de uma temporada já encerrada (issue #11).
        val standings = leagueRepo.findStandings(season)
        return if (standings.isEmpty()) {
            ResponseEntity.status(404).body(
                ErrorResponse(code = "STANDINGS_NOT_FOUND", message = "Temporada $season sem tabela"),
            )
        } else {
            ResponseEntity.ok(standings)
        }
    }

    @GetMapping("/round/current")
    suspend fun getCurrentRound(): ResponseEntity<Any> {
        // A rodada "atual" é sempre a de maior número: após cada RoundFinished
        // o PlayRoundService já cria a próxima (Scheduled), então a mais recente
        // é a que está em aberto.
        val current = leagueRepo.findLatest()
        return if (current == null) {
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
    suspend fun getReadiness(): ResponseEntity<Any> =
        ResponseEntity.ok(readiness.status())

    /** Técnico autenticado sinaliza que está pronto para a rodada (issue #20). */
    @PostMapping("/round/ready")
    suspend fun markReady(@AuthenticationPrincipal userId: Long): ResponseEntity<Any> =
        ResponseEntity.ok(readiness.markReady(UserId(userId)))

    /**
     * Inicia a simulação da rodada. Em liga compartilhada só destrava quando
     * TODOS os técnicos humanos sinalizaram prontos (issue #20); caso contrário
     * retorna 409 com quem ainda falta.
     */
    @PostMapping("/round/play")
    suspend fun playRound(): ResponseEntity<Any> =
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
                        // Segundos até o auto-start do escape hatch (issue #45);
                        // ausente quando o cronômetro ainda não está armado.
                        "secondsRemaining" to (result.status.secondsRemaining?.toString() ?: ""),
                    ),
                ),
            )
        }
}
