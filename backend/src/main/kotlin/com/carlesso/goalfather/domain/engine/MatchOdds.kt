package com.carlesso.goalfather.domain.engine

import com.carlesso.goalfather.domain.engine.MatchSimulator.Companion.P_GOAL
import com.carlesso.goalfather.domain.engine.MatchSimulator.Companion.P_SAVE
import com.carlesso.goalfather.domain.model.attackFactor
import com.carlesso.goalfather.domain.model.defenseFactor
import com.carlesso.goalfather.domain.model.teamStrength

/**
 * As probabilidades de um lance, função pura do [MatchSetup] (força × tática).
 * Fonte ÚNICA da conta para as duas engines: o [MatchSimulator] (minuto a
 * minuto) e o [QuickMatchSimulator] (só os lances). Antes elas tinham cópias,
 * e só o teste estatístico impedia que se afastassem.
 *
 * Calcular as odds não consome o `Random`, então extrair a conta não muda
 * nenhum resultado com a mesma seed.
 *
 * - [goalP]: chance de UM lance virar gol. Acompanha a média dos dois pesos de
 *   gol: dois times abertos fazem um jogo de muitos gols, dois fechados um 0×0
 *   arrastado. O que sai do gol volta para a defesa ([saveP]) em vez de sumir,
 *   então cartão e lesão mantêm a mesma frequência, e pesos 1.0/1.0 reproduzem
 *   exatamente a engine anterior à tática.
 * - [homeRatio]: quanto dos gols é do mandante. O mesmo peso reparte os gols
 *   entre os times: força × tática.
 *
 * Peso de gol de cada lado = quanto ELE ataca × quanto o adversário DEIXA
 * atacar (issue #56). É o produto que faz a postura do rival pesar: atacar
 * contra um time fechado rende menos que contra um time aberto.
 */
internal data class MatchOdds(
    val goalP: Double,
    val saveP: Double,
    val homeRatio: Double,
) {
    companion object {
        fun of(setup: MatchSetup): MatchOdds {
            val homeGoalWeight = setup.home.attackFactor() * setup.away.defenseFactor()
            val awayGoalWeight = setup.away.attackFactor() * setup.home.defenseFactor()
            val goalP = (P_GOAL * (homeGoalWeight + awayGoalWeight) / 2).coerceIn(0.0, P_GOAL + P_SAVE)
            val homeShare = setup.home.teamStrength() * homeGoalWeight
            val awayShare = setup.away.teamStrength() * awayGoalWeight
            return MatchOdds(
                goalP = goalP,
                saveP = P_GOAL + P_SAVE - goalP,
                homeRatio = if (homeShare + awayShare > 0) homeShare / (homeShare + awayShare) else 0.5,
            )
        }
    }
}
