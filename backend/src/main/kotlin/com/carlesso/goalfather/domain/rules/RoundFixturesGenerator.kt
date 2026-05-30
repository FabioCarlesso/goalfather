package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStatus

/**
 * Gera os pareamentos de uma rodada via algoritmo de Berger (round-robin
 * de turno único). Função PURA — mesmo input produz exatamente o mesmo
 * schedule (critério de determinismo da issue #1).
 *
 * Ideia do Berger: o primeiro clube (aqui, o de menor `id`) fica FIXO e os
 * demais rotacionam a cada rodada. Para `N` clubes (par) o turno tem `N-1`
 * rodadas distintas, cada uma com `N/2` partidas, e nenhum par se repete
 * dentro do turno.
 *
 * Espelha `generateRound` em `frontend/src/mocks/seed.ts`. Quando o backend
 * assume a geração (este arquivo), o frontend pode descartar sua cópia.
 *
 * Idioma Kotlin: função top-level pura em `domain/rules` (sem classe
 * utilitária estática à la Java) e `data class` imutáveis montadas via
 * `map` — nenhuma mutação de estado.
 */
fun generateRound(roundNumber: Int, season: Int, clubs: List<Club>): Round {
    require(roundNumber >= 1) { "roundNumber deve ser >= 1: $roundNumber" }
    require(clubs.size >= 2) { "Liga precisa de ao menos 2 clubes: ${clubs.size}" }
    require(clubs.size % 2 == 0) { "Número de clubes deve ser par: ${clubs.size}" }

    // Ordena por id para determinismo independente da ordem de entrada e
    // para garantir o "clube fixo" do Berger (o de menor id).
    val ordered = clubs.sortedBy { it.id.value }
    val n = ordered.size
    val fixed = ordered.first()
    val rotating = ordered.drop(1)

    // Rotação dependente da rodada — ciclo de N-1 posições (turno único).
    val offset = (roundNumber - 1) % (n - 1)
    val rotated = rotating.drop(offset) + rotating.take(offset)
    val order = listOf(fixed) + rotated

    val matches = (0 until n / 2).map { i ->
        val home = order[i]
        val away = order[n - 1 - i]
        RoundMatch(
            matchId = roundNumber * 1000L + i + 1,
            homeClubId = home.id,
            awayClubId = away.id,
            homeClubName = home.name,
            awayClubName = away.name,
        )
    }

    return Round(
        number = roundNumber,
        season = season,
        status = RoundStatus.Scheduled,
        matches = matches,
    )
}
