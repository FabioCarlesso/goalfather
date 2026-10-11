package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.CupClub
import com.carlesso.goalfather.domain.model.CupStage
import com.carlesso.goalfather.domain.model.CupTie
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.KnockoutPhase
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.TieResult
import kotlin.random.Random

/**
 * Regras da copa mata-mata (issue #61). Funções PURAS: o sorteio recebe o
 * `Random` por parâmetro (seed por temporada, [cupDrawSeed]) e o avanço da
 * chave é determinístico — o mesmo sorteio com os mesmos resultados produz
 * sempre a mesma copa.
 */

/** Menos que isso a "copa" seria só uma final repetindo um jogo da liga. */
const val MIN_CUP_CLUBS: Int = 4

/** Maior chave suportada por [KnockoutPhase] (32-avos). */
const val MAX_CUP_CLUBS: Int = 32

/** A liga comporta uma copa? Fora da faixa a temporada é só de pontos corridos. */
fun canHoldCup(clubs: Collection<Club>): Boolean = clubs.size in MIN_CUP_CLUBS..MAX_CUP_CLUBS

/**
 * Seed do sorteio da copa. Mesmo empacotamento de [agingSeed] — temporada
 * nos bits altos — com um salt próprio, para o sorteio não repetir a
 * sequência de nenhum outro sorteio da mesma temporada.
 */
fun cupDrawSeed(season: Int): Long = (season.toLong() shl 32) xor CUP_SEED_SALT

/** "CUP" em hexa — só para descolar o stream do sorteio dos demais. */
private const val CUP_SEED_SALT: Long = 0x43_5550L

/**
 * Sorteia a chave da copa: todos os clubes, embaralhados com [rng], numa
 * chave com a menor potência de 2 que os comporta. As vagas que sobram viram
 * folgas ([CupTie.Bye]) — 12 clubes numa chave de 16 dão 4 folgas e 4 jogos.
 *
 * As folgas ocupam primeiro os confrontos de índice PAR (0, 2, 4, …). Como os
 * vencedores de `2k` e `2k+1` se cruzam na fase seguinte, isso faz cada
 * clube que folgou estrear contra quem venceu um jogo — em vez de duas folgas
 * se encontrarem e um clube chegar às quartas sem ter entrado em campo.
 *
 * Os clubes são ordenados por id ANTES do embaralhamento: a mesma liga
 * listada em outra ordem (o repositório não promete ordem) sorteia igual.
 */
fun drawCup(season: Int, clubs: Collection<Club>, rng: Random): Cup {
    require(canHoldCup(clubs)) {
        "Copa exige de $MIN_CUP_CLUBS a $MAX_CUP_CLUBS clubes: ${clubs.size}"
    }
    // `takeHighestOneBit` (stdlib Kotlin) — potência de 2 imediatamente
    // abaixo; `(n - 1)` + `shl 1` dá a menor potência de 2 que é >= n.
    val bracketSize = (clubs.size - 1).takeHighestOneBit() shl 1
    val tieCount = bracketSize / 2
    val byes = bracketSize - clubs.size
    val byeSlots = ((0 until tieCount step 2) + (1 until tieCount step 2)).take(byes).toSet()

    val drawn = ArrayDeque(clubs.sortedBy { it.id.value }.shuffled(rng).map { CupClub(it.id, it.name) })
    val ties = (0 until tieCount).map { index ->
        if (index in byeSlots) {
            CupTie.Bye(index, drawn.removeFirst())
        } else {
            CupTie.Fixture(index, home = drawn.removeFirst(), away = drawn.removeFirst())
        }
    }
    return Cup(season, listOf(CupStage(KnockoutPhase.opening(bracketSize), ties)))
}

/**
 * O que uma fase disputada produziu: a copa atualizada (resultados gravados
 * e, se a fase fechou, a seguinte já sorteada pela chave) e o prêmio de cada
 * clube que avançou EM CAMPO.
 */
data class CupRoundOutcome(
    val cup: Cup,
    val prizes: Map<ClubId, Long>,
)

/**
 * Registra na fase corrente os placares de uma rodada de copa já encerrada e
 * avança a chave: quando a fase fecha, os vencedores de `2k` e `2k+1` se
 * enfrentam na fase seguinte, o de `2k` com mando. Não há novo sorteio — o
 * caminho até a final foi definido no [drawCup], como numa copa de verdade.
 *
 * Premiação (issue #61, "por fase avançada"): quem vence um confronto leva
 * `phase.prizeCents` da fase em que venceu. Folga não premia — o clube avança,
 * mas não ganhou jogo nenhum. Só confrontos decididos NESTA chamada pagam, então
 * reaplicar a mesma rodada não premia duas vezes.
 *
 * Partida sem placar final, ou empatada sem pênaltis (rodada simulada fora do
 * modo mata-mata), deixa o confronto em aberto em vez de estourar: o
 * `TieResult` recusaria o empate, e uma exception aqui travaria a liga inteira
 * depois do claim da rodada (issue #46). O confronto em aberto volta na
 * próxima data de copa.
 */
fun Cup.recordRound(round: Round): CupRoundOutcome {
    val stage = currentStage ?: return CupRoundOutcome(this, emptyMap())
    val played = round.matches
        .filter { it.status == RoundStatus.Finished }
        .associateBy { it.homeClubId to it.awayClubId }

    val prizes = mutableMapOf<ClubId, Long>()
    val ties = stage.ties.map { tie ->
        when (tie) {
            is CupTie.Bye -> tie
            is CupTie.Fixture -> {
                val match = played[tie.home.id to tie.away.id]
                val result = match?.toTieResult()
                if (tie.result != null || result == null) {
                    tie
                } else {
                    tie.copy(result = result).also { decided ->
                        decided.winner?.let { prizes[it.id] = stage.phase.prizeCents }
                    }
                }
            }
        }
    }

    val recorded = stage.copy(ties = ties)
    val next = recorded.phase.next?.takeIf { recorded.isComplete }?.let { nextStage(it, recorded) }
    return CupRoundOutcome(
        cup = copy(stages = stages.dropLast(1) + recorded + listOfNotNull(next)),
        prizes = prizes,
    )
}

/** Placar → resultado de mata-mata; `null` se o placar não decide ninguém. */
private fun RoundMatch.toTieResult(): TieResult? =
    when {
        homeGoals != awayGoals -> TieResult(homeGoals, awayGoals)
        penalties != null && penalties.home != penalties.away -> TieResult(homeGoals, awayGoals, penalties)
        else -> null
    }

/** Cruza os vencedores da fase anterior, na ordem da chave. */
private fun nextStage(phase: KnockoutPhase, previous: CupStage): CupStage {
    val winners = previous.ties.sortedBy { it.index }.map { tie ->
        requireNotNull(tie.winner) { "Fase ${previous.phase} completa com confronto sem vencedor" }
    }
    // `chunked(2)` + desestruturação de lista: cada par vira um confronto.
    val ties = winners.chunked(2).mapIndexed { index, (home, away) ->
        CupTie.Fixture(index, home = home, away = away)
    }
    return CupStage(phase, ties)
}

/**
 * Monta a rodada de copa da data [roundNumber]: os confrontos ainda em aberto
 * da fase corrente, em jogo único. O `matchId` segue a fórmula das rodadas de
 * liga sem o dígito da divisão — `rodada × 1000 + índice` —, que nenhuma
 * partida de liga usa (a delas começa em `× 1000 + 101`). É também a seed da
 * simulação, então "mesma seed = mesma disputa" vale para os pênaltis.
 *
 * A `division` de cada partida é a do mandante — a copa mistura divisões, e é
 * esse o dado que deixa a UI mostrar o "clube pequeno" recebendo o grande.
 */
fun cupRound(
    roundNumber: Int,
    season: Int,
    phase: KnockoutPhase,
    cup: Cup,
    clubs: Collection<Club>,
): Round {
    val divisionOf = clubs.associate { it.id to it.division }
    val pending = cup.currentStage?.ties.orEmpty()
        .filterIsInstance<CupTie.Fixture>()
        .filter { it.result == null }

    return Round(
        number = roundNumber,
        season = season,
        status = RoundStatus.Scheduled,
        stage = RoundStage.Cup(cup.currentStage?.phase ?: phase),
        matches = pending.map { tie ->
            RoundMatch(
                matchId = roundNumber * 1000L + tie.index + 1,
                homeClubId = tie.home.id,
                awayClubId = tie.away.id,
                homeClubName = tie.home.name,
                awayClubName = tie.away.name,
                division = divisionOf[tie.home.id] ?: Division.FIRST,
            )
        },
    )
}
