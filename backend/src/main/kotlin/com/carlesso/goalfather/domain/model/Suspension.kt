package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.serialization.CompetitionIdSerializer
import kotlinx.serialization.Serializable

/**
 * Suspensão automática de um jogador numa competição (issue #130).
 *
 * Mesmo idioma do [Availability]: em vez de `suspended: Boolean` + "quantos
 * jogos faltam" (que permitiria `suspended = false` com 2 jogos pendentes), a
 * suspensão é um VALOR que existe ou não na lista do jogador. "Suspenso por
 * zero jogos" não é representável — o `init` exige `>= 1`, e quem cumpre o
 * último jogo simplesmente sai da lista.
 *
 * A competição faz parte da identidade: um vermelho no Brasileirão tira o
 * jogador do próximo jogo DO Brasileirão, não da copa.
 */
@Serializable
data class Suspension(
    @Serializable(with = CompetitionIdSerializer::class)
    val competitionId: CompetitionId,
    val matchesLeft: Int,
) {
    init {
        require(matchesLeft >= 1) { "Suspensão deve ter ao menos 1 jogo: $matchesLeft" }
    }
}

/**
 * Regra disciplinar de uma competição (issue #130) — DADO da competição, não
 * código: cada regulamento escolhe os números.
 *
 * - [yellowsForSuspension]: amarelos acumulados que suspendem o próximo jogo
 *   (3 no Brasileirão e na Libertadores).
 * - [matchesPerRedCard]: jogos de gancho automático por expulsão (vermelho
 *   direto ou segundo amarelo na mesma partida). Gancho maior por julgamento
 *   do tribunal está fora de escopo.
 * - [resetsYellowsAtKnockout]: se os amarelos pendentes zeram ao entrar no
 *   mata-mata (Libertadores/Champions fazem isso).
 */
data class DisciplineRules(
    val yellowsForSuspension: Int = 3,
    val matchesPerRedCard: Int = 1,
    val resetsYellowsAtKnockout: Boolean = false,
) {
    init {
        require(yellowsForSuspension >= 1) { "Amarelos para suspensão deve ser >= 1: $yellowsForSuspension" }
        require(matchesPerRedCard >= 1) { "Gancho por vermelho deve ser >= 1: $matchesPerRedCard" }
    }

    companion object {
        /** 3 amarelos ou 1 vermelho = 1 jogo, sem zerar: o padrão da CBF. */
        val DEFAULT: DisciplineRules = DisciplineRules()
    }
}
