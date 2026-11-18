package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.ErrorResponse
import com.carlesso.goalfather.adapter.`in`.web.dto.toDto
import com.carlesso.goalfather.application.port.`in`.ContinentalUseCase
import com.carlesso.goalfather.domain.model.WorldQualificationRules
import kotlinx.serialization.json.Json
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Libertadores (issue #132): grupos, tabelas e chave do mata-mata. Sem
 * `season`, a edição mais recente — a em disputa. 404 quando o save não tem
 * Libertadores (o mundo do E2E, por exemplo): estado normal do jogo, mas não
 * há nada para mostrar — a mesma postura do `/api/league/cup`.
 */
@RestController
@RequestMapping("/api/libertadores")
class LibertadoresController(
    private val continentals: ContinentalUseCase,
    private val json: Json,
) {

    @GetMapping
    suspend fun getLibertadores(@RequestParam(required = false) season: Int?): ResponseEntity<Any> {
        val view = continentals.edition(WorldQualificationRules.CONMEBOL_LIBERTADORES, season)
            ?: return ResponseEntity.status(404).body(
                ErrorResponse(
                    code = "LIBERTADORES_NOT_FOUND",
                    message = if (season == null) "Nenhuma Libertadores no mundo" else "Temporada $season sem Libertadores",
                ),
            )
        return json.okJson(view.toDto())
    }
}
