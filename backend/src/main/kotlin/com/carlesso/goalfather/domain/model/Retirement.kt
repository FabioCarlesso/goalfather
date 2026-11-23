package com.carlesso.goalfather.domain.model

/**
 * Um jogador pendurou as chuteiras na virada de temporada (issue #55). No fio,
 * quem espelha `Retirement` do `contract/openapi.yaml` é o `RetirementDto` —
 * este tipo não é `@Serializable` (issue #96).
 *
 * Até a #139 carregava também o garoto que subia no lugar (reposição 1:1). A
 * base virou uma entrada anual por clube, sem par com quem saiu: os garotos
 * viajam à parte, em [YouthPromotion].
 */
data class Retirement(
    val clubId: ClubId,
    val retired: Player,
)

/**
 * Um garoto da base que subiu ao elenco principal na virada de temporada
 * (issue #139): de 2 a 4 por clube, com nome e nacionalidade do país do clube.
 */
data class YouthPromotion(
    val clubId: ClubId,
    val player: Player,
)
