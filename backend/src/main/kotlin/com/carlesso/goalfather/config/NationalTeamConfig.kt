package com.carlesso.goalfather.config

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.NationalWindowRepository
import com.carlesso.goalfather.application.service.NationalTeamService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Seleções nas datas FIFA (issue #134). Um bean só, exposto pelo tipo
 * concreto: ele é o `NationalTeamClock` que o `PlayRoundService` chama e o
 * `NationalTeamUseCase` que o controller lê — o Spring injeta pelo tipo de
 * cada interface. Configuração própria (e não mais um `@Bean` no
 * `BeanConfig`) para a fiação da feature ficar num lugar só.
 */
@Configuration
class NationalTeamConfig {

    @Bean
    fun nationalTeamService(
        clubRepo: ClubRepository,
        windowRepo: NationalWindowRepository,
        leagueRepo: LeagueRepository,
    ): NationalTeamService = NationalTeamService(clubRepo, windowRepo, leagueRepo)
}
