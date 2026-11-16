package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.metrics.GoalfatherMetrics
import com.carlesso.goalfather.application.port.`in`.PlayRoundUseCase
import com.carlesso.goalfather.application.port.out.ClubClaimRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.CompetitionCatalogRepository
import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.Discipline
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.leagueOf
import com.carlesso.goalfather.domain.rules.SeasonPlan
import com.carlesso.goalfather.domain.rules.drawNationalCup
import com.carlesso.goalfather.domain.rules.matchRulesOf
import com.carlesso.goalfather.domain.rules.nationalCupDrawSeed
import com.carlesso.goalfather.domain.rules.ofCountry
import com.carlesso.goalfather.domain.rules.rulesFor
import com.carlesso.goalfather.domain.rules.tiebreakersOf
import com.carlesso.goalfather.domain.rules.worldPlan
import com.carlesso.goalfather.domain.rules.worldSeasons
import com.carlesso.goalfather.application.port.out.CupRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.application.port.out.PlayerRepository
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.application.port.out.SeasonHistoryRepository
import com.carlesso.goalfather.application.port.out.TransferOfferRepository
import com.carlesso.goalfather.domain.engine.MatchSetup
import com.carlesso.goalfather.domain.engine.MatchSimulator
import com.carlesso.goalfather.domain.engine.QuickMatchSimulator
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.event.TrainingReport
import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.BoardExpectation
import com.carlesso.goalfather.domain.model.BoardReview
import com.carlesso.goalfather.domain.model.BoardVerdict
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.CupClub
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.ExpiryReason
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.PenaltyScore
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Retirement
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundFinance
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.sumOfMoney
import com.carlesso.goalfather.domain.model.teamStrength
import com.carlesso.goalfather.domain.rules.AgingOutcome
import com.carlesso.goalfather.domain.rules.ageOneSeason
import com.carlesso.goalfather.domain.rules.afterRound
import com.carlesso.goalfather.domain.rules.ageSquadForSeason
import com.carlesso.goalfather.domain.rules.applyPromotionRelegation
import com.carlesso.goalfather.domain.rules.applyRoundFitness
import com.carlesso.goalfather.domain.rules.applyRoundToStandings
import com.carlesso.goalfather.domain.rules.boardExpectations
import com.carlesso.goalfather.domain.rules.cashDelta
import com.carlesso.goalfather.domain.rules.decidesOnPenalties
import com.carlesso.goalfather.domain.rules.fitnessSeed
import com.carlesso.goalfather.domain.rules.gate
import com.carlesso.goalfather.domain.rules.isSalaryDate
import com.carlesso.goalfather.domain.rules.marketAgingSeed
import com.carlesso.goalfather.domain.rules.recordRound
import com.carlesso.goalfather.domain.rules.seasonBoardReviews
import com.carlesso.goalfather.domain.rules.MatchCards
import com.carlesso.goalfather.domain.rules.afterMatchIn
import com.carlesso.goalfather.domain.rules.competitionByClub
import com.carlesso.goalfather.domain.rules.startingLineupIn
import com.carlesso.goalfather.domain.rules.withCleanRecord
import com.carlesso.goalfather.domain.rules.seasonRecordOf
import com.carlesso.goalfather.domain.rules.seasonStandings
import com.carlesso.goalfather.domain.rules.train
import com.carlesso.goalfather.domain.rules.trainingSeed
import com.carlesso.goalfather.domain.rules.toDismissal
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Implementação de `PlayRoundUseCase`. Orquestra a engine + repos para
 * simular TODAS as partidas de uma rodada e emitir um stream
 * multiplexado de `RoundEvent`.
 *
 * Determinismo: cada partida usa `matchId` como seed do `Random`.
 * Mesmo `matchId` → mesma sequência. Útil para testes E2E e debug.
 *
 * Espelha exatamente o handler MSW `/ws/round/:n` em
 * `frontend/src/mocks/handlers.ts`. Quando este service rodar dentro
 * do Spring (Fase 3), o handler MSW pode ser desligado e a UI
 * continua igual.
 *
 * **Copa (issue #61).** Uma data do calendário é rodada de liga OU fase de
 * copa (`Round.stage`), e as duas passam por ESTE mesmo fluxo — prontidão,
 * claim `finishRound`, stream e efeitos. Não existe um segundo mecanismo de
 * sincronização para a copa, que é o ponto de atenção multiplayer da issue.
 * O que muda numa data de copa: empate vai aos pênaltis, a tabela não mexe,
 * a folha não é cobrada e o vencedor de cada confronto leva o prêmio da fase.
 *
 * **Diretoria (issue #62).** Cada rodada de LIGA mexe na confiança da diretoria
 * em cada técnico (posição × meta), dentro da mesma gravação por clube dos
 * demais efeitos. Na virada, a diretoria dá o veredito — bônus, permanência ou
 * demissão — e fixa a meta da temporada nova. A engine de partida não muda.
 *
 * **Propostas entre técnicos (issue #63).** Cada encaixe encerrado anda um
 * encaixe no prazo das propostas pendentes; quem chega a zero expira.
 *
 * **Calendário semanal (issue #125).** O que se joga é um ENCAIXE (semana +
 * meio ou fim de semana), não mais uma "rodada" global: todos os jogos de todas
 * as competições marcados para ele. Liga e copa podem dividir o encaixe, então
 * tudo que dependia do tipo da rodada passou a olhar a PARTIDA — pênaltis,
 * tabela, folha, diretoria, chave da copa. Lesões andam só para quem jogou
 * (contam jogos do clube). O próximo encaixe sai do plano do mundo
 * (`worldPlan`), recalculado a cada fim de encaixe.
 *
 * **Mundo do MVP (issue #131).** As competições vêm do catálogo do save
 * (`CompetitionCatalogRepository`): cada liga com o seu formato, desempates,
 * disciplina e limite de estrangeiros, e cada país na SUA temporada (lida das
 * tabelas correntes). Quando todas as competições de um país terminam, a
 * virada é DAQUELE país — história, acesso/rebaixamento, envelhecimento,
 * diretoria, copa e tabelas novas — e o resto do mundo segue jogando.
 *
 * **Simulação rápida (issue #129).** Só jogo com técnico humano passa pela
 * engine completa e é transmitido lance a lance; os jogos só da IA vão para a
 * `QuickMatchSimulator`, em paralelo, e o stream leva apenas o desfecho deles.
 * A separação e o paralelismo moram no [MatchdaySimulator]; daqui para baixo
 * (claim, efeitos, virada) nada sabe qual engine rodou.
 *
 * **Suspensões e estrangeiros (issue #130).** Cada clube entra em campo com
 * `startingLineupIn(competição da partida)` — sem os suspensos dela e dentro do
 * limite de estrangeiros dela — e os cartões do jogo contam só para aquela
 * competição (`afterMatchIn`). As regras são funções puras de
 * `domain/rules/SuspensionRules.kt`; aqui só se escolhe a competição.
 */
class PlayRoundService(
    private val clubRepo: ClubRepository,
    private val leagueRepo: LeagueRepository,
    private val readinessRepo: RoundReadinessRepository,
    // Mercado e jogadores soltos só entram em cena na virada de temporada
    // (issue #55): quem está anunciado envelhece junto, e o aposentado sai do
    // banco em vez de virar linha órfã.
    private val marketRepo: MarketRepository,
    private val playerRepo: PlayerRepository,
    // Histórico da liga (issue #60): a virada de temporada grava o snapshot
    // ANTES de zerar tabela e estatísticas.
    private val historyRepo: SeasonHistoryRepository,
    // Copa mata-mata (issue #61): o sorteio sai na virada de temporada e a
    // chave avança a cada data de copa encerrada.
    private val cupRepo: CupRepository,
    // Diretoria (issue #62): o técnico demitido devolve o clube à IA — o claim
    // da issue #19 na direção inversa.
    private val claimRepo: ClubClaimRepository,
    // Propostas entre técnicos (issue #63): o prazo delas anda com o relógio
    // do jogo, uma rodada a cada rodada encerrada.
    private val offerRepo: TransferOfferRepository,
    // Catálogo de competições do save (issue #131): quais ligas e copas o mundo
    // tem, com formato, desempates, disciplina e limite de estrangeiros.
    private val catalogRepo: CompetitionCatalogRepository,
    private val simulator: MatchSimulator = MatchSimulator(),
    // Default = registry isolado: mantém os testes existentes construindo o
    // service sem passar métrica; em produção o BeanConfig injeta o registry real
    // (mesmo padrão do `Random`/`MatchSimulator` já injetáveis).
    private val meterRegistry: MeterRegistry = SimpleMeterRegistry(),
    // Simulação rápida dos jogos sem técnico humano (issue #129). O dispatcher
    // é injetável para teste; em produção, o pool de CPU do `Dispatchers.Default`.
    quickSimulator: QuickMatchSimulator = QuickMatchSimulator(),
    simulationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    // Claim + efeitos de um encaixe rodam com uma licença deste portão: o
    // reset do profile e2e espera as finalizações em curso antes de apagar a
    // liga. Default próprio para os testes que montam o service à mão.
    private val finalizationGate: RoundFinalizationGate = RoundFinalizationGate(),
) : PlayRoundUseCase {

    private val simulationTimer: Timer = meterRegistry.timer(GoalfatherMetrics.ROUND_SIMULATION)

    // Parâmetros de construtor SEM `val` só existem no `init`/inicializadores:
    // `quickSimulator` e `simulationDispatcher` não viram propriedade do
    // service — só montam o colaborador que de fato os usa.
    private val matchdaySimulator =
        MatchdaySimulator(simulator, quickSimulator, meterRegistry, simulationDispatcher)

    override fun stream(matchday: Matchday): Flow<RoundEvent> = flow {
        val round = leagueRepo.findRound(matchday)
            ?: throw IllegalArgumentException("Encaixe ${matchday.key} não encontrado")

        // Gate de prontidão no servidor (issue #151): só simula encaixe que o
        // `RoundReadinessService.start()` já liberou (`Scheduled → InProgress`,
        // com todos prontos ou timeout vencido). Antes, o gate só existia no
        // `POST /round/play`, e qualquer cliente que abrisse o WS direto jogava
        // o encaixe sozinho. Desfecho como VALOR (um `RoundEvent`), não
        // exception: "ainda não liberado" é regra de negócio, não falha.
        //
        // Fica ANTES da simulação de propósito: nenhum trabalho de engine nem
        // amostra de métrica para um stream que não vai jogar. Sem claim novo:
        // `start` grava `InProgress` antes de responder ao `play`, então o WS
        // que abre depois já lê o status certo.
        if (!round.isReleased) {
            emit(RoundEvent.RoundNotStarted)
            return@flow
        }

        // Clubes que entraram em campo nesta rodada — guardados para
        // persistir as estatísticas dos jogadores ao final (issue #2).
        val involvedClubs = mutableMapOf<Long, Club>()
        // As regras de cada competição (limite de estrangeiros, disciplina,
        // desempates) vêm do catálogo do save (issue #131).
        val catalog = catalogRepo.findAll()

        // Fase 1 — I/O: busca os clubes e monta os `MatchSetup` ANTES de ligar o
        // cronômetro (issue #51). `map` é `inline`, então as chamadas `suspend`
        // a `findById` valem aqui dentro sem precisar de `async`/coroutine extra.
        val setups = round.matches.map { match ->
            val home = clubRepo.findById(match.homeClubId)
            val away = clubRepo.findById(match.awayClubId)
            home?.let { involvedClubs[it.id.value] = it }
            away?.let { involvedClubs[it.id.value] = it }

            val setup = MatchSetup(
                // Sem suspensos daquela competição e dentro do limite de
                // estrangeiros dela (issue #130).
                home = home?.startingLineupIn(catalog.matchRulesOf(match.competition)) ?: emptyLineup(),
                away = away?.startingLineupIn(catalog.matchRulesOf(match.competition)) ?: emptyLineup(),
                homeName = match.homeClubName,
                awayName = match.awayClubName,
                // Pênaltis pela perna do jogo de copa (issues #61/#131): jogo
                // único empatado, ou volta com agregado empatado. Por PARTIDA
                // (issue #125): o mesmo encaixe pode ter liga e copa.
                decidesOnPenalties = match::decidesOnPenalties,
                homeClubId = match.homeClubId,
                awayClubId = match.awayClubId,
            )
            // Com técnico humano em campo → engine completa e stream; jogo só
            // da IA → simulação rápida, sem transmissão (issue #129).
            MatchFixture(match, setup, simulationModeOf(home, away))
        }

        // Fase 2 — engine: o timer cobre SÓ `simulator.simulate(...)` de todas as
        // partidas, espelhando o `PlayMatchService`. Nenhum I/O de repositório
        // fica aqui dentro: como `Timer.start/stop` mede wall time, um
        // `findById` no meio contaria a latência do banco como "simulação".
        // Também não fica no WS handler: lá há delays de ~80ms/minuto para dar
        // sensação de "ao vivo" — cronometrar lá mediria a animação.
        // Reconexão (replay de rodada já `Finished`) também gera amostra, de
        // propósito: a engine roda de novo para re-emitir os eventos, então é
        // trabalho de simulação real e entra na métrica.
        //
        // Desde a issue #129 o trabalho em si mora no [MatchdaySimulator]: os
        // jogos com humano passam pela engine completa, os demais pela rápida,
        // em paralelo. Este timer segue medindo o encaixe INTEIRO.
        val sample = Timer.start(meterRegistry)
        val simulated = matchdaySimulator.simulate(setups)
        sample.stop(simulationTimer)

        // Só o que é transmitido: o feed inteiro dos jogos com humano e apenas
        // o desfecho (pênaltis + FullTime, minuto 90) dos rápidos.
        // Estável por minuto: eventos de minuto igual saem juntos
        // (sensação de "vários estádios ao mesmo tempo").
        val tagged = simulated
            .flatMap { sim -> sim.broadcast.map { sim.match.matchId to it } }
            .sortedBy { it.second.minute }
        for ((matchId, event) in tagged) {
            emit(RoundEvent.MatchUpdate(matchId, event))
        }

        // Idempotência (replay): se a rodada JÁ consta finalizada, este stream é
        // só uma reconexão ao WS. Re-emitimos os eventos para visualização, mas
        // NÃO re-aplicamos estatísticas/caixa nem geramos a próxima rodada — caso
        // contrário gols, cartões e bilheteria seriam contados em dobro.
        if (round.status == RoundStatus.Finished) {
            emit(RoundEvent.RoundFinished(leagueRepo.currentStandings()))
            return@flow
        }

        // Ponto de serialização (issues #20 e #46): dois WS da mesma rodada — no
        // mesmo processo OU em instâncias diferentes do backend — podem chegar
        // aqui ao mesmo tempo. `finishRound` faz a transição `→ Finished` sob
        // lock otimista (`@Version` na rodada): exatamente UM chamador recebe
        // `true` e fica autorizado a aplicar os efeitos. O perdedor não escreve
        // nada e cai no mesmo replay da reconexão.
        //
        // Antes disto era um `Mutex` in-JVM, válido só com instância única: dois
        // nós liam `status != Finished` e dobravam caixa, estatísticas e tabela.
        val byMatch = simulated.associateBy { it.match.matchId }
        val finishedMatches = round.matches.map { m -> m.finish(byMatch[m.matchId]?.score, byMatch[m.matchId]?.penalties) }
        val finishedRound = round.copy(matches = finishedMatches, status = RoundStatus.Finished)

        // Claim e efeitos dentro de uma licença do portão de finalização: o
        // reset do profile e2e espera este bloco terminar antes de apagar a
        // liga (ver [RoundFinalizationGate]). `null` = perdemos o claim.
        val tailEvents: List<RoundEvent>? = finalizationGate.finalizing {
            if (!leagueRepo.finishRound(finishedRound)) return@finalizing null

            // Vencemos a corrida: aplicamos os efeitos exatamente uma vez.
            //
            // Ordem (claim ANTES dos efeitos) troca a semântica de *at-least-once*
            // para *at-most-once*: nunca dobramos caixa/estatísticas, mas uma
            // interrupção entre o claim e o fim da persistência deixaria a rodada
            // `Finished` com efeitos parciais — e como todo stream seguinte cai no
            // replay, a liga travaria (a próxima rodada nunca seria gerada). Por
            // isso o bloco roda em `NonCancellable`: nenhum cancelamento do coletor
            // (shutdown, um adapter que cancele o Flow) pode rasgar a finalização no
            // meio. O `RoundWebSocketHandler` nem cancela mais ao fechar a aba: a
            // rodada segue até o fim sem pacing (revisão do PR #92).
            //
            // NonCancellable NÃO cobre crash de processo entre o claim e o fim —
            // esse resíduo de durabilidade fica documentado em docs/ARQUITETURA.md
            // (fechá-lo exige efeitos + claim na MESMA transação, hoje inviável com
            // os ports `suspend` por agregado). É estreito: dezenas de ms.
            //
            // Enquanto os efeitos não são gravados, um leitor concorrente vê a
            // tabela sem os pontos desta rodada; uma reconexão posterior já devolve
            // a definitiva (não vale para o caso de crash acima).
            withContext(NonCancellable) {
                // Efeitos leem a SÚMULA de cada jogo (feed completo ou destaques do
                // rápido), não o que foi transmitido.
                finalizeRound(
                    round,
                    finishedRound,
                    simulated.flatMap { it.sheet },
                    involvedClubs.values,
                    catalog,
                    simulated.cardsByClub(),
                )
            }
        }
        if (tailEvents == null) {
            emit(RoundEvent.RoundFinished(leagueRepo.currentStandings()))
            return@flow
        }
        // Emissão fora do NonCancellable: `emit` exige o mesmo contexto do
        // coletor (invariante do Flow), que `withContext` quebraria.
        for (event in tailEvents) emit(event)
    }

    /**
     * Aplica os efeitos da rodada vencida e devolve os eventos de cauda na
     * ordem de emissão (SeasonFinished, quando há, antes do RoundFinished).
     * Roda inteiro dentro de `NonCancellable` — não emite nada, só persiste e
     * devolve, para que o chamador emita no contexto do Flow.
     */
    private suspend fun finalizeRound(
        round: Round,
        finishedRound: Round,
        events: List<MatchEvent>,
        involvedClubs: Collection<Club>,
        catalog: List<GameCompetition>,
        clubCards: Map<ClubId, Discipline>,
    ): List<RoundEvent> {
        // A temporada corrente de cada país sai das tabelas correntes (issue
        // #131): o Brasil de 2027 convive com a Europa de 2026/27.
        val currentStandings = leagueRepo.currentStandings()
        val seasons = currentStandings.worldSeasons()

        // Copas nacionais (issues #61/#131). Os placares (com os pênaltis) vão
        // para a chave ANTES das finanças: é o resultado que decide quem leva o
        // prêmio da fase, e o prêmio entra no caixa na mesma gravação da
        // bilheteria. Cada copa recebe só as partidas DELA.
        val cups = cupRepo.currentCups(catalog, seasons)
        val cupOutcomes = cups.mapNotNull { (id, cup) ->
            val own = finishedRound.matches.filter { it.competition == id && it.isKnockout }
            if (own.isEmpty()) null else id to cup.recordRound(finishedRound.copy(matches = own))
        }.toMap()
        cupOutcomes.values.forEach { cupRepo.save(it.cup) }
        val cupsNow = cups + cupOutcomes.mapValues { (_, outcome) -> outcome.cup }
        val prizes = cupOutcomes.values.flatMap { it.prizes.entries }.associate { it.key to it.value }

        // Regras de cada clube no encaixe: a competição que ele jogou, com o
        // limite de estrangeiros e a disciplina do catálogo (issue #130/#131).
        val competitionOf = round.competitionByClub().mapValues { (_, id) -> catalog.matchRulesOf(id) }

        // Calcula o balanço financeiro da rodada — bilheteria (mandante),
        // folha salarial em rodadas de "mês" (issue #4) e prêmio da copa.
        val finances = computeFinances(round, involvedClubs, prizes, competitionOf)

        // Acumula estatísticas dos jogadores (issue #2), aplica o caixa
        // (issue #4) e o desgaste da rodada (issue #54) em uma única gravação
        // por clube.
        //
        // A fadiga alcança TODOS os clubes, não só os que entraram em campo:
        // quem folga na rodada precisa recuperar stamina. `ifEmpty` cai para os
        // clubes envolvidos se o repositório vier vazio, preservando o
        // comportamento dos testes que só stubam `findById`.
        val clubs = clubRepo.findAll().ifEmpty { involvedClubs.toList() }

        // O MESMO encaixe é aplicado à tabela de cada liga do mundo: só os jogos
        // daquela liga contam, com os desempates DELA (issue #131). Calculada
        // ANTES dos efeitos por clube porque a confiança da diretoria (issue
        // #62) é função da posição JÁ com esta rodada — e entra na mesma
        // gravação do clube que o caixa e o elenco.
        val newStandings = currentStandings
            .map { applyRoundToStandings(finishedRound, it, catalog.tiebreakersOf(it.competitionId), clubCards) }
        val boardAfterRound = boardEvaluator(finishedRound, newStandings, clubs)

        val training = persistRoundEffects(
            round.matchday,
            events,
            clubs,
            involvedClubs,
            finances,
            boardAfterRound,
            competitionOf,
        )
        newStandings.forEach { leagueRepo.saveStandings(it) }

        // Encaixe consumido: zera a prontidão para o próximo começar limpo
        // (issue #20). Os técnicos sinalizam de novo no encaixe seguinte.
        readinessRepo.reset(round.matchday)

        // Avança o relógio (issues #125/#131). O plano do mundo (as ligas e
        // copas de cada país, na temporada corrente de cada um) é recalculado
        // — é função pura do catálogo, dos clubes, das temporadas e das copas.
        // Os países cujas competições terminaram TODAS neste encaixe viram,
        // cada um no seu tempo: o Brasil em dezembro, a Europa em maio. O resto
        // do mundo segue jogando.
        val plan = worldPlan(catalog, clubs, seasons, cupsNow)
        val ending = plan.countriesEndingBy(round.matchday)

        val tail = mutableListOf<RoundEvent>()
        val nextSeasons = seasons + ending.associateWith { seasons.getValue(it) + 1 }
        // Clubes já na temporada nova (divisão nova), por país que virou.
        val turnedClubs = mutableListOf<Club>()
        for (country in ending.sortedWith(HOME_FIRST)) {
            val endedSeason = seasons.getValue(country)
            val turnover = startNextSeason(
                country = country,
                endedSeason = endedSeason,
                finalStandings = newStandings.filter { it.country.code == country },
                cupChampion = catalog.ofCountry(country).firstNotNullOfOrNull { cupsNow[it.id]?.champion },
                catalog = catalog,
            )
            tail += turnover.event
            turnedClubs += turnover.clubs
        }
        // O mercado é do mundo, não de um país: envelhece UMA vez por ano,
        // quando o primeiro país abre a temporada daquele ano (o Brasil, em
        // janeiro; a Europa abre a mesma "safra" em agosto e não envelhece de
        // novo). Ver `docs/ARQUITETURA.md`.
        val newestSeason = seasons.values.maxOrNull()
        val openedYear = ending.map { nextSeasons.getValue(it) }.maxOrNull()
        if (openedYear != null && newestSeason != null && openedYear > newestSeason) ageMarket(openedYear)

        // Gera o próximo encaixe antes de sinalizar RoundFinished, para que
        // `getCurrentRound` já encontre uma rodada Scheduled. Com virada, o
        // plano é refeito com as temporadas, divisões e copas novas.
        val (nextPlan, nextClubs) = if (ending.isEmpty()) {
            plan to clubs
        } else {
            // Só id, nome, país e divisão importam para o plano: os clubes dos
            // países que viraram saem da própria virada (divisão nova), os
            // demais seguem como lidos acima.
            val world = clubs.filterNot { it.country.code in ending } + turnedClubs
            worldPlan(catalog, world, nextSeasons, cupRepo.currentCups(catalog, nextSeasons)) to world
        }
        checkNotInPast(nextPlan, ending, round.matchday)
        nextPlan.nextAfter(round.matchday)?.let { leagueRepo.saveRound(nextPlan.roundAt(it, nextClubs)) }

        // Prazo das propostas entre técnicos (issue #63): uma rodada a menos
        // para cada pendente, e quem chega a zero expira. Aqui, depois do claim
        // da rodada (issue #46), o relógio anda exatamente uma vez por rodada.
        // DEPOIS do agendamento de propósito (revisão do PR #94): é um efeito
        // secundário, e uma falha nele antes de `scheduleRound` deixaria a
        // rodada `Finished` sem sucessora — a liga travaria por causa de uma
        // proposta. Aqui, no pior caso, o prazo anda na rodada seguinte.
        advanceOfferDeadlines()

        // RoundFinished é sempre o último evento do stream (sinal de "rodada
        // encerrada"); os SeasonFinished, quando há, vêm logo antes dele —
        // um por país que virou.
        tail += RoundEvent.RoundFinished(newStandings, finances, training)
        return tail
    }

    /**
     * A edição nova de um país nunca cai no passado: as janelas (`SeasonSpan`)
     * de duas edições seguidas não se sobrepõem. Se cair, o catálogo está mal
     * configurado — e jogar no passado pularia rodadas em silêncio.
     */
    private fun checkNotInPast(plan: SeasonPlan, turned: Set<CountryCode>, now: Matchday) {
        if (turned.isEmpty()) return
        val stale = plan.schedule.rounds.filter {
            plan.competitions.getValue(it.competition).game.country.code in turned && it.matchday <= now
        }
        check(stale.isEmpty()) { "Temporada nova marcada no passado (antes de ${now.key}): ${stale.take(3)}" }
    }

    /**
     * Encerra a temporada de UM país e abre a seguinte (issues #11/#131):
     * grava a história, aplica acesso/rebaixamento às tabelas finais (só o
     * Brasil tem; as europeias são fechadas no MVP), envelhece e zera as
     * estatísticas dos clubes DAQUELE país, renova a diretoria, sorteia a copa
     * nova e grava as tabelas novas. As encerradas NÃO são apagadas (PK por
     * competição + temporada) e continuam consultáveis via
     * `GET /api/league/standings?season=`. Devolve o `SeasonFinished` para o
     * chamador emitir (nada é emitido aqui — roda sob `NonCancellable`).
     *
     * **A ORDEM AQUI É REGRA, não estilo.** O snapshot é a PRIMEIRA coisa que
     * acontece porque `startNextSeasonClubs` zera `goals` logo abaixo: montar
     * o record depois daria um artilheiro sempre nulo. Os clubes são lidos uma
     * vez só e atravessam as duas etapas — a leitura acontece depois de
     * `persistRoundEffects`, então o caixa e os gols da última rodada já estão
     * no estado que a história registra.
     */
    private suspend fun startNextSeason(
        country: CountryCode,
        endedSeason: Int,
        finalStandings: List<Standings>,
        cupChampion: CupClub?,
        catalog: List<GameCompetition>,
    ): CountryTurnover {
        val countryCatalog = catalog.ofCountry(country)
        val clubsAtSeasonEnd = clubRepo.findAll().filter { it.country.code == country }

        // História ANTES do reset (issue #60), uma por (temporada, país). O
        // `append` devolve `false` se ela já tinha record — ignorado de
        // propósito: quem chega aqui venceu o claim da rodada (issue #46),
        // então um `false` só pode vir de uma réplica retardatária, e o certo é
        // não sobrescrever a história.
        val record = seasonRecordOf(endedSeason, finalStandings, clubsAtSeasonEnd, cupChampion)
        historyRepo.append(record)

        val nextSeason = endedSeason + 1
        val rules = countryCatalog.rulesFor(finalStandings)
        val nextDivisions = applyPromotionRelegation(finalStandings, rules, countryCatalog)
        val (agedClubs, retirements) = startNextSeasonClubs(nextSeason, nextDivisions, clubsAtSeasonEnd)
        // Diretoria (issue #62) DEPOIS do envelhecimento e da troca de divisão:
        // a meta da temporada nova é função do elenco que o clube vai ter e da
        // liga em que vai jogar, não dos que acabaram de sair.
        val (clubs, boardReviews) = applyBoardTurnover(country, endedSeason, nextSeason, finalStandings, agedClubs)
        // Sorteio da copa nova do país (issue #61), com os clubes JÁ nas
        // divisões novas — o calendário da temporada reserva os encaixes dela.
        drawNationalCups(nextSeason, countryCatalog, clubs)
        seasonStandings(nextSeason, countryCatalog, clubs).forEach { leagueRepo.saveStandings(it) }

        val event = RoundEvent.SeasonFinished(
            season = endedSeason,
            // Campeão = líder da elite do país, o mesmo que foi para a história:
            // uma fonte só evita evento e record discordarem sobre quem levou.
            champion = record.champion.row,
            standings = finalStandings,
            retirements = retirements,
            cupChampion = cupChampion,
            boardReviews = boardReviews,
        )
        return CountryTurnover(event, clubs)
    }

    /** O evento da virada de um país + os clubes dele já na temporada nova. */
    private data class CountryTurnover(
        val event: RoundEvent.SeasonFinished,
        val clubs: List<Club>,
    )

    /**
     * Veredito da diretoria sobre a temporada que acabou e meta da que começa
     * (issue #62), numa ÚNICA gravação por clube: o bônus de quem cumpriu a
     * meta entra no caixa como delta (mesma postura da rodada, issue #89) junto
     * da meta nova, com a confiança de volta ao valor inicial.
     *
     * A demissão vem depois, e só para clube com dono humano: o clube volta ao
     * pool da IA e o técnico fica sem clube, com a demissão registrada para a
     * tela de game over. Clube da IA "demitido" só troca de técnico em
     * silêncio — o veredito fica no balanço, sem efeito.
     *
     * Roda sob `NonCancellable`, como o resto da virada, e só para quem venceu
     * o claim da rodada (issue #46): o bônus é pago uma vez.
     */
    private suspend fun applyBoardTurnover(
        country: CountryCode,
        endedSeason: Int,
        nextSeason: Int,
        finalStandings: List<Standings>,
        clubs: List<Club>,
    ): BoardTurnover {
        val reviews = seasonBoardReviews(endedSeason, finalStandings, clubs)
        val verdicts = reviews.associate { it.clubId to it.verdict }
        val nextBoards = boardExpectations(nextSeason, clubs)

        val updated = clubs.map { club ->
            // `when` exaustivo sobre o `sealed` (e o `null` de quem não tinha
            // meta): um veredito novo no domínio quebra a compilação aqui.
            val bonus = when (val verdict = verdicts[club.id]) {
                is BoardVerdict.TargetMet -> verdict.bonusCents
                BoardVerdict.TargetMissed, BoardVerdict.Fired, null -> Money.ZERO
            }
            saveReapplying(club) { current ->
                current.copy(cash = current.cash + bonus, board = nextBoards[current.id])
            }
        }

        val owners = clubs.associate { it.id to it.ownerId }
        // `toDismissal()` só existe para o veredito `Fired`: o `mapNotNull`
        // filtra a demissão e monta o registro numa passada só.
        val dismissals = reviews.filter { owners[it.clubId] != null }.mapNotNull { it.toDismissal() }
        val released = mutableListOf<ClubId>()
        for (dismissal in dismissals) {
            if (claimRepo.release(dismissal.clubId, dismissal) != null) released += dismissal.clubId
        }
        // Propostas do técnico que saiu morrem com a saída dele (revisão do
        // PR #94), dos dois lados: o clube agora é da IA, que não negocia nem
        // paga, e quem assumir depois não herda negociação alheia.
        expireOffersOf(released)
        // O `release` muda dono e versão no banco: devolver a cópia de antes
        // dele deixaria clubes com `ownerId`/`version` velhos para quem vier
        // depois. Só relê quando houve demissão — o caso comum não paga a leitura.
        val clubsAfter = if (dismissals.isEmpty()) {
            updated
        } else {
            clubRepo.findAll().filter { it.country.code == country }.ifEmpty { updated }
        }
        return BoardTurnover(clubsAfter, reviews)
    }

    /** Expira por [ExpiryReason.MANAGER_LEFT] as pendentes que envolvem [clubIds]. */
    private suspend fun expireOffersOf(clubIds: Collection<ClubId>) {
        if (clubIds.isEmpty()) return
        offerRepo.findPending()
            .filter { offer -> clubIds.any(offer::involves) }
            .mapNotNull { it.expire(ExpiryReason.MANAGER_LEFT) }
            .forEach { offerRepo.saveIfPending(it) }
    }

    /** Clubes já com a meta nova + o balanço da diretoria da temporada encerrada. */
    private data class BoardTurnover(
        val clubs: List<Club>,
        val reviews: List<BoardReview>,
    )

    /**
     * Como a confiança da diretoria anda nesta rodada (issue #62), por clube.
     * Devolve uma função porque a avaliação acontece DENTRO do `saveReapplying`
     * de cada clube — sobre o clube relido, se houver conflito.
     *
     * Só rodada de LIGA mexe na confiança: a meta é de posição na tabela, e data
     * de copa não mexe na tabela. O `when` sobre o `sealed` força a decisão.
     *
     * E só nas divisões que JOGARAM: com divisões de tamanhos diferentes, a
     * menor encerra o turno antes e folga nas últimas datas — a tabela dela
     * parada não é desempenho novo, e reavaliá-la a cada data empurraria a
     * confiança (até a demissão) sem que o técnico entrasse em campo.
     *
     * Clube sem meta desta temporada (save anterior à issue) recebe a meta
     * agora, calculada da liga como ela está — `lazy` porque, no caso comum,
     * todo clube já tem a sua e o cálculo nem roda.
     */
    private fun boardEvaluator(
        round: Round,
        standings: List<Standings>,
        clubs: Collection<Club>,
    ): (Club) -> BoardExpectation? {
        // Ligas que jogaram neste encaixe (issue #125: o encaixe pode ter só
        // copa, ou liga e copa juntas; issue #131: ligas de vários países).
        // `when` exaustivo sobre o `sealed`: um terceiro tipo de jogo obriga a
        // decidir aqui se ele mexe na diretoria.
        val playedLeagues = round.matches.mapNotNullTo(HashSet()) { match ->
            when (match.stage) {
                is RoundStage.League -> match.competition
                is RoundStage.Cup -> null
            }
        }
        if (playedLeagues.isEmpty()) return Club::board
        val positions = standings
            .filter { it.competitionId in playedLeagues }
            .flatMap { it.rows }
            .associate { it.clubId to it.position }
        // A meta é da temporada do PAÍS do clube (issue #131).
        val seasons = standings.worldSeasons()
        val fallback by lazy {
            clubs.groupBy { it.country.code }
                .flatMap { (code, own) -> seasons[code]?.let { boardExpectations(it, own) }?.entries.orEmpty() }
                .associate { it.key to it.value }
        }
        // Função anônima (`fun(...)`) em vez de lambda: nela `return` sai da
        // própria função, sem rótulo — o idioma para corpo com saídas antecipadas.
        return fun(club: Club): BoardExpectation? {
            val position = positions[club.id] ?: return club.board
            val season = seasons[club.country.code] ?: return club.board
            val expectation = club.board?.takeIf { it.season == season }
                ?: fallback[club.id]
                ?: return club.board
            return expectation.afterRound(position)
        }
    }

    /**
     * Anda o prazo de cada proposta pendente (issue #63) e expira as que já
     * não têm o que negociar — o jogador saiu do elenco do vendedor (revisão do
     * PR #94). A regra é do domínio (`TransferOffer.advance`); a gravação é
     * condicional a a proposta ainda estar pendente, então um aceite ou recusa
     * que chegue no meio do caminho vence e o `false` daqui é ignorado.
     *
     * Os elencos são lidos DEPOIS dos efeitos e da virada (aposentadorias
     * incluídas) e só quando há proposta pendente — o caso comum não paga a
     * leitura extra.
     */
    private suspend fun advanceOfferDeadlines() {
        val pending = offerRepo.findPending()
        if (pending.isEmpty()) return
        val squadsBySeller = clubRepo.findAll().associate { club -> club.id to club.squad.mapTo(HashSet()) { it.id } }
        for (offer in pending) {
            val stillWithSeller = offer.playerId in squadsBySeller[offer.sellerClubId].orEmpty()
            offerRepo.saveIfPending(offer.advance(stillWithSeller))
        }
    }

    /**
     * Sorteia e grava as copas nacionais da temporada [season] de um país
     * (issues #61/#131), entre os clubes das ligas dele. Copa que o motor não
     * comporta não é sorteada — o país joga só as ligas. Seed por copa +
     * temporada: reprocessar a virada sorteia igual.
     */
    private suspend fun drawNationalCups(season: Int, countryCatalog: List<GameCompetition>, clubs: List<Club>) {
        val entrants = clubs.filter { countryCatalog.leagueOf(it) != null }
        for (game in countryCatalog.filter { it.kind == CompetitionKind.NationalCup }) {
            drawNationalCup(season, game, entrants, Random(nationalCupDrawSeed(season, game.id)))?.let { cupRepo.save(it) }
        }
    }

    /**
     * Prepara os clubes para a nova temporada numa ÚNICA gravação por clube:
     * envelhece o elenco e promove a base no lugar de quem se aposentou
     * (issue #55), zera gols/cartões, cura lesões e move quem subiu/desceu
     * para a nova divisão (issue #47). Recebe os clubes já relidos (com o caixa
     * pós-bilheteria) para não desfazer as finanças aplicadas na rodada.
     * Devolve o estado atualizado — que alimenta o calendário e as tabelas da
     * temporada nova — junto das aposentadorias, que viajam no `SeasonFinished`
     * para o técnico saber quem pendurou as chuteiras.
     *
     * O RNG do envelhecimento é semeado por `temporada + clube` — mesmo padrão
     * determinístico do desgaste (`fitnessSeed`) e da partida (`matchId`):
     * reprocessar a mesma virada com o mesmo elenco dá exatamente a mesma
     * evolução. O aposentado sai do `squad` (e da folha, que é somada dele) e é
     * APAGADO do banco: mantê-lo com `club_id = null` o deixaria indistinguível
     * de um agente livre.
     */
    private suspend fun startNextSeasonClubs(
        nextSeason: Int,
        nextDivisions: Map<ClubId, Division>,
        clubsAtSeasonEnd: List<Club>,
    ): SeasonTurnover {
        val clubs = mutableListOf<Club>()
        val retirements = mutableListOf<Retirement>()

        for (club in clubsAtSeasonEnd) {
            // Envelhecimento roda sobre o clube que estiver no banco NA HORA de
            // gravar (issue #89): se um técnico mexeu no clube no meio da virada,
            // o `saveReapplying` relê e refaz a conta. O RNG é semeado por
            // temporada + clube, então a releitura não muda o sorteio — e só as
            // aposentadorias da tentativa que gravou entram no resultado.
            var turnRetirements = emptyList<Retirement>()
            val updated = saveReapplying(club) { current ->
                val turn = ageSquadForSeason(current.squad, current.id, nextSeason)
                turnRetirements = turn.retirements

                // Pré-temporada: elenco volta inteiro e descansado (issue #54).
                val reset = turn.squad.map {
                    it.copy(
                        goals = 0,
                        yellowCards = 0,
                        redCards = 0,
                        availability = Availability.Available,
                        stamina = 100,
                    ).withCleanRecord() // sem amarelo pendente nem gancho (issue #130)
                }
                current.copy(
                    squad = reset,
                    division = nextDivisions[current.id] ?: current.division,
                )
            }
            retirements += turnRetirements
            clubs += updated
        }

        // Depois do save do clube: enquanto o aposentado ainda constava do
        // elenco, apagá-lo faria o `save` tentar reatribuir uma linha inexistente.
        playerRepo.deleteAll(retirements.map { it.retired.id })
        return SeasonTurnover(clubs, retirements)
    }

    /** Clubes prontos para a temporada nova + as aposentadorias da virada. */
    private data class SeasonTurnover(
        val clubs: List<Club>,
        val retirements: List<Retirement>,
    )

    /**
     * Envelhece quem está anunciado no mercado (issue #55). Sem isso a lista de
     * transferências seria um museu: enquanto todo elenco envelhece, os
     * anunciados ficariam eternamente com a idade do seed.
     *
     * Ninguém sobe da base aqui — não há clube para promover —, então o
     * aposentado simplesmente sai do mercado: `claim` remove a entrada (mesma
     * transição atômica da compra, issue #21) e a linha do jogador é apagada.
     * Se o `claim` devolver `false`, alguém comprou o jogador no meio do
     * caminho: ele já pertence a um elenco e não é mais problema desta regra.
     */
    private suspend fun ageMarket(nextSeason: Int) {
        val entries = marketRepo.findAll()
        if (entries.isEmpty()) return

        val rng = Random(marketAgingSeed(nextSeason))
        val stillListed = mutableListOf<Player>()
        val retired = mutableListOf<PlayerId>()

        for (entry in entries) {
            when (val outcome = entry.player.ageOneSeason(rng)) {
                is AgingOutcome.Retired ->
                    if (marketRepo.claim(outcome.player.id)) retired += outcome.player.id

                is AgingOutcome.Evolved,
                is AgingOutcome.Steady,
                is AgingOutcome.Regressed,
                -> stillListed += outcome.player
            }
        }

        playerRepo.saveFreeAgents(stillListed)
        playerRepo.deleteAll(retired)
    }

    /**
     * Calcula o balanço financeiro de cada clube na rodada: bilheteria (só o
     * mandante, em função da capacidade, da força do time e do preço que o
     * técnico cobra — issue #59) e folha salarial (a cada N rodadas). Função
     * sem efeitos colaterais — a gravação fica em [persistRoundEffects].
     *
     * Público e preço viajam no `RoundFinance` junto da receita: com o preço
     * sob controle do técnico, só o total não diz se a rodada rendeu por
     * estádio cheio ou por ingresso caro. Os dois números saem de UMA chamada
     * a `gate` — a regra de domínio continua sendo quem calcula dinheiro.
     *
     * O `gate` só é montado para quem jogou EM CASA: `startingLineup()`
     * reconstrói a escalação (mapa por id + filtro de lesionados + reservas),
     * e fazer isso para a liga inteira seria jogar fora metade do trabalho a
     * cada rodada.
     */
    private fun computeFinances(
        round: Round,
        clubs: Collection<Club>,
        cupPrizes: Map<ClubId, Money>,
        competitionOf: Map<ClubId, Competition>,
    ): List<RoundFinance> {
        val homeClubIds = round.matches.map { it.homeClubId.value }.toSet()
        // Folha segue as rodadas de LIGA; jogo de copa não cobra (issue #61).
        // Por clube desde o calendário semanal (issue #125): paga quem joga,
        // neste encaixe, uma rodada de liga que é "mês" de salário.
        val payroll: Set<ClubId> = round.matches
            .filter { isSalaryDate(it.stage) }
            .flatMapTo(HashSet()) { listOf(it.homeClubId, it.awayClubId) }
        return clubs.map { club ->
            val homeGate =
                if (club.id.value in homeClubIds) {
                    gate(
                        stadiumCapacity = club.stadiumCapacity,
                        // Mesmo time que a engine pôs em campo (issue #130).
                        homeStrength = (competitionOf[club.id]?.let(club::startingLineupIn) ?: club.startingLineup())
                            .teamStrength(),
                        ticketPriceCents = club.ticketPriceCents,
                    )
                } else {
                    null
                }
            val crowd = homeGate?.attendance ?: 0
            val revenue = homeGate?.revenue ?: Money.ZERO
            val salaries = if (club.id in payroll) club.squad.sumOfMoney { it.salary } else Money.ZERO
            val prize = cupPrizes[club.id] ?: Money.ZERO
            // Rombo = quanto da folha o caixa+bilheteria+prêmio não cobriram.
            // Espelha o truncamento em zero de [persistRoundEffects] (issue #23).
            val deficit = (salaries - revenue - prize - club.cash).coerceAtLeast(Money.ZERO)
            RoundFinance(
                clubId = club.id,
                ticketRevenue = revenue,
                salariesPaid = salaries,
                deficit = deficit,
                ticketPrice = club.ticketPriceCents,
                attendance = crowd,
                cupPrize = prize,
            )
        }
    }

    /**
     * Aplica, numa ÚNICA gravação por clube, o incremento de estatísticas
     * dos jogadores (gols/cartões — issue #2), a variação de caixa da rodada
     * (bilheteria − salários — issue #4), o desgaste físico: titulares
     * cansam, reservas recuperam e as lesões andam uma rodada (issue #54) —
     * e, por fim, a semana de treino no foco escolhido pelo técnico
     * (issue #58), devolvida como relatório por clube.
     *
     * O treino roda DEPOIS do desgaste porque é a semana que sucede a
     * partida: o elenco entra em campo cansado da rodada e é aí que o foco
     * decide se a semana repõe forma física ou desenvolve atributo. Rodar
     * antes daria ao técnico uma recuperação que a própria rodada apagaria.
     * Como este bloco só executa para quem venceu o claim da rodada
     * (issue #46), o treino é aplicado exatamente uma vez por rodada.
     *
     * A atribuição de estatísticas é por `playerId`: cada clube só atualiza
     * jogadores do próprio elenco (ids únicos), sem ambiguidade entre
     * mandante e visitante. O caixa nunca fica negativo (`coerceAtLeast(0)`).
     * Clubes sem nenhuma mudança não são salvos.
     *
     * O RNG da fadiga é semeado por `encaixe + clube` — determinístico como o
     * resto do fluxo (a partida usa `matchId`), então reprocessar o mesmo
     * encaixe com o mesmo elenco dá exatamente o mesmo desgaste.
     *
     * @param clubs todos os clubes da liga — quem folgou também recupera.
     * @param playedClubs só os que entraram em campo; para os demais o conjunto
     *   de titulares é vazio, então o elenco inteiro descansa.
     * @param boardAfterRound a confiança da diretoria depois da rodada (issue
     *   #62) — ver [boardEvaluator].
     * @param competitionOf a competição que cada clube jogou no encaixe (issue
     *   #130): decide o limite de estrangeiros dos titulares e em que
     *   competição os cartões contam para suspensão.
     */
    private suspend fun persistRoundEffects(
        matchday: Matchday,
        events: List<MatchEvent>,
        clubs: Collection<Club>,
        playedClubs: Collection<Club>,
        finances: List<RoundFinance>,
        boardAfterRound: (Club) -> BoardExpectation?,
        competitionOf: Map<ClubId, Competition>,
    ): List<TrainingReport> {
        val playedIds = playedClubs.map { it.id }.toSet()
        val goals = mutableMapOf<Long, Int>()
        val yellow = mutableMapOf<Long, Int>()
        val red = mutableMapOf<Long, Int>()
        // Lesão carrega a duração sorteada pela engine; se o jogador se
        // machucar duas vezes na mesma rodada, vale o afastamento mais longo.
        val injuries = mutableMapOf<PlayerId, Int>()

        for (event in events) {
            when (event) {
                is MatchEvent.Goal -> goals.merge(event.scorerId.value, 1, Int::plus)
                is MatchEvent.Card ->
                    if (event.red) red.merge(event.playerId.value, 1, Int::plus)
                    else yellow.merge(event.playerId.value, 1, Int::plus)
                is MatchEvent.Injury -> injuries.merge(event.playerId, event.roundsOut, ::maxOf)
                // Miss e Save não mexem na ficha do jogador — vivem só no feed
                // e no sumário do FullTime (issue #57). Gol de pênalti na
                // disputa também não conta para a artilharia (issue #61): a
                // disputa desempata o confronto, não faz parte do jogo.
                is MatchEvent.KickOff,
                is MatchEvent.Miss,
                is MatchEvent.Save,
                is MatchEvent.PenaltyShootout,
                is MatchEvent.FullTime,
                -> Unit
            }
        }

        val financeByClub = finances.associateBy { it.clubId.value }
        val cardsByPlayer: Map<PlayerId, MatchCards> = (yellow.keys + red.keys).associate { id ->
            PlayerId(id) to MatchCards(yellow = yellow[id] ?: 0, red = red[id] ?: 0)
        }
        val training = mutableListOf<TrainingReport>()

        for (club in clubs) {
            // Quem entrou em campo — mesma escalação que a engine usou. Clube
            // que folgou não tem titular: o elenco inteiro recupera.
            val starterIds =
                competitionOf[club.id]?.takeIf { club.id in playedIds }
                    ?.let { club.startingLineupIn(it).players.map { p -> p.id }.toSet() }
                    ?: emptySet()

            // Efeitos da rodada sobre o clube ATUAL (issue #89): na 1ª tentativa
            // é o `club` já lido; se outra escrita chegar antes do `save`,
            // `saveReapplying` relê e recalcula — os RNGs são semeados por
            // rodada + clube, então reaplicar é determinístico. Os titulares
            // ficam fixos no `club` lido: são os que a engine pôs em campo.
            var report: TrainingReport? = null
            saveReapplying(club) { current ->
                val rested = applyRoundFitness(
                    squad = current.squad,
                    starterIds = starterIds,
                    rng = Random(fitnessSeed(matchday.serial, club.id)),
                    newInjuries = injuries,
                    // Lesão conta jogos do CLUBE (issue #125): quem não jogou
                    // neste encaixe descansa, mas a lesão não anda.
                    clubPlayed = club.id in playedIds,
                )

                val updatedSquad = rested.map { p ->
                    val id = p.id.value
                    val g = goals[id] ?: 0
                    val y = yellow[id] ?: 0
                    val r = red[id] ?: 0
                    if (g == 0 && y == 0 && r == 0) {
                        p
                    } else {
                        p.copy(
                            goals = p.goals + g,
                            yellowCards = p.yellowCards + y,
                            redCards = p.redCards + r,
                        )
                    }
                }

                // Suspensão por cartões (issue #130): conta só na competição que
                // o clube jogou — quem estava suspenso cumpre um jogo, o 3º
                // amarelo ou o vermelho abre gancho. Clube que folgou não muda.
                val disciplined = competitionOf[club.id]?.takeIf { club.id in playedIds }
                    ?.let { updatedSquad.afterMatchIn(it, cardsByPlayer) }
                    ?: updatedSquad

                // Semana de treino no foco escolhido (issue #58). Seed própria
                // (`trainingSeed`) para não repetir a sequência do desgaste desta
                // mesma rodada/clube. O relatório sai daqui pronto para o evento
                // de fim de rodada, mesmo vazio — "treinei e ninguém evoluiu"
                // também é notícia.
                val trained = train(
                    squad = disciplined,
                    focus = current.trainingFocus,
                    rng = Random(trainingSeed(matchday.serial, current.id)),
                )
                report = TrainingReport(current.id, current.trainingFocus, trained.events)

                // Caixa é aplicado como DELTA sobre o clube relido — é isso que
                // impede a rodada de apagar um débito concorrente (ampliar estádio,
                // compra) ou vice-versa (issue #89).
                val finance = financeByClub[current.id.value]
                val newCash = if (finance == null) current.cash
                    else (current.cash + finance.cashDelta()).coerceAtLeast(Money.ZERO)

                current.copy(squad = trained.squad, cash = newCash, board = boardAfterRound(current))
            }
            training += checkNotNull(report)
        }

        return training
    }

    /**
     * Grava num clube um efeito da rodada sem perder escritas concorrentes
     * (issue #89). A rodada não tem como devolver "409" para ninguém — ela é o
     * relógio do jogo —, então, no conflito, RELÊ o clube e reaplica
     * [transform] sobre o estado novo: o caixa entra como delta e o elenco é
     * recalculado, preservando o que o técnico gravou no meio do caminho
     * (ampliar estádio, compra, preço do ingresso).
     *
     * A 1ª tentativa usa [snapshot] (o clube já lido pela rodada) para não
     * custar uma leitura extra no caso comum, sem concorrência. Se nada mudou,
     * não grava — mesma regra de antes ("clubes sem mudança não são salvos").
     *
     * Esgotar as tentativas significa o mesmo clube sendo regravado sem parar
     * durante a finalização — contenção patológica, não fluxo de negócio, e
     * por isso o único caso que vira exception (CLAUDE.md).
     *
     * `inline` deixa [transform] atribuir `var`s locais do chamador (o
     * relatório de treino, as aposentadorias) sem cerimônia de closure.
     */
    private suspend inline fun saveReapplying(snapshot: Club, transform: (Club) -> Club): Club {
        var current = snapshot
        val outcome = retryOnConflict(
            maxAttempts = ROUND_CLUB_WRITE_ATTEMPTS,
            isConflict = { it is ClubSaveResult.Conflict },
        ) { attempt ->
            if (attempt > 0) {
                current = checkNotNull(clubRepo.findById(snapshot.id)) {
                    "Clube ${snapshot.id.value} sumiu durante a finalização da rodada"
                }
            }
            val updated = transform(current)
            if (updated == current) ClubSaveResult.Saved(current) else clubRepo.save(updated)
        }
        return when (outcome) {
            is ClubSaveResult.Saved -> outcome.club
            is ClubSaveResult.Conflict -> error(
                "Clube ${snapshot.id.value}: $ROUND_CLUB_WRITE_ATTEMPTS conflitos seguidos ao gravar a rodada",
            )
        }
    }

    private fun RoundMatch.finish(score: Pair<Int, Int>?, penalties: PenaltyScore?): RoundMatch =
        if (score == null) this
        else copy(
            status = RoundStatus.Finished,
            homeGoals = score.first,
            awayGoals = score.second,
            minute = 90,
            penalties = penalties,
        )

    private fun emptyLineup() =
        Lineup(players = emptyList(), formation = Formation.F_4_4_2)
}

/**
 * Tentativas da rodada ao gravar um clube (issue #89). Folga maior que a dos
 * comandos do técnico ([MAX_CLUB_WRITE_ATTEMPTS]): a rodada não pode devolver
 * conflito para ninguém, então insiste mais antes de desistir.
 */
private const val ROUND_CLUB_WRITE_ATTEMPTS: Int = 5

/**
 * Cartões de cada clube no encaixe (issue #131), da súmula de cada jogo — o
 * insumo dos critérios de desempate por disciplina (`FEWER_*_CARDS`). O lado
 * do cartão (`home`) diz de que clube ele é.
 */
private fun List<SimulatedMatch>.cardsByClub(): Map<ClubId, Discipline> {
    val cards = mutableMapOf<ClubId, Discipline>()
    for (sim in this) {
        for (card in sim.sheet.filterIsInstance<MatchEvent.Card>()) {
            val club = if (card.home) sim.match.homeClubId else sim.match.awayClubId
            val current = cards[club] ?: Discipline()
            cards[club] = if (card.red) current.copy(redCards = current.redCards + 1)
                else current.copy(yellowCards = current.yellowCards + 1)
        }
    }
    return cards
}

/** Ordem das viradas no mesmo encaixe: o Brasil primeiro, depois pelo código do país. */
private val HOME_FIRST: Comparator<CountryCode> =
    compareBy<CountryCode> { it.value != "BRA" }.thenBy { it.value }
