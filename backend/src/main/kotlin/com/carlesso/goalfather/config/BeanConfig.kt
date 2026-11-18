package com.carlesso.goalfather.config

import com.carlesso.goalfather.application.port.`in`.BoardUseCase
import com.carlesso.goalfather.application.port.`in`.CalendarUseCase
import com.carlesso.goalfather.application.port.`in`.ContinentalUseCase
import com.carlesso.goalfather.application.port.`in`.CompetitionQueryUseCase
import com.carlesso.goalfather.application.port.`in`.BuyPlayerUseCase
import com.carlesso.goalfather.application.port.`in`.ClaimClubUseCase
import com.carlesso.goalfather.application.port.`in`.ExpandStadiumUseCase
import com.carlesso.goalfather.application.port.`in`.LoginUseCase
import com.carlesso.goalfather.application.port.`in`.NationalTeamClock
import com.carlesso.goalfather.application.port.`in`.PlayRoundUseCase
import com.carlesso.goalfather.application.port.`in`.RegisterUserUseCase
import com.carlesso.goalfather.application.port.`in`.RoundReadinessUseCase
import com.carlesso.goalfather.application.port.`in`.SaveLineupUseCase
import com.carlesso.goalfather.application.port.`in`.SeasonHistoryUseCase
import com.carlesso.goalfather.application.port.`in`.SellPlayerUseCase
import com.carlesso.goalfather.application.port.`in`.SetTicketPriceUseCase
import com.carlesso.goalfather.application.port.`in`.SetTrainingFocusUseCase
import com.carlesso.goalfather.application.port.`in`.StreamMatchUseCase
import com.carlesso.goalfather.application.port.`in`.TransferOfferUseCase
import com.carlesso.goalfather.application.port.`in`.TreatSquadUseCase
import com.carlesso.goalfather.application.port.`in`.WorldOnboardingUseCase
import com.carlesso.goalfather.application.port.out.ClubClaimRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.application.port.out.ContinentalRepository
import com.carlesso.goalfather.application.port.out.CupRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.application.port.out.PasswordHasher
import com.carlesso.goalfather.application.port.out.PlayerRepository
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.application.port.out.SeasonHistoryRepository
import com.carlesso.goalfather.application.port.out.TransferOfferRepository
import com.carlesso.goalfather.application.port.out.UserRepository
import com.carlesso.goalfather.application.service.BoardService
import com.carlesso.goalfather.application.service.BuyPlayerService
import com.carlesso.goalfather.application.service.CalendarService
import com.carlesso.goalfather.application.service.ContinentalService
import com.carlesso.goalfather.application.service.ClaimClubService
import com.carlesso.goalfather.application.service.CompetitionQueryService
import com.carlesso.goalfather.application.service.ExpandStadiumService
import com.carlesso.goalfather.application.service.LoginService
import com.carlesso.goalfather.application.service.PlayMatchService
import com.carlesso.goalfather.application.service.PlayRoundService
import com.carlesso.goalfather.application.service.RoundFinalizationGate
import com.carlesso.goalfather.application.service.RegisterUserService
import com.carlesso.goalfather.application.service.RoundReadinessService
import com.carlesso.goalfather.application.service.SaveLineupService
import com.carlesso.goalfather.application.service.SeasonHistoryService
import com.carlesso.goalfather.application.service.SellPlayerService
import com.carlesso.goalfather.application.service.SetTicketPriceService
import com.carlesso.goalfather.application.service.SetTrainingFocusService
import com.carlesso.goalfather.application.service.TransferOfferService
import com.carlesso.goalfather.application.service.TreatSquadService
import com.carlesso.goalfather.application.service.WorldOnboardingService
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
        catalogRepo: CompetitionCatalogRepository,
    ): SaveLineupUseCase = SaveLineupService(clubRepo, catalogRepo)

    /** Departamento médico (issue #54). */
    @Bean
    fun treatSquadUseCase(
        clubRepo: ClubRepository,
    ): TreatSquadUseCase = TreatSquadService(clubRepo)

    /** Foco de treino da semana (issue #58). */
    @Bean
    fun setTrainingFocusUseCase(
        clubRepo: ClubRepository,
    ): SetTrainingFocusUseCase = SetTrainingFocusService(clubRepo)

    /** Preço do ingresso do estádio (issue #59). */
    @Bean
    fun setTicketPriceUseCase(
        clubRepo: ClubRepository,
    ): SetTicketPriceUseCase = SetTicketPriceService(clubRepo)

    /** Ampliação do estádio (issue #79). */
    @Bean
    fun expandStadiumUseCase(
        clubRepo: ClubRepository,
    ): ExpandStadiumUseCase = ExpandStadiumService(clubRepo)

    @Bean
    fun playRoundUseCase(
        clubRepo: ClubRepository,
        leagueRepo: LeagueRepository,
        readinessRepo: RoundReadinessRepository,
        // Virada de temporada (issue #55): mercado envelhece e aposentado sai do banco.
        marketRepo: MarketRepository,
        playerRepo: PlayerRepository,
        // Histórico da liga (issue #60): snapshot da temporada antes do reset.
        historyRepo: SeasonHistoryRepository,
        // Copa mata-mata (issue #61): sorteio na virada, avanço a cada fase.
        cupRepo: CupRepository,
        // Diretoria (issue #62): demissão devolve o clube à IA.
        claimRepo: ClubClaimRepository,
        // Propostas entre técnicos (issue #63): o prazo anda a cada rodada.
        offerRepo: TransferOfferRepository,
        // Catálogo de competições do save (issue #131).
        catalogRepo: CompetitionCatalogRepository,
        simulator: MatchSimulator,
        // MeterRegistry é provido pelo actuator; injetado para o timer da
        // simulação (issue #44). Fora do Spring, o service usa um registry isolado.
        meterRegistry: MeterRegistry,
        // Portão entre a finalização de encaixes e o reset do profile e2e.
        finalizationGate: RoundFinalizationGate,
        // Continentais (issue #132): grupos, mata-mata e o sorteio da edição seguinte.
        continentalRepo: ContinentalRepository,
        // Datas FIFA (issue #134): convocações e jogos de seleção.
        nationalTeams: NationalTeamClock,
    ): PlayRoundUseCase = PlayRoundService(
        clubRepo,
        leagueRepo,
        readinessRepo,
        marketRepo,
        playerRepo,
        historyRepo,
        cupRepo,
        claimRepo,
        offerRepo,
        catalogRepo,
        simulator,
        meterRegistry,
        finalizationGate = finalizationGate,
        continentalRepo = continentalRepo,
        nationalTeams = nationalTeams,
    )

    /**
     * Um portão só para o processo: o `PlayRoundService` finaliza encaixes com
     * ele e o `E2eStateReset` (profile e2e) o fecha antes de recriar a liga.
     */
    @Bean
    fun roundFinalizationGate(): RoundFinalizationGate = RoundFinalizationGate()

    /** Propostas de transferência entre técnicos humanos (issue #63). */
    @Bean
    fun transferOfferUseCase(
        offerRepo: TransferOfferRepository,
        clubRepo: ClubRepository,
        leagueRepo: LeagueRepository,
    ): TransferOfferUseCase = TransferOfferService(offerRepo, clubRepo, leagueRepo)

    /** Painel da diretoria: meta da temporada e confiança no técnico (issue #62). */
    @Bean
    fun boardUseCase(
        clubRepo: ClubRepository,
        leagueRepo: LeagueRepository,
    ): BoardUseCase = BoardService(clubRepo, leagueRepo)

    /** Onboarding país → liga → clube sobre o mundo do save (issue #131). */
    @Bean
    fun worldOnboardingUseCase(
        catalog: CompetitionCatalogRepository,
        clubRepo: ClubRepository,
        leagueRepo: LeagueRepository,
    ): WorldOnboardingUseCase = WorldOnboardingService(catalog, clubRepo, leagueRepo)

    /** Navegação pelo mundo: competições e calendário do clube (issue #133). */
    @Bean
    fun competitionQueryUseCase(
        catalog: CompetitionCatalogRepository,
        clubRepo: ClubRepository,
        leagueRepo: LeagueRepository,
        cupRepo: CupRepository,
        continentalRepo: ContinentalRepository,
    ): CompetitionQueryUseCase = CompetitionQueryService(catalog, clubRepo, leagueRepo, cupRepo, continentalRepo)

    /** Leitura do histórico de temporadas e da carreira do técnico (issue #60). */
    @Bean
    fun seasonHistoryUseCase(
        historyRepo: SeasonHistoryRepository,
    ): SeasonHistoryUseCase = SeasonHistoryService(historyRepo)

    @Bean
    fun roundReadinessUseCase(
        leagueRepo: LeagueRepository,
        userRepo: UserRepository,
        readinessRepo: RoundReadinessRepository,
        readinessProps: RoundReadinessProperties,
        clock: Clock,
    ): RoundReadinessUseCase =
        RoundReadinessService(leagueRepo, userRepo, readinessRepo, readinessProps.timeout, clock)

    /** Relógio do jogo: encaixe corrente e próximo jogo do clube (issue #125). */
    @Bean
    fun calendarUseCase(
        leagueRepo: LeagueRepository,
        clubRepo: ClubRepository,
        cupRepo: CupRepository,
        catalogRepo: CompetitionCatalogRepository,
        continentalRepo: ContinentalRepository,
    ): CalendarUseCase = CalendarService(leagueRepo, clubRepo, cupRepo, catalogRepo, continentalRepo)

    /** Leitura da Libertadores (issue #132): grupos com tabela e o mata-mata. */
    @Bean
    fun continentalUseCase(
        continentalRepo: ContinentalRepository,
        catalogRepo: CompetitionCatalogRepository,
    ): ContinentalUseCase = ContinentalService(continentalRepo, catalogRepo)

    @Bean
    fun streamMatchUseCase(
        clubRepo: ClubRepository,
        leagueRepo: LeagueRepository,
        catalogRepo: CompetitionCatalogRepository,
        simulator: MatchSimulator,
        meterRegistry: MeterRegistry,
    ): StreamMatchUseCase = PlayMatchService(clubRepo, leagueRepo, catalogRepo, simulator, meterRegistry)

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
