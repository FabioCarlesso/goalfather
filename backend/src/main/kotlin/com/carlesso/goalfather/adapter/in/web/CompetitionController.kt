package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.toDetailDto
import com.carlesso.goalfather.adapter.`in`.web.dto.toDto
import com.carlesso.goalfather.adapter.`in`.web.dto.toSummaryDto
import com.carlesso.goalfather.application.port.`in`.CompetitionLookup
import com.carlesso.goalfather.application.port.`in`.CompetitionQueryUseCase
import com.carlesso.goalfather.domain.model.CompetitionId
import kotlinx.serialization.json.Json
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Navegação pelas competições do mundo (issue #133): lista, detalhe, tabela,
 * jogos por encaixe, artilharia e chave. Tudo leitura; as regras (desempate,
 * faixas, quem passou) já vêm aplicadas pelo caso de uso.
 *
 * Corpos pelo kotlinx (`okJson`), como na `LeagueController`: `RoundMatchDto`
 * e o `CupDto` carregam `sealed` com discriminador.
 */
@RestController
@RequestMapping("/api/competitions")
class CompetitionController(
    private val queries: CompetitionQueryUseCase,
    private val json: Json,
) {

    @GetMapping
    suspend fun list(): ResponseEntity<Any> =
        json.okJson(queries.competitions().map { it.toSummaryDto() })

    @GetMapping("/{competitionId}")
    suspend fun detail(@PathVariable competitionId: String): ResponseEntity<Any> =
        respond(competitionId, queries.competition(CompetitionId(competitionId))) { json.okJson(it.toDetailDto()) }

    @GetMapping("/{competitionId}/standings")
    suspend fun standings(@PathVariable competitionId: String): ResponseEntity<Any> =
        respond(competitionId, queries.standings(CompetitionId(competitionId)), "STANDINGS_NOT_FOUND") {
            json.okJson(it.toDto())
        }

    @GetMapping("/{competitionId}/matches")
    suspend fun matches(@PathVariable competitionId: String): ResponseEntity<Any> =
        respond(competitionId, queries.matches(CompetitionId(competitionId))) { days ->
            json.okJson(days.map { it.toDto() })
        }

    @GetMapping("/{competitionId}/scorers")
    suspend fun scorers(@PathVariable competitionId: String): ResponseEntity<Any> =
        respond(competitionId, queries.scorers(CompetitionId(competitionId))) { scorers ->
            json.okJson(scorers.map { it.toDto() })
        }

    @GetMapping("/{competitionId}/bracket")
    suspend fun bracket(@PathVariable competitionId: String): ResponseEntity<Any> =
        respond(competitionId, queries.bracket(CompetitionId(competitionId)), "BRACKET_NOT_FOUND") {
            json.okJson(it.toDto())
        }

    /**
     * Traduz o [CompetitionLookup] em HTTP. O `when` sobre o `sealed` é
     * exaustivo — sem `else` —, então um desfecho novo no caso de uso obriga
     * a decidir o status aqui. `inline` + lambda: o corpo de sucesso é
     * montado no ponto de chamada, sem alocar a lambda.
     */
    private inline fun <T> respond(
        id: String,
        lookup: CompetitionLookup<T>,
        notAvailableCode: String = "NOT_FOUND",
        ok: (T) -> ResponseEntity<Any>,
    ): ResponseEntity<Any> = when (lookup) {
        is CompetitionLookup.Found -> ok(lookup.value)
        CompetitionLookup.UnknownCompetition -> ResponseEntity.status(404).body(
            ErrorResponse(code = "COMPETITION_NOT_FOUND", message = "Competição $id não encontrada"),
        )
        CompetitionLookup.NotAvailable -> ResponseEntity.status(404).body(
            ErrorResponse(code = notAvailableCode, message = "Competição $id sem este recurso"),
        )
    }
}
