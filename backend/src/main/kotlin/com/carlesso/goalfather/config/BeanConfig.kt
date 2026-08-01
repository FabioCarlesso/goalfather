package com.carlesso.goalfather.config

import com.carlesso.goalfather.application.port.`in`.BuyPlayerUseCase
import com.carlesso.goalfather.application.port.`in`.ClaimClubUseCase
import com.carlesso.goalfather.application.port.`in`.LoginUseCase
import com.carlesso.goalfather.application.port.`in`.PlayRoundUseCase
import com.carlesso.goalfather.application.port.`in`.RegisterUserUseCase
import com.carlesso.goalfather.application.port.`in`.RoundReadinessUseCase
import com.carlesso.goalfather.application.port.`in`.SaveLineupUseCase
import com.carlesso.goalfather.application.port.`in`.SellPlayerUseCase
import com.carlesso.goalfather.application.port.`in`.StreamMatchUseCase
import com.carlesso.goalfather.application.port.`in`.TreatSquadUseCase
import com.carlesso.goalfather.application.port.out.ClubClaimRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.application.port.out.PasswordHasher
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.application.port.out.UserRepository
import com.carlesso.goalfather.application.service.BuyPlayerService
import com.carlesso.goalfather.application.service.ClaimClubService
import com.carlesso.goalfather.application.service.LoginService
import com.carlesso.goalfather.application.service.PlayMatchService
import com.carlesso.goalfather.application.service.PlayRoundService
import com.carlesso.goalfather.application.service.RegisterUserService
import com.carlesso.goalfather.application.service.RoundReadinessService
import com.carlesso.goalfather.application.service.SaveLineupService
import com.carlesso.goalfather.application.service.SellPlayerService
import com.carlesso.goalfather.application.service.TreatSquadService
import com.carlesso.goalfather.domain.engine.MatchSimulator
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

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
@EnableConfigurationProperties(RoundReadinessProperties::class)
class BeanConfig {

    /**
     * Fonte de tempo injetável (issue #45). Um bean único deixa o timeout de
     * readiness testável e, se preciso, mockável; produção usa o relógio do SO.
     */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

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

    /** Departamento médico (issue #54). */
    @Bean
    fun treatSquadUseCase(
        clubRepo: ClubRepository,
    ): TreatSquadUseCase = TreatSquadService(clubRepo)

    @Bean
    fun playRoundUseCase(
        clubRepo: ClubRepository,
        leagueRepo: LeagueRepository,
        readinessRepo: RoundReadinessRepository,
        simulator: MatchSimulator,
        // MeterRegistry é provido pelo actuator; injetado para o timer da
        // simulação (issue #44). Fora do Spring, o service usa um registry isolado.
        meterRegistry: MeterRegistry,
    ): PlayRoundUseCase = PlayRoundService(clubRepo, leagueRepo, readinessRepo, simulator, meterRegistry)

    @Bean
    fun roundReadinessUseCase(
        leagueRepo: LeagueRepository,
        userRepo: UserRepository,
        readinessRepo: RoundReadinessRepository,
        readinessProps: RoundReadinessProperties,
        clock: Clock,
    ): RoundReadinessUseCase =
        RoundReadinessService(leagueRepo, userRepo, readinessRepo, readinessProps.timeout, clock)

    @Bean
    fun streamMatchUseCase(
        clubRepo: ClubRepository,
        leagueRepo: LeagueRepository,
        simulator: MatchSimulator,
        meterRegistry: MeterRegistry,
    ): StreamMatchUseCase = PlayMatchService(clubRepo, leagueRepo, simulator, meterRegistry)

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
