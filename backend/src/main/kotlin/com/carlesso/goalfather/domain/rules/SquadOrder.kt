package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Player

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
    val byQuality = sortedWith(compareByDescending<Player> { it.overall }.thenBy { it.id.value })
    // `fold` sobre as vagas acumula os titulares escolhidos sem `var`: cada
    // vaga pega o melhor da posição que ainda não está na lista.
    val byPosition = formation.slots.fold(emptyList<Player>()) { chosen, position ->
        val best = byQuality.firstOrNull { it.position == position && it !in chosen }
        if (best == null) chosen else chosen + best
    }
    val starters = byPosition + byQuality.filterNot { it in byPosition }.take(formation.slots.size - byPosition.size)
    val reserves = byQuality.filterNot { it in starters }.sortedWith(
        compareBy<Player> { it.position.ordinal }.thenByDescending { it.overall }.thenBy { it.id.value },
    )
    return starters + reserves
}
