package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.seed.MVP_START_YEAR
import java.time.LocalDate
import java.time.Month

/**
 * A data do JOGO (não a do relógio do SO): o dia do encaixe corrente — o
 * último gerado, como no `CalendarService`. É o "hoje" do contrato restante no
 * valor de mercado (issue #135). Sem encaixe ainda (mundo recém-criado, antes
 * da primeira rodada), 1º de janeiro do ano do MVP.
 *
 * Extension function num PORT: o serviço e o controller leem `leagueRepo.today()`
 * como se o método fosse do repositório, sem que a interface ganhe um membro
 * novo que todo adapter e todo mock teriam de implementar.
 */
suspend fun LeagueRepository.today(): LocalDate =
    findLatest()?.matchday?.date ?: LocalDate.of(MVP_START_YEAR, Month.JANUARY, 1)
