package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.metrics.GoalfatherMetrics
import com.carlesso.goalfather.application.port.`in`.PlayRoundUseCase
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.application.port.out.PlayerRepository
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.domain.engine.MatchSetup
import com.carlesso.goalfather.domain.engine.MatchSimulator
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.event.TrainingReport
import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Retirement
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundFinance
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.teamStrength
import com.carlesso.goalfather.domain.rules.AgingOutcome
import com.carlesso.goalfather.domain.rules.ageOneSeason
import com.carlesso.goalfather.domain.rules.ageSquadForSeason
import com.carlesso.goalfather.domain.rules.applyPromotionRelegation
import com.carlesso.goalfather.domain.rules.applyRoundFitness
import com.carlesso.goalfather.domain.rules.applyRoundToStandings
import com.carlesso.goalfather.domain.rules.canScheduleSeason
import com.carlesso.goalfather.domain.rules.fitnessSeed
import com.carlesso.goalfather.domain.rules.gate
import com.carlesso.goalfather.domain.rules.generateRound
import com.carlesso.goalfather.domain.rules.isSalaryRound
import com.carlesso.goalfather.domain.rules.marketAgingSeed
import com.carlesso.goalfather.domain.rules.promotionSpotsFor
import com.carlesso.goalfather.domain.rules.relegationSpotsFor
import com.carlesso.goalfather.domain.rules.seasonRounds
import com.carlesso.goalfather.domain.rules.train
import com.carlesso.goalfather.domain.rules.trainingSeed
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
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
    private val simulator: MatchSimulator = MatchSimulator(),
    // Default = registry isolado: mantém os testes existentes construindo o
    // service sem passar métrica; em produção o BeanConfig injeta o registry real
    // (mesmo padrão do `Random`/`MatchSimulator` já injetáveis).
    private val meterRegistry: MeterRegistry = SimpleMeterRegistry(),
) : PlayRoundUseCase {

    private val simulationTimer: Timer = meterRegistry.timer(GoalfatherMetrics.ROUND_SIMULATION)

    override fun stream(roundNumber: Int): Flow<RoundEvent> = flow {
        val round = leagueRepo.findRound(roundNumber)
            ?: throw IllegalArgumentException("Rodada $roundNumber não encontrada")

        // Pré-computa todos os eventos de todas as partidas.
        // (Em uma versão futura podemos intercalar geração + emissão para
        // não materializar tudo na memória, mas com ~3 partidas × ~30
        // eventos cabe folgado.)
        val tagged = mutableListOf<Pair<Long, MatchEvent>>()
        val finalScores = mutableMapOf<Long, Pair<Int, Int>>()
        // Clubes que entraram em campo nesta rodada — guardados para
        // persistir as estatísticas dos jogadores ao final (issue #2).
        val involvedClubs = mutableMapOf<Long, Club>()

        // Timer só sobre o trabalho da engine (todas as partidas). Fica AQUI e não
        // no WS handler de propósito: o handler intercala delays de ~80ms/minuto
        // para dar sensação de "ao vivo" — cronometrar lá mediria a animação, não
        // a simulação. `Timer.start/stop` só captura wall time, então atravessa
        // suspensões de coroutine sem problema.
        val sample = Timer.start(meterRegistry)
        for (match in round.matches) {
            val home = clubRepo.findById(match.homeClubId)
            val away = clubRepo.findById(match.awayClubId)
            home?.let { involvedClubs[it.id.value] = it }
            away?.let { involvedClubs[it.id.value] = it }

            val setup = MatchSetup(
                home = home?.startingLineup() ?: emptyLineup(),
                away = away?.startingLineup() ?: emptyLineup(),
                homeName = match.homeClubName,
                awayName = match.awayClubName,
            )

            for (event in simulator.simulate(setup, Random(match.matchId)).toList()) {
                tagged.add(match.matchId to event)
                if (event is MatchEvent.FullTime) {
                    finalScores[match.matchId] = event.homeGoals to event.awayGoals
                }
            }
        }
        sample.stop(simulationTimer)

        // Estável por minuto: eventos de minuto igual saem juntos
        // (sensação de "vários estádios ao mesmo tempo").
        tagged.sortBy { it.second.minute }
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
        val finishedMatches = round.matches.map { m -> m.finish(finalScores[m.matchId]) }
        val finishedRound = round.copy(matches = finishedMatches, status = RoundStatus.Finished)

        if (!leagueRepo.finishRound(finishedRound)) {
            emit(RoundEvent.RoundFinished(leagueRepo.currentStandings()))
            return@flow
        }

        // Vencemos a corrida: aplicamos os efeitos exatamente uma vez.
        //
        // Ordem (claim ANTES dos efeitos) troca a semântica de *at-least-once*
        // para *at-most-once*: nunca dobramos caixa/estatísticas, mas uma
        // interrupção entre o claim e o fim da persistência deixaria a rodada
        // `Finished` com efeitos parciais — e como todo stream seguinte cai no
        // replay, a liga travaria (a próxima rodada nunca seria gerada). Por
        // isso o bloco roda em `NonCancellable`: fechar a aba do WS (o handler
        // faz `job.cancel()` no `afterConnectionClosed`) logo após o FullTime
        // não pode rasgar a finalização no meio.
        //
        // NonCancellable NÃO cobre crash de processo entre o claim e o fim —
        // esse resíduo de durabilidade fica documentado em docs/ARQUITETURA.md
        // (fechá-lo exige efeitos + claim na MESMA transação, hoje inviável com
        // os ports `suspend` por agregado). É estreito: dezenas de ms.
        //
        // Enquanto os efeitos não são gravados, um leitor concorrente vê a
        // tabela sem os pontos desta rodada; uma reconexão posterior já devolve
        // a definitiva (não vale para o caso de crash acima).
        val tailEvents = withContext(NonCancellable) {
            finalizeRound(round, finishedRound, tagged.map { it.second }, involvedClubs.values)
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
    ): List<RoundEvent> {
        // Calcula o balanço financeiro da rodada — bilheteria (mandante) e
        // folha salarial em rodadas de "mês" (issue #4).
        val finances = computeFinances(round, involvedClubs)

        // Acumula estatísticas dos jogadores (issue #2), aplica o caixa
        // (issue #4) e o desgaste da rodada (issue #54) em uma única gravação
        // por clube.
        //
        // A fadiga alcança TODOS os clubes, não só os que entraram em campo:
        // quem folga na rodada precisa recuperar stamina. Hoje toda divisão tem
        // nº PAR de clubes (o gerador de fixtures rejeita ímpar), então na
        // prática ninguém folga — mas depender disso deixaria a regra errada por
        // acidente. `ifEmpty` cai para os clubes envolvidos se o repositório
        // vier vazio, preservando o comportamento dos testes que só stubam
        // `findById`.
        //
        // Uma leitura só, reusada no calendário lá embaixo: `persistRoundEffects`
        // não mexe em id/nome/divisão, que é tudo que o agendamento consulta.
        val clubs = clubRepo.findAll().ifEmpty { involvedClubs.toList() }
        val training = persistRoundEffects(round.number, events, clubs, involvedClubs, finances)

        // A MESMA rodada é aplicada à tabela de cada divisão: só os jogos da
        // divisão em questão contam (clubes fora da tabela são ignorados pela
        // regra), então nenhum filtro manual é necessário (issue #47).
        val newStandings = leagueRepo.currentStandings()
            .map { applyRoundToStandings(finishedRound, it) }
        newStandings.forEach { leagueRepo.saveStandings(it) }

        // Rodada consumida: zera a prontidão para a próxima começar limpa
        // (issue #20). Os técnicos sinalizam de novo na rodada seguinte.
        readinessRepo.reset(round.number)

        // Avança o calendário. Num turno único (Berger), a temporada dura o
        // turno da MAIOR divisão (N-1 rodadas para N clubes). Enquanto não
        // chega à última rodada, gera a próxima da MESMA temporada. Ao
        // encerrar a última, vira a temporada (issue #11) com
        // promoção/rebaixamento entre divisões (issue #47).
        val canSchedule = canScheduleSeason(clubs)
        val seasonEnded = canSchedule && round.number >= seasonRounds(clubs)

        val tail = mutableListOf<RoundEvent>()
        if (seasonEnded) {
            tail += startNextSeason(round.season, newStandings)
        } else if (canSchedule) {
            // Gera a próxima rodada antes de sinalizar RoundFinished, para que
            // `getCurrentRound` já encontre uma rodada Scheduled.
            leagueRepo.saveRound(generateRound(round.number + 1, round.season, clubs))
        }

        // RoundFinished é sempre o último evento do stream (sinal de "rodada
        // encerrada"); SeasonFinished, quando há, vem logo antes dele.
        tail += RoundEvent.RoundFinished(newStandings, finances, training)
        return tail
    }

    /**
     * Encerra a temporada e abre a seguinte: aplica promoção/rebaixamento
     * às tabelas finais (issue #47), zera as estatísticas de temporada dos
     * jogadores, gera a rodada 1 e tabelas novas (uma por divisão). As
     * tabelas encerradas NÃO são apagadas (PK por `season`+`division`),
     * continuam consultáveis via `GET /api/league/standings?season=`.
     * Devolve o `SeasonFinished` para o chamador emitir (nada é emitido
     * aqui — roda sob `NonCancellable`).
     */
    private suspend fun startNextSeason(
        endedSeason: Int,
        finalStandings: List<Standings>,
    ): RoundEvent.SeasonFinished {
        // Campeão = líder da elite (divisão 1).
        val champion = finalStandings.minBy { it.division }.rows.first()
        val nextSeason = endedSeason + 1

        val nextDivisions = applyPromotionRelegation(finalStandings)
        val (clubs, retirements) = startNextSeasonClubs(nextSeason, nextDivisions)
        ageMarket(nextSeason)
        leagueRepo.saveRound(generateRound(1, nextSeason, clubs))
        freshStandings(nextSeason, clubs).forEach { leagueRepo.saveStandings(it) }

        return RoundEvent.SeasonFinished(
            season = endedSeason,
            champion = champion,
            standings = finalStandings,
            retirements = retirements,
        )
    }

    /**
     * Prepara os clubes para a nova temporada numa ÚNICA gravação por clube:
     * envelhece o elenco e promove a base no lugar de quem se aposentou
     * (issue #55), zera gols/cartões, cura lesões e move quem subiu/desceu
     * para a nova divisão (issue #47). Relê os clubes (já com o caixa
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
    ): SeasonTurnover {
        val clubs = mutableListOf<Club>()
        val retirements = mutableListOf<Retirement>()

        for (club in clubRepo.findAll()) {
            val turn = ageSquadForSeason(club.squad, club.id, nextSeason)
            retirements += turn.retirements

            // Pré-temporada: elenco volta inteiro e descansado (issue #54).
            val reset = turn.squad.map {
                it.copy(
                    goals = 0,
                    yellowCards = 0,
                    redCards = 0,
                    availability = Availability.Available,
                    stamina = 100,
                )
            }
            val updated = club.copy(
                squad = reset,
                division = nextDivisions[club.id] ?: club.division,
            )
            if (updated != club) clubRepo.save(updated)
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

    private fun freshStandings(season: Int, clubs: List<Club>): List<Standings> {
        val byDivision = clubs.groupBy { it.division }.toSortedMap()
        return byDivision.map { (division, divisionClubs) ->
            Standings(
                season = season,
                round = 0,
                rows = divisionClubs.sortedBy { it.id.value }.mapIndexed { i, club ->
                    StandingRow(position = i + 1, clubId = club.id, clubName = club.name)
                },
                division = division,
                promotionSpots = promotionSpotsFor(division),
                relegationSpots = relegationSpotsFor(division, byDivision.size),
            )
        }
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
    private fun computeFinances(round: Round, clubs: Collection<Club>): List<RoundFinance> {
        val homeClubIds = round.matches.map { it.homeClubId.value }.toSet()
        val salaryRound = isSalaryRound(round.number)
        return clubs.map { club ->
            val homeGate =
                if (club.id.value in homeClubIds) {
                    gate(
                        stadiumCapacity = club.stadiumCapacity,
                        homeStrength = club.startingLineup().teamStrength(),
                        ticketPriceCents = club.ticketPriceCents,
                    )
                } else {
                    null
                }
            val crowd = homeGate?.attendance ?: 0
            val revenue = homeGate?.revenue ?: 0L
            val salaries = if (salaryRound) club.squad.sumOf { it.salary.toLong() } else 0L
            // Rombo = quanto da folha o caixa+bilheteria não cobriram. Espelha o
            // truncamento em zero de [persistRoundEffects] (issue #23).
            val deficit = (salaries - revenue - club.cash).coerceAtLeast(0)
            RoundFinance(
                clubId = club.id,
                ticketRevenue = revenue,
                salariesPaid = salaries,
                deficit = deficit,
                ticketPrice = club.ticketPriceCents,
                attendance = crowd,
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
     * O RNG da fadiga é semeado por `rodada + clube` — determinístico como o
     * resto do fluxo (a partida usa `matchId`), então reprocessar a mesma
     * rodada com o mesmo elenco dá exatamente o mesmo desgaste.
     *
     * @param clubs todos os clubes da liga — quem folgou também recupera.
     * @param playedClubs só os que entraram em campo; para os demais o conjunto
     *   de titulares é vazio, então o elenco inteiro descansa.
     */
    private suspend fun persistRoundEffects(
        roundNumber: Int,
        events: List<MatchEvent>,
        clubs: Collection<Club>,
        playedClubs: Collection<Club>,
        finances: List<RoundFinance>,
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
                // e no sumário do FullTime (issue #57).
                is MatchEvent.KickOff,
                is MatchEvent.Miss,
                is MatchEvent.Save,
                is MatchEvent.FullTime,
                -> Unit
            }
        }

        val financeByClub = finances.associateBy { it.clubId.value }
        val training = mutableListOf<TrainingReport>()

        for (club in clubs) {
            // Quem entrou em campo — mesma escalação que a engine usou. Clube
            // que folgou não tem titular: o elenco inteiro recupera.
            val starterIds =
                if (club.id in playedIds) club.startingLineup().players.map { it.id }.toSet()
                else emptySet()

            val rested = applyRoundFitness(
                squad = club.squad,
                starterIds = starterIds,
                rng = Random(fitnessSeed(roundNumber, club.id)),
                newInjuries = injuries,
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

            // Semana de treino no foco escolhido (issue #58). Seed própria
            // (`trainingSeed`) para não repetir a sequência do desgaste desta
            // mesma rodada/clube. O relatório sai daqui pronto para o evento
            // de fim de rodada, mesmo vazio — "treinei e ninguém evoluiu"
            // também é notícia.
            val trained = train(
                squad = updatedSquad,
                focus = club.trainingFocus,
                rng = Random(trainingSeed(roundNumber, club.id)),
            )
            training += TrainingReport(club.id, club.trainingFocus, trained.events)

            val finance = financeByClub[club.id.value]
            val newCash = if (finance == null) club.cash
                else (club.cash + finance.ticketRevenue - finance.salariesPaid).coerceAtLeast(0)

            if (trained.squad != club.squad || newCash != club.cash) {
                clubRepo.save(club.copy(squad = trained.squad, cash = newCash))
            }
        }

        return training
    }

    private fun RoundMatch.finish(score: Pair<Int, Int>?): RoundMatch =
        if (score == null) this
        else copy(
            status = RoundStatus.Finished,
            homeGoals = score.first,
            awayGoals = score.second,
            minute = 90,
        )

    private fun emptyLineup() =
        Lineup(players = emptyList(), formation = Formation.F_4_4_2)
}
