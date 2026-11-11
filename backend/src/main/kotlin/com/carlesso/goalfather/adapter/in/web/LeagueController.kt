package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.PlayRoundResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.toDto
import com.carlesso.goalfather.application.port.`in`.CalendarUseCase
import com.carlesso.goalfather.application.port.`in`.RoundReadinessUseCase
import com.carlesso.goalfather.application.port.`in`.SeasonHistoryUseCase
import com.carlesso.goalfather.application.port.out.CupRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.UserId
import com.carlesso.goalfather.domain.result.StartRoundResult
import kotlinx.serialization.json.Json
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Endpoints da liga. Nenhum corpo de sucesso é tipo de domínio (issue #97): todo
 * handler mapeia para o DTO de `LeagueDtos.kt`/`CupDtos.kt` e serializa pelo
 * kotlinx (`okJson`). Assim o JSON que sai é o que os testes de DTO verificam —
 * pelo Jackson, que o Spring usa quando o tipo declarado é `Any`, sairiam
 * também os getters computados do domínio, sem passar pelo contrato. Quando um
 * deles interessa ao cliente, o DTO o materializa e o `openapi.yaml` o declara
 * (ex.: `ReadinessStatus.allReady`/`canStart`, issue #98).
 */
@RestController
@RequestMapping("/api/league")
class LeagueController(
    private val leagueRepo: LeagueRepository,
    private val readiness: RoundReadinessUseCase,
    private val history: SeasonHistoryUseCase,
    private val cups: CupRepository,
    private val calendar: CalendarUseCase,
    private val json: Json,
) {

    /**
     * Relógio do jogo (issue #125): o encaixe corrente (semana + meio/fim de
     * semana) e, com `clubId`, o próximo jogo do clube e a competição dele.
     * Sem encaixe no calendário devolve tudo ausente — estado normal de uma
     * liga sem calendário, não erro.
     */
    @GetMapping("/calendar")
    suspend fun getCalendar(@RequestParam(required = false) clubId: Long?): ResponseEntity<Any> =
        json.okJson(calendar.calendar(clubId?.let(::ClubId)).toDto())


    /**
     * Chave da copa mata-mata (issue #61). Sem `season`, a da temporada mais
     * recente — a que está em disputa. 404 quando a temporada não teve copa
     * (liga pequena demais, ou save anterior à copa): é estado normal do jogo,
     * mas não há chave nenhuma para mostrar.
     *
     * Leitura direta do port de saída, como `/standings`: não há projeção nem
     * regra a aplicar — o DTO só expõe derivações que o domínio já calcula.
     */
    @GetMapping("/cup")
    suspend fun getCup(@RequestParam(required = false) season: Int?): ResponseEntity<Any> {
        val cup = if (season == null) cups.findLatest() else cups.findBySeason(season)
        return if (cup == null) {
            ResponseEntity.status(404).body(
                ErrorResponse(
                    code = "CUP_NOT_FOUND",
                    message = if (season == null) "Nenhuma copa disputada" else "Temporada $season sem copa",
                ),
            )
        } else {
            // kotlinx explícito: os confrontos são `sealed` (Bye/Fixture) e
            // precisam do discriminador — ver `okJson`.
            json.okJson(cup.toDto())
        }
    }

    /**
     * Temporadas encerradas, da mais recente para a mais antiga (issue #60).
     * Lista vazia enquanto a primeira temporada não virou — "sem história" é
     * estado normal do jogo novo, não erro.
     */
    @GetMapping("/history")
    suspend fun getHistory(): ResponseEntity<Any> =
        json.okJson(history.history().map { it.toDto() })

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
            json.okJson(record.toDto())
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
            json.okJson(career.toDto())
        }
    }

    @GetMapping("/standings")
    suspend fun getStandings(@RequestParam(required = false) season: Int?): ResponseEntity<Any> {
        if (season == null) {
            // Uma tabela por divisão, da elite para baixo (issue #47).
            return json.okJson(leagueRepo.currentStandings().map { it.toDto() })
        }
        // Histórico: tabelas finais de uma temporada já encerrada (issue #11).
        val standings = leagueRepo.findStandings(season)
        return if (standings.isEmpty()) {
            ResponseEntity.status(404).body(
                ErrorResponse(code = "STANDINGS_NOT_FOUND", message = "Temporada $season sem tabela"),
            )
        } else {
            json.okJson(standings.map { it.toDto() })
        }
    }

    @GetMapping("/round/current")
    suspend fun getCurrentRound(): ResponseEntity<Any> {
        // O encaixe "atual" é sempre o mais adiante no tempo: após cada
        // RoundFinished o PlayRoundService já gera o próximo (Scheduled), então
        // o mais recente é o que está em aberto (issue #125).
        val current = leagueRepo.findLatest()
        return if (current == null) {
            ResponseEntity.status(404).body(
                ErrorResponse(code = "ROUND_NOT_FOUND", message = "Nenhuma rodada disponivel"),
            )
        } else {
            // kotlinx explícito: o `stage` de cada partida é `sealed` (liga ×
            // copa, issue #61) e o cliente desambigua pelo `type` — ver `okJson`.
            json.okJson(current.toDto())
        }
    }

    /**
     * Estado de prontidão da rodada corrente — alimenta o card de lobby da UI
     * (issue #20). Leitura barata; o cliente faz refetch após sinalizar pronto.
     */
    @GetMapping("/round/readiness")
    suspend fun getReadiness(): ResponseEntity<Any> =
        json.okJson(readiness.status().toDto())

    /** Técnico autenticado sinaliza que está pronto para a rodada (issue #20). */
    @PostMapping("/round/ready")
    suspend fun markReady(@AuthenticationPrincipal userId: Long): ResponseEntity<Any> =
        json.okJson(readiness.markReady(UserId(userId)).toDto())

    /**
     * Inicia a simulação da rodada. Em liga compartilhada só destrava quando
     * TODOS os técnicos humanos sinalizaram prontos (issue #20); caso contrário
     * retorna 409 com quem ainda falta.
     */
    @PostMapping("/round/play")
    suspend fun playRound(): ResponseEntity<Any> =
        when (val result = readiness.start()) {
            is StartRoundResult.Started ->
                ResponseEntity.ok(PlayRoundResponse(matchday = result.matchday.key))

            is StartRoundResult.NoRound -> ResponseEntity.status(404).body(
                ErrorResponse(code = "ROUND_NOT_FOUND", message = "Nenhuma rodada disponivel"),
            )

            is StartRoundResult.AlreadyFinished -> ResponseEntity.status(409).body(
                ErrorResponse(
                    code = "ROUND_ALREADY_FINISHED",
                    message = "Encaixe ${result.matchday.key} já encerrado",
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
