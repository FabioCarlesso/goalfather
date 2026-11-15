package com.carlesso.goalfather.adapter.`in`.web

import com.carlesso.goalfather.adapter.`in`.web.dto.WorldCountryDto
import com.carlesso.goalfather.adapter.`in`.web.dto.toDto
import com.carlesso.goalfather.application.port.`in`.WorldOnboardingUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Mundo do jogo para o onboarding (issue #131): o primeiro passo, país → liga.
 * O último passo (clubes livres de uma liga) é `GET /api/clubs/available`.
 */
@RestController
@RequestMapping("/api/world")
class WorldController(private val onboarding: WorldOnboardingUseCase) {

    @GetMapping("/countries")
    suspend fun countries(): List<WorldCountryDto> = onboarding.countries().map { it.toDto() }
}
