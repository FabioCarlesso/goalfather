package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.metrics.GoalfatherMetrics
import com.carlesso.goalfather.domain.engine.MatchSetup
import com.carlesso.goalfather.domain.engine.MatchSimulator
import com.carlesso.goalfather.domain.engine.QuickMatchResult
import com.carlesso.goalfather.domain.engine.QuickMatchSimulator
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.PenaltyScore
import com.carlesso.goalfather.domain.model.RoundMatch
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlin.random.Random

/**
 * Como uma partida do encaixe é simulada (issue #129). `enum` basta: os dois
 * modos não carregam dado próprio — o que muda é o RESULTADO, modelado em
 * [SimulatedMatch].
 */
enum class SimulationMode {
    /** Engine completa, minuto a minuto, transmitida pelo WS: há técnico assistindo. */
    FULL,

    /** [QuickMatchSimulator]: só placar e efeitos, sem transmissão. Jogo entre clubes da IA. */
    QUICK,
}

/**
 * Jogo com técnico humano em QUALQUER lado vai para a engine completa — é o
 * jogo que alguém está assistindo. Clube ausente (`null`) conta como IA.
 *
 * Decidido pela posse NA HORA da simulação: o drill-down de um encaixe já
 * jogado (`PlayMatchService`) relê os clubes, então uma troca de dono depois do
 * jogo muda o modo — mesma ressalva que já valia para elenco alterado depois.
 */
fun simulationModeOf(home: Club?, away: Club?): SimulationMode =
    if (home?.ownerId != null || away?.ownerId != null) SimulationMode.FULL else SimulationMode.QUICK

/** Uma partida do encaixe pronta para simular: o jogo, os elencos e o modo. */
data class MatchFixture(
    val match: RoundMatch,
    val setup: MatchSetup,
    val mode: SimulationMode,
)

/**
 * Uma partida já simulada, nos dois sabores. `sealed` para que cada consumidor
 * leia as duas formas pelas MESMAS propriedades — o stream e os efeitos da
 * rodada não precisam saber qual engine rodou.
 */
sealed interface SimulatedMatch {
    val match: RoundMatch

    /** O que vai para o WebSocket da rodada (`RoundEvent.MatchUpdate`). */
    val broadcast: List<MatchEvent>

    /** Os eventos que alimentam os efeitos (gols, cartões, lesões na ficha). */
    val sheet: List<MatchEvent>

    /**
     * Placar dos 90 minutos. Nulo só se a engine não fechou o jogo (sem
     * `FullTime`) — a partida então fica como estava, como antes da issue.
     */
    val score: Pair<Int, Int>?

    /** Disputa de pênaltis, quando o mata-mata empatou. */
    val penalties: PenaltyScore?

    /** Engine completa: o feed inteiro é transmitido e é, ele mesmo, a súmula. */
    data class Full(override val match: RoundMatch, val events: List<MatchEvent>) : SimulatedMatch {
        override val broadcast: List<MatchEvent> get() = events
        override val sheet: List<MatchEvent> get() = events
        override val score: Pair<Int, Int>? =
            events.filterIsInstance<MatchEvent.FullTime>().lastOrNull()?.let { it.homeGoals to it.awayGoals }
        override val penalties: PenaltyScore? =
            events.filterIsInstance<MatchEvent.PenaltyShootout>().lastOrNull()
                ?.let { PenaltyScore(it.homeScore, it.awayScore) }
    }

    /**
     * Jogo rápido: no WS só o DESFECHO — pênaltis (se houve) e o `FullTime`,
     * que já basta para a tela da rodada fechar o placar daquele jogo. Os
     * destaques (gols/cartões/lesões) vão para os efeitos, não para o stream.
     */
    data class Quick(override val match: RoundMatch, val result: QuickMatchResult) : SimulatedMatch {
        override val broadcast: List<MatchEvent> get() = listOfNotNull(result.shootout) + result.fullTime
        override val sheet: List<MatchEvent> get() = result.summary()
        override val score: Pair<Int, Int> get() = result.homeGoals to result.awayGoals
        override val penalties: PenaltyScore? get() = result.shootout?.let { PenaltyScore(it.homeScore, it.awayScore) }
    }
}

/**
 * Simula as partidas de um encaixe (issue #129), separando os jogos com
 * técnico humano (engine completa) dos demais (rápida, em paralelo).
 *
 * **Determinismo.** Cada partida usa `Random(matchId)` — a mesma regra de
 * sempre —, criado DENTRO da tarefa dela. Nenhum RNG é compartilhado entre
 * threads, então rodar em paralelo não muda resultado nenhum: a ordem em que
 * as coroutines terminam é irrelevante, e o resultado volta na ordem das
 * [MatchFixture]s recebidas.
 *
 * Colaborador sem Spring, como os services: o `dispatcher` é injetável para os
 * testes (e é `Dispatchers.Default` em produção — o pool dimensionado pelo nº de
 * CPUs, feito para trabalho de CPU como simular partidas; `Dispatchers.IO`
 * seria para bloqueio de I/O).
 */
class MatchdaySimulator(
    private val fullEngine: MatchSimulator = MatchSimulator(),
    private val quickEngine: QuickMatchSimulator = QuickMatchSimulator(),
    private val meterRegistry: MeterRegistry,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val quickTimer: Timer = meterRegistry.timer(GoalfatherMetrics.QUICK_SIMULATION)

    suspend fun simulate(fixtures: List<MatchFixture>): List<SimulatedMatch> {
        val (full, quick) = fixtures.partition { it.mode == SimulationMode.FULL }

        // Jogos com humano: poucos (um por técnico), sequenciais, no contexto de
        // quem chamou — exatamente como antes da issue.
        val fullResults = full.map { fixture ->
            SimulatedMatch.Full(fixture.match, fullEngine.simulate(fixture.setup, Random(fixture.match.matchId)).toList())
        }
        val quickResults = simulateQuick(quick)

        // Devolve na ordem do encaixe: o stream depende dela para desempatar
        // eventos do mesmo minuto (`sortBy` é estável).
        val byMatch = (fullResults + quickResults).associateBy { it.match.matchId }
        return fixtures.map { byMatch.getValue(it.match.matchId) }
    }

    /**
     * Os jogos rápidos, todos em paralelo — concorrência ESTRUTURADA:
     *
     * - `coroutineScope { }` abre um escopo filho e só retorna quando TODOS os
     *   filhos terminaram. Não existe "fire and forget": nenhuma simulação fica
     *   rodando depois que esta função devolveu.
     * - Se uma simulação lançar, o escopo cancela as irmãs e repassa a exception
     *   para quem chamou; e se quem chamou for cancelado, o cancelamento desce
     *   para todas. É o contrário de um `GlobalScope.launch`, em que um jogo
     *   falho deixaria os outros pendurados.
     * - `async(dispatcher)` cria um filho por jogo que devolve um `Deferred`;
     *   `awaitAll()` espera todos e entrega os resultados NA ORDEM da lista, não
     *   na ordem em que terminaram.
     *
     * O timer cobre o lote inteiro (wall time): é o "quanto o encaixe esperou
     * pelos jogos da IA", o número do critério de < 1 s da issue.
     */
    private suspend fun simulateQuick(fixtures: List<MatchFixture>): List<SimulatedMatch> {
        if (fixtures.isEmpty()) return emptyList()
        val sample = Timer.start(meterRegistry)
        val results = coroutineScope {
            fixtures.map { fixture ->
                async(dispatcher) {
                    SimulatedMatch.Quick(fixture.match, quickEngine.simulate(fixture.setup, Random(fixture.match.matchId)))
                }
            }.awaitAll()
        }
        sample.stop(quickTimer)
        return results
    }
}
