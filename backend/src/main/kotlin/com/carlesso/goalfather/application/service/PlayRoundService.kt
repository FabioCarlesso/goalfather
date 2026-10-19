package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.metrics.GoalfatherMetrics
import com.carlesso.goalfather.application.port.`in`.PlayRoundUseCase
import com.carlesso.goalfather.application.port.out.ClubClaimRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.CupRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.application.port.out.PlayerRepository
import com.carlesso.goalfather.application.port.out.RoundReadinessRepository
import com.carlesso.goalfather.application.port.out.SeasonHistoryRepository
import com.carlesso.goalfather.application.port.out.TransferOfferRepository
import com.carlesso.goalfather.domain.engine.MatchSetup
import com.carlesso.goalfather.domain.engine.MatchSimulator
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.event.RoundEvent
import com.carlesso.goalfather.domain.event.TrainingReport
import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.BoardExpectation
import com.carlesso.goalfather.domain.model.BoardReview
import com.carlesso.goalfather.domain.model.BoardVerdict
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CupClub
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.PenaltyScore
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Retirement
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundFinance
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.teamStrength
import com.carlesso.goalfather.domain.rules.AgingOutcome
import com.carlesso.goalfather.domain.rules.ageOneSeason
import com.carlesso.goalfather.domain.rules.afterRound
import com.carlesso.goalfather.domain.rules.ageSquadForSeason
import com.carlesso.goalfather.domain.rules.applyPromotionRelegation
import com.carlesso.goalfather.domain.rules.applyRoundFitness
import com.carlesso.goalfather.domain.rules.applyRoundToStandings
import com.carlesso.goalfather.domain.rules.boardExpectations
import com.carlesso.goalfather.domain.rules.canHoldCup
import com.carlesso.goalfather.domain.rules.canScheduleSeason
import com.carlesso.goalfather.domain.rules.cashDelta
import com.carlesso.goalfather.domain.rules.cupDrawSeed
import com.carlesso.goalfather.domain.rules.drawCup
import com.carlesso.goalfather.domain.rules.fitnessSeed
import com.carlesso.goalfather.domain.rules.gate
import com.carlesso.goalfather.domain.rules.generateRound
import com.carlesso.goalfather.domain.rules.isSalaryDate
import com.carlesso.goalfather.domain.rules.marketAgingSeed
import com.carlesso.goalfather.domain.rules.promotionSpotsFor
import com.carlesso.goalfather.domain.rules.recordRound
import com.carlesso.goalfather.domain.rules.relegationSpotsFor
import com.carlesso.goalfather.domain.rules.scheduleRound
import com.carlesso.goalfather.domain.rules.seasonCalendar
import com.carlesso.goalfather.domain.rules.seasonBoardReviews
import com.carlesso.goalfather.domain.rules.seasonRecordOf
import com.carlesso.goalfather.domain.rules.train
import com.carlesso.goalfather.domain.rules.trainingSeed
import com.carlesso.goalfather.domain.rules.toDismissal
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
 * **Propostas entre técnicos (issue #63).** Cada rodada encerrada anda uma
 * rodada no prazo das propostas pendentes; quem chega a zero expira.
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
        // Disputas de pênaltis da rodada de copa (issue #61), por partida.
        val shootouts = mutableMapOf<Long, PenaltyScore>()
        // Clubes que entraram em campo nesta rodada — guardados para
        // persistir as estatísticas dos jogadores ao final (issue #2).
        val involvedClubs = mutableMapOf<Long, Club>()

        // Fase 1 — I/O: busca os clubes e monta os `MatchSetup` ANTES de ligar o
        // cronômetro (issue #51). `map` é `inline`, então as chamadas `suspend`
        // a `findById` valem aqui dentro sem precisar de `async`/coroutine extra.
        val setups = round.matches.map { match ->
            val home = clubRepo.findById(match.homeClubId)
            val away = clubRepo.findById(match.awayClubId)
            home?.let { involvedClubs[it.id.value] = it }
            away?.let { involvedClubs[it.id.value] = it }

            match to MatchSetup(
                home = home?.startingLineup() ?: emptyLineup(),
                away = away?.startingLineup() ?: emptyLineup(),
                homeName = match.homeClubName,
                awayName = match.awayClubName,
                // Data de copa = mata-mata: empate decide nos pênaltis (issue #61).
                knockout = round.isKnockout,
            )
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
        val sample = Timer.start(meterRegistry)
        for ((match, setup) in setups) {
            for (event in simulator.simulate(setup, Random(match.matchId)).toList()) {
                tagged.add(match.matchId to event)
                when (event) {
                    is MatchEvent.FullTime -> finalScores[match.matchId] = event.homeGoals to event.awayGoals
                    is MatchEvent.PenaltyShootout ->
                        shootouts[match.matchId] = PenaltyScore(event.homeScore, event.awayScore)
                    else -> Unit
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
        val finishedMatches = round.matches.map { m -> m.finish(finalScores[m.matchId], shootouts[m.matchId]) }
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
        // Copa da temporada (issue #61). Numa data de copa, os placares (com
        // os pênaltis) vão para a chave ANTES das finanças: é o resultado que
        // decide quem leva o prêmio da fase, e o prêmio entra no caixa na mesma
        // gravação da bilheteria.
        val seasonCup = cupRepo.findBySeason(round.season)
        val cupOutcome = when (round.stage) {
            is RoundStage.League -> null
            is RoundStage.Cup -> seasonCup?.recordRound(finishedRound)
        }
        cupOutcome?.let { cupRepo.save(it.cup) }
        val cup = cupOutcome?.cup ?: seasonCup

        // Calcula o balanço financeiro da rodada — bilheteria (mandante),
        // folha salarial em rodadas de "mês" (issue #4) e prêmio da copa.
        val finances = computeFinances(round, involvedClubs, cupOutcome?.prizes.orEmpty())

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

        // A MESMA rodada é aplicada à tabela de cada divisão: só os jogos da
        // divisão em questão contam (clubes fora da tabela são ignorados pela
        // regra), então nenhum filtro manual é necessário (issue #47).
        // Calculada ANTES dos efeitos por clube porque a confiança da
        // diretoria (issue #62) é função da posição JÁ com esta rodada — e
        // entra na mesma gravação do clube que o caixa e o elenco.
        val newStandings = leagueRepo.currentStandings()
            .map { applyRoundToStandings(finishedRound, it) }
        val boardAfterRound = boardEvaluator(finishedRound, newStandings, clubs)

        val training = persistRoundEffects(round.number, events, clubs, involvedClubs, finances, boardAfterRound)
        newStandings.forEach { leagueRepo.saveStandings(it) }

        // Rodada consumida: zera a prontidão para a próxima começar limpa
        // (issue #20). Os técnicos sinalizam de novo na rodada seguinte.
        readinessRepo.reset(round.number)

        // Avança o calendário. Num turno único (Berger), a liga dura o turno
        // da MAIOR divisão (N-1 rodadas para N clubes), e as fases da copa são
        // intercaladas entre elas (issue #61) — `seasonCalendar` diz o que cada
        // data disputa. Enquanto não chega à última data, gera a próxima da
        // MESMA temporada. Ao encerrar a última, vira a temporada (issue #11)
        // com promoção/rebaixamento entre divisões (issue #47).
        val canSchedule = canScheduleSeason(clubs)
        val calendar = if (canSchedule) seasonCalendar(clubs, cup) else emptyList()
        val seasonEnded = canSchedule && round.number >= calendar.size

        val tail = mutableListOf<RoundEvent>()
        if (seasonEnded) {
            tail += startNextSeason(round.season, newStandings, cup?.champion)
        } else if (canSchedule) {
            // Gera a próxima data antes de sinalizar RoundFinished, para que
            // `getCurrentRound` já encontre uma rodada Scheduled. `number` é
            // 1-based e a lista 0-based: `calendar[number]` é a data seguinte.
            val next = calendar[round.number]
            leagueRepo.saveRound(scheduleRound(round.number + 1, round.season, next, clubs, cup))
        }

        // Prazo das propostas entre técnicos (issue #63): uma rodada a menos
        // para cada pendente, e quem chega a zero expira. Aqui, depois do claim
        // da rodada (issue #46), o relógio anda exatamente uma vez por rodada.
        // DEPOIS do agendamento de propósito (revisão do PR #94): é um efeito
        // secundário, e uma falha nele antes de `scheduleRound` deixaria a
        // rodada `Finished` sem sucessora — a liga travaria por causa de uma
        // proposta. Aqui, no pior caso, o prazo anda na rodada seguinte.
        advanceOfferDeadlines()

        // RoundFinished é sempre o último evento do stream (sinal de "rodada
        // encerrada"); SeasonFinished, quando há, vem logo antes dele.
        tail += RoundEvent.RoundFinished(newStandings, finances, training)
        return tail
    }

    /**
     * Encerra a temporada e abre a seguinte: grava a história (issue #60),
     * aplica promoção/rebaixamento às tabelas finais (issue #47), zera as
     * estatísticas de temporada dos jogadores, gera a rodada 1 e tabelas
     * novas (uma por divisão). As tabelas encerradas NÃO são apagadas (PK por
     * `season`+`division`), continuam consultáveis via
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
        endedSeason: Int,
        finalStandings: List<Standings>,
        cupChampion: CupClub?,
    ): RoundEvent.SeasonFinished {
        val clubsAtSeasonEnd = clubRepo.findAll()

        // História ANTES do reset (issue #60). O `append` devolve `false` se a
        // temporada já tinha record — ignorado de propósito: quem chega aqui
        // venceu o claim da rodada (issue #46), então um `false` só pode vir de
        // uma réplica retardatária, e o certo é não sobrescrever a história.
        val record = seasonRecordOf(endedSeason, finalStandings, clubsAtSeasonEnd, cupChampion)
        historyRepo.append(record)

        val nextSeason = endedSeason + 1
        val nextDivisions = applyPromotionRelegation(finalStandings)
        val (agedClubs, retirements) = startNextSeasonClubs(nextSeason, nextDivisions, clubsAtSeasonEnd)
        // Diretoria (issue #62) DEPOIS do envelhecimento e da troca de divisão:
        // a meta da temporada nova é função do elenco que o clube vai ter e da
        // divisão em que vai jogar, não dos que acabaram de sair.
        val (clubs, boardReviews) = applyBoardTurnover(endedSeason, nextSeason, finalStandings, agedClubs)
        ageMarket(nextSeason)
        // Sorteio da copa da temporada nova (issue #61), com os clubes JÁ nas
        // divisões novas. Vem antes da rodada 1 só por legibilidade: o
        // calendário garante que a data 1 é sempre de liga.
        drawSeasonCup(nextSeason, clubs)
        leagueRepo.saveRound(generateRound(1, nextSeason, clubs))
        freshStandings(nextSeason, clubs).forEach { leagueRepo.saveStandings(it) }

        return RoundEvent.SeasonFinished(
            season = endedSeason,
            // Campeão = líder da elite, o mesmo que foi para a história: uma
            // fonte só evita evento e record discordarem sobre quem levou.
            champion = record.champion.row,
            standings = finalStandings,
            retirements = retirements,
            cupChampion = cupChampion,
            boardReviews = boardReviews,
        )
    }

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
                BoardVerdict.TargetMissed, BoardVerdict.Fired, null -> 0L
            }
            saveReapplying(club) { current ->
                current.copy(cash = current.cash + bonus, board = nextBoards[current.id])
            }
        }

        val owners = clubs.associate { it.id to it.ownerId }
        // `toDismissal()` só existe para o veredito `Fired`: o `mapNotNull`
        // filtra a demissão e monta o registro numa passada só.
        val dismissals = reviews.filter { owners[it.clubId] != null }.mapNotNull { it.toDismissal() }
        for (dismissal in dismissals) claimRepo.release(dismissal.clubId, dismissal)
        // O `release` muda dono e versão no banco: devolver a cópia de antes
        // dele deixaria clubes com `ownerId`/`version` velhos para quem vier
        // depois. Só relê quando houve demissão — o caso comum não paga a leitura.
        val clubsAfter = if (dismissals.isEmpty()) updated else clubRepo.findAll().ifEmpty { updated }
        return BoardTurnover(clubsAfter, reviews)
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
        when (round.stage) {
            is RoundStage.Cup -> return Club::board
            is RoundStage.League -> Unit
        }
        val playedDivisions = round.matches.mapTo(HashSet()) { it.division }
        val positions = standings
            .filter { it.division in playedDivisions }
            .flatMap { it.rows }
            .associate { it.clubId to it.position }
        val fallback by lazy { boardExpectations(round.season, clubs) }
        // Função anônima (`fun(...)`) em vez de lambda: nela `return` sai da
        // própria função, sem rótulo — o idioma para corpo com saídas antecipadas.
        return fun(club: Club): BoardExpectation? {
            val position = positions[club.id] ?: return club.board
            val expectation = club.board?.takeIf { it.season == round.season }
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
     * Sorteia e grava a copa da temporada (issue #61). Liga fora da faixa de
     * [canHoldCup] joga só o campeonato — e o calendário, sem copa, volta a
     * ser só de liga. Seed por temporada: reprocessar a virada sorteia igual.
     */
    private suspend fun drawSeasonCup(season: Int, clubs: List<Club>) {
        if (!canHoldCup(clubs)) return
        cupRepo.save(drawCup(season, clubs, Random(cupDrawSeed(season))))
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
                    )
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
    private fun computeFinances(
        round: Round,
        clubs: Collection<Club>,
        cupPrizes: Map<ClubId, Long>,
    ): List<RoundFinance> {
        val homeClubIds = round.matches.map { it.homeClubId.value }.toSet()
        // Folha segue as rodadas de LIGA; data de copa não cobra (issue #61).
        val salaryRound = isSalaryDate(round.stage)
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
            val prize = cupPrizes[club.id] ?: 0L
            // Rombo = quanto da folha o caixa+bilheteria+prêmio não cobriram.
            // Espelha o truncamento em zero de [persistRoundEffects] (issue #23).
            val deficit = (salaries - revenue - prize - club.cash).coerceAtLeast(0)
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
     * O RNG da fadiga é semeado por `rodada + clube` — determinístico como o
     * resto do fluxo (a partida usa `matchId`), então reprocessar a mesma
     * rodada com o mesmo elenco dá exatamente o mesmo desgaste.
     *
     * @param clubs todos os clubes da liga — quem folgou também recupera.
     * @param playedClubs só os que entraram em campo; para os demais o conjunto
     *   de titulares é vazio, então o elenco inteiro descansa.
     * @param boardAfterRound a confiança da diretoria depois da rodada (issue
     *   #62) — ver [boardEvaluator].
     */
    private suspend fun persistRoundEffects(
        roundNumber: Int,
        events: List<MatchEvent>,
        clubs: Collection<Club>,
        playedClubs: Collection<Club>,
        finances: List<RoundFinance>,
        boardAfterRound: (Club) -> BoardExpectation?,
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
        val training = mutableListOf<TrainingReport>()

        for (club in clubs) {
            // Quem entrou em campo — mesma escalação que a engine usou. Clube
            // que folgou não tem titular: o elenco inteiro recupera.
            val starterIds =
                if (club.id in playedIds) club.startingLineup().players.map { it.id }.toSet()
                else emptySet()

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
                    focus = current.trainingFocus,
                    rng = Random(trainingSeed(roundNumber, current.id)),
                )
                report = TrainingReport(current.id, current.trainingFocus, trained.events)

                // Caixa é aplicado como DELTA sobre o clube relido — é isso que
                // impede a rodada de apagar um débito concorrente (ampliar estádio,
                // compra) ou vice-versa (issue #89).
                val finance = financeByClub[current.id.value]
                val newCash = if (finance == null) current.cash
                    else (current.cash + finance.cashDelta()).coerceAtLeast(0)

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
