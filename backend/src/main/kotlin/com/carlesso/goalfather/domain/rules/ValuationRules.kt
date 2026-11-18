package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Employment
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import java.time.LocalDate

/**
 * Valor de mercado de [player], em USD (issue #135).
 *
 * Assinatura combinada no commit base da onda 6: a #136 a usa para derivar os
 * salários dos contratos iniciais enquanto a #135 calibra a curva. O corpo
 * abaixo é PROVISÓRIO (só overall); a #135 o substitui pela curva completa
 * (overall, idade, contrato restante em [today] e força da liga).
 *
 * Nunca lê `player.salary`: o salário é que sai do valor, não o contrário.
 */
fun marketValue(
    player: Player,
    employment: Employment,
    leagueStrength: LeagueStrength,
    today: LocalDate,
): Money = when (employment) {
    Employment.FreeAgent -> Money.ZERO
    is Employment.UnderContract -> Money.dollars(10_000L * player.overall * player.overall)
}
