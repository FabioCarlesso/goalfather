package com.carlesso.goalfather.config

import com.carlesso.goalfather.application.port.`in`.BuyPlayerUseCase
import com.carlesso.goalfather.application.port.`in`.ClaimClubUseCase
import com.carlesso.goalfather.application.port.`in`.LoginUseCase
import com.carlesso.goalfather.application.port.`in`.PlayRoundUseCase
import com.carlesso.goalfather.application.port.`in`.RegisterUserUseCase
import com.carlesso.goalfather.application.port.`in`.SaveLineupUseCase
import com.carlesso.goalfather.application.port.`in`.SellPlayerUseCase
import com.carlesso.goalfather.application.port.`in`.StreamMatchUseCase
import com.carlesso.goalfather.application.port.out.ClubClaimRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.application.port.out.PasswordHasher
import com.carlesso.goalfather.application.port.out.UserRepository
import com.carlesso.goalfather.application.service.BuyPlayerService
import com.carlesso.goalfather.application.service.ClaimClubService
import com.carlesso.goalfather.application.service.LoginService
import com.carlesso.goalfather.application.service.PlayMatchService
import com.carlesso.goalfather.application.service.PlayRoundService
import com.carlesso.goalfather.application.service.RegisterUserService
import com.carlesso.goalfather.application.service.SaveLineupService
import com.carlesso.goalfather.application.service.SellPlayerService
import com.carlesso.goalfather.domain.engine.MatchSimulator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wiring explícito de application services como beans. Os services não
 * têm anotações Spring (estão em `application/` — camada agnóstica), por
 * isso são registrados aqui.
 *
 * Inversão: cada `@Bean` recebe os ports (interfaces) via parâmetros do
 * método; Spring resolve para as implementações `@Repository` em
 * `adapter/out/persistence`. O domínio não conhece esses adapters.
 */
@Configuration
class BeanConfig {

    @Bean
    fun matchSimulator(): MatchSimulator = MatchSimulator()

    @Bean
    fun buyPlayerUseCase(
        clubRepo: ClubRepository,
        marketRepo: MarketRepository,
    ): BuyPlayerUseCase = BuyPlayerService(clubRepo, marketRepo)

    @Bean
    fun sellPlayerUseCase(
        clubRepo: ClubRepository,
        marketRepo: MarketRepository,
    ): SellPlayerUseCase = SellPlayerService(clubRepo, marketRepo)

    @Bean
    fun saveLineupUseCase(
        clubRepo: ClubRepository,
    ): SaveLineupUseCase = SaveLineupService(clubRepo)

    @Bean
    fun playRoundUseCase(
        clubRepo: ClubRepository,
        leagueRepo: LeagueRepository,
        simulator: MatchSimulator,
    ): PlayRoundUseCase = PlayRoundService(clubRepo, leagueRepo, simulator)

    @Bean
    fun streamMatchUseCase(
        clubRepo: ClubRepository,
        leagueRepo: LeagueRepository,
        simulator: MatchSimulator,
    ): StreamMatchUseCase = PlayMatchService(clubRepo, leagueRepo, simulator)

    // ── Auth (issue #18) + seleção de clube (issue #19) ───────────────────
    @Bean
    fun registerUserUseCase(
        userRepo: UserRepository,
        passwordHasher: PasswordHasher,
    ): RegisterUserUseCase = RegisterUserService(userRepo, passwordHasher)

    @Bean
    fun loginUseCase(
        userRepo: UserRepository,
        passwordHasher: PasswordHasher,
    ): LoginUseCase = LoginService(userRepo, passwordHasher)

    @Bean
    fun claimClubUseCase(
        claimRepo: ClubClaimRepository,
    ): ClaimClubUseCase = ClaimClubService(claimRepo)
}
