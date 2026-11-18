package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.NationalTeamsOverviewDto
import com.carlesso.goalfather.adapter.`in`.web.dto.toDto
import com.carlesso.goalfather.application.port.`in`.NationalTeamUseCase
import com.carlesso.goalfather.domain.model.ClubId
import kotlinx.serialization.json.Json
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * Seleções nas datas FIFA (issue #134): o painel das seleções e os convocados
 * de um clube. Só leitura — convocar e jogar é do relógio do jogo.
 *
 * Os convocados ficam em `/api/clubs/{id}/call-ups` (recurso do clube, como
 * `/board` e `/offers`), mas num controller próprio: a feature mora inteira
 * aqui, sem mexer no `ClubController`.
 */
@RestController
class NationalTeamController(
    private val nationalTeams: NationalTeamUseCase,
    private val json: Json,
) {

    @GetMapping("/api/national-teams")
    suspend fun overview(): NationalTeamsOverviewDto = nationalTeams.overview().toDto()

    @GetMapping("/api/clubs/{id}/call-ups")
    suspend fun callUps(@PathVariable id: Long): ResponseEntity<Any> {
        val callUps = nationalTeams.clubCallUps(ClubId(id))
            ?: return ResponseEntity.status(404).body(
                ErrorResponse(code = "CLUB_NOT_FOUND", message = "Clube $id não encontrado"),
            )
        return json.okJson(callUps.toDto())
    }
}
