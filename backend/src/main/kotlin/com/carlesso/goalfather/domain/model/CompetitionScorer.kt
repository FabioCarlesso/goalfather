package com.carlesso.goalfather.domain.model

/**
 * Um artilheiro dos clubes de uma competição (issue #133). Nomes copiados como
 * FOTO, como no `SeasonTopScorer`: a linha se lê sozinha.
 *
 * Corte do MVP: [goals] são os gols da TEMPORADA do jogador (`Player.goals`),
 * em todas as competições — o jogo ainda não separa gols por competição.
 */
data class CompetitionScorer(
    val playerId: PlayerId,
    val playerName: String,
    val clubId: ClubId,
    val clubName: String,
    val goals: Int,
)
