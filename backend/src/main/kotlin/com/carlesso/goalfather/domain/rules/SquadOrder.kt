package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.Position

/**
 * Ordem do elenco de um clube recém-criado (issue #131). A escalação
 * automática (`Club.startingLineup`, usada por todo clube da IA e por quem
 * nunca salvou escalação) entra com os 11 PRIMEIROS do elenco. O pack lista os
 * jogadores agrupados por posição — goleiros primeiro —, então sem esta ordem
 * um clube real entraria em campo com dois ou três goleiros.
 *
 * Os 11 primeiros viram o melhor time em [formation]: para cada vaga, o melhor
 * `overall` ainda livre daquela posição, na ordem das vagas da formação
 * (goleiro, defesa, meio, ataque — a mesma em que a UI as desenha). Vaga sem
 * jogador da posição é preenchida pelos melhores que sobraram. Depois vêm os
 * reservas, por posição e `overall`. Empates caem no id, para a ordem ser total
 * e o seed determinístico.
 */
fun List<Player>.inStartingOrder(formation: Formation = Formation.F_4_4_2): List<Player> {
    val byQuality = byQuality()
    val byPosition = byQuality.bestFor(formation.slots)
    val starters = byPosition + byQuality.filterNot { it in byPosition }.take(formation.slots.size - byPosition.size)
    val reserves = byQuality.filterNot { it in starters }.sortedWith(
        compareBy<Player> { it.position.ordinal }.thenByDescending { it.overall }.thenBy { it.id.value },
    )
    return starters + reserves
}

/**
 * Reservas na ordem em que cobrem as [vacancies] de uma escalação SALVA
 * (revisão da PR #154): para cada vaga, o melhor reserva da mesma posição
 * ainda livre; depois, os demais pelo `overall`. Uma vaga sem reserva da
 * posição fica com o primeiro dos demais — o melhor que sobrou —, porque o
 * chamador completa o time com `take(11)`.
 */
fun List<Player>.inReplacementOrder(vacancies: List<Position>): List<Player> {
    val byQuality = byQuality()
    val samePosition = byQuality.bestFor(vacancies)
    return samePosition + byQuality.filterNot { it in samePosition }
}

/** Melhor `overall` primeiro; empate no id, para a ordem ser total. */
private fun List<Player>.byQuality(): List<Player> =
    sortedWith(compareByDescending<Player> { it.overall }.thenBy { it.id.value })

/**
 * Para cada posição de [slots], o primeiro jogador (desta lista, já ordenada
 * por qualidade) daquela posição ainda não escolhido. `fold` acumula os
 * escolhidos sem `var`; vaga sem jogador da posição fica sem ninguém.
 */
private fun List<Player>.bestFor(slots: List<Position>): List<Player> =
    slots.fold(emptyList()) { chosen, position ->
        val best = firstOrNull { it.position == position && it !in chosen }
        if (best == null) chosen else chosen + best
    }
