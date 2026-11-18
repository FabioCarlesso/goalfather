package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.toDto
import com.carlesso.goalfather.application.port.`in`.CompetitionQueryUseCase
import com.carlesso.goalfather.domain.model.ClubId
import kotlinx.serialization.json.Json
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Calendário do clube semana a semana (issue #133). Controller próprio, fora
 * da `ClubController`: é leitura do calendário do MUNDO filtrada pelo clube,
 * servida pelo mesmo caso de uso da navegação pelas competições.
 */
@RestController
class ClubScheduleController(
    private val queries: CompetitionQueryUseCase,
    private val json: Json,
) {

    @GetMapping("/api/clubs/{id}/schedule")
    suspend fun schedule(
        @PathVariable id: Long,
        @RequestParam(required = false, defaultValue = "$DEFAULT_WEEKS") weeks: Int,
    ): ResponseEntity<Any> {
        if (weeks !in WEEKS_RANGE) {
            return ResponseEntity.status(400).body(
                ErrorResponse(
                    code = "INVALID_PARAMETER",
                    message = "Parâmetro 'weeks' fora de $WEEKS_RANGE",
                    details = mapOf("parameter" to "weeks", "value" to weeks.toString()),
                ),
            )
        }
        val schedule = queries.clubSchedule(ClubId(id), weeks)
            ?: return ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $id não encontrado"),
            )
        return json.okJson(schedule.toDto())
    }

    private companion object {
        const val DEFAULT_WEEKS = 8
        val WEEKS_RANGE = 1..26
    }
}
