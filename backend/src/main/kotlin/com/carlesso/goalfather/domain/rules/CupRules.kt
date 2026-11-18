package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Competition
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.CupClub
import com.carlesso.goalfather.domain.model.CupLeg
import com.carlesso.goalfather.domain.model.CupPhase
import com.carlesso.goalfather.domain.model.CupStage
import com.carlesso.goalfather.domain.model.CupTie
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.KnockoutStage
import com.carlesso.goalfather.domain.model.LegScore
import com.carlesso.goalfather.domain.model.Legs
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.TieResult
import kotlin.random.Random

/**
 * Regras da copa mata-mata (issues #61 e #131). Funções PURAS: o sorteio
 * recebe o `Random` por parâmetro (seed por temporada, [cupDrawSeed]), os
 * sorteios das fases seguintes saem da `Cup.drawSeed` que ele fixa, e o avanço
 * da chave é determinístico — o mesmo sorteio com os mesmos resultados produz
 * sempre a mesma copa.
 */

/**
 * Seed do sorteio da copa. Mesmo empacotamento de [agingSeed] — temporada
 * nos bits altos — com um salt próprio, para o sorteio não repetir a
 * sequência de nenhum outro sorteio da mesma temporada.
 */
fun cupDrawSeed(season: Int): Long = (season.toLong() shl 32) xor CUP_SEED_SALT

/** "CUP" em hexa — só para descolar o stream do sorteio dos demais. */
private const val CUP_SEED_SALT: Long = 0x43_5550L

// ─── Sorteio ──────────────────────────────────────────────────────────────

/**
 * Sorteia a copa de [season] no formato da [competition] (issue #131).
 *
 * [seeded] vem do mais forte ao mais fraco, e a ordem IMPORTA: os mais fortes
 * entram nas fases mais tardias. Andando da última fase para a primeira, cada
 * fase com entrantes pega o TOPO do que sobrou da lista — na Copa do Brasil do
 * MVP, os 24 primeiros (20 da Série A + 4 da B) entram na 5ª fase e os 16
 * restantes jogam a 4ª. Só a fase de abertura é sorteada agora; as seguintes,
 * quando a anterior termina ([recordRound]).
 *
 * Formato que não é `Knockout`, ou nº de clubes que não fecha com os entrantes
 * do formato, é `require`: o catálogo que diz "esta é uma copa nacional" e o
 * formato dela vêm do pack, e um pack incoerente é erro de configuração do
 * mundo — não um desfecho de jogo que o chamador pudesse tratar, como o
 * `error` de `seasonPlan` para uma liga que não cabe no calendário.
 */
fun drawCup(season: Int, competition: Competition, seeded: List<Club>, rng: Random): Cup {
    val format = competition.format
    // `require` tem contrato (`returns() implies value`): depois dele o
    // compilador sabe que `format` É um `Knockout` — smart cast sem `as`.
    require(format is CompetitionFormat.Knockout) {
        "Copa ${competition.id.value} precisa de formato Knockout: ${format::class.simpleName}"
    }
    require(seeded.size == format.entrants) {
        "Copa ${competition.id.value} espera ${format.entrants} clubes: ${seeded.size}"
    }
    require(seeded.distinctBy { it.id }.size == seeded.size) { "Clube repetido na copa ${competition.id.value}" }

    val clubs = seeded.map { CupClub(it.id, it.name) }
    val phases = format.stages.zip(entrantsByStage(format.stages, clubs)) { stage, entrants ->
        CupPhase(stage.name, stage.teams, stage.legs, entrants)
    }
    val opening = phases.first()
    val firstStage = drawStage(opening, opening.entrants, rng)
    return Cup(
        season = season,
        competitionId = competition.id,
        competitionName = competition.name,
        phases = phases,
        stages = listOf(firstStage),
        drawSeed = rng.nextLong(),
    )
}

/**
 * Quem entra em cada fase: da última para a primeira, cada uma tira do topo de
 * [seeded] os seus `newEntrants`. Para a fase `i`, o início do recorte é a soma
 * dos entrantes das fases DEPOIS dela — `subList` devolve uma visão, e o
 * `toList` a copia para a copa não segurar a lista do chamador.
 */
private fun entrantsByStage(stages: List<KnockoutStage>, seeded: List<CupClub>): List<List<CupClub>> =
    stages.indices.map { i ->
        val start = stages.drop(i + 1).sumOf { it.newEntrants }
        seeded.subList(start, start + stages[i].newEntrants).toList()
    }

/**
 * Sorteia os confrontos de [phase] entre [clubs] — sorteio livre, sem potes
 * (as restrições da CBF, como evitar dois clubes do mesmo estado, ficam fora
 * do MVP). O primeiro de cada par sorteado tem o mando do jogo único / da ida.
 */
private fun drawStage(phase: CupPhase, clubs: List<CupClub>, rng: Random): CupStage {
    require(clubs.size == phase.teams) { "Fase ${phase.name} espera ${phase.teams} clubes: ${clubs.size}" }
    val byId = clubs.associateBy { it.id }
    val ties = drawKnockoutPairings(clubs.map { it.id }, rng).mapIndexed { index, pairing ->
        val home = byId.getValue(pairing.home)
        val away = byId.getValue(pairing.away)
        when (phase.legs) {
            Legs.SINGLE -> CupTie.Single(index, home, away)
            Legs.TWO_LEGGED -> CupTie.TwoLegged(index, home, away)
        }
    }
    return CupStage(phase.name, ties)
}

// ─── Prêmios ──────────────────────────────────────────────────────────────

/** Cheque de quem vence a final — o campeão leva o maior prêmio da copa. */
val CUP_FINAL_PRIZE: Money = Money.dollars(1_000_000)

/** Vencer a semifinal. Cada fase antes dela paga a METADE da seguinte. */
val CUP_SEMI_FINAL_PRIZE: Money = Money.dollars(400_000)

/** Piso: a escada de metades não desce disto, por mais fases que o formato tenha. */
val CUP_MIN_PRIZE: Money = Money.dollars(10_000)

/**
 * Prêmio por confronto VENCIDO numa fase que fica a [phasesToFinal] fases da
 * final (0 = a própria final). A regra é por ORDEM, não por nome, para valer
 * para qualquer formato do pack: final US$ 1 mi; semifinal US$ 400 mil; cada
 * fase anterior, metade da seguinte (quartas 200 mil, oitavas 100 mil, 5ª fase
 * 50 mil, 4ª fase 25 mil), com piso de US$ 10 mil.
 *
 * Calibrada contra o CAIXA (revisão da PR #154), na mesma proporção do jogo
 * antes da #131: lá, a campanha do campeão somava US$ 215 mil para um caixa
 * de US$ 160 mil (~1,3×). Aqui, a do campeão que entra na 5ª fase soma
 * US$ 1,75 mi, ~1,3× o caixa do Flamengo (US$ 1,33 mi) e ~17 folhas dele.
 * A escala anterior (final US$ 10 mi) pagava 7× o caixa do maior clube do
 * Brasil numa partida e tirava do jogo a restrição financeira. Premiar a vitória (e não a participação, como a CBF) é
 * herança da issue #61: o prêmio é "por fase avançada".
 */
fun cupPhasePrize(phasesToFinal: Int): Money {
    require(phasesToFinal >= 0) { "Fase depois da final: $phasesToFinal" }
    if (phasesToFinal == 0) return CUP_FINAL_PRIZE
    // `shr` = divisão por 2^n em inteiro; o `coerceAtMost` evita o deslocamento
    // "dar a volta" (em Long, `x shr 64` é `x shr 0`).
    val halved = CUP_SEMI_FINAL_PRIZE.cents shr (phasesToFinal - 1).coerceAtMost(62)
    return maxOf(Money(halved), CUP_MIN_PRIZE)
}

/** Prêmio de quem vence um confronto da fase de índice [phaseIndex] desta copa. */
fun Cup.prizeOf(phaseIndex: Int): Money = cupPhasePrize(phases.lastIndex - phaseIndex)

// ─── Avanço da chave ──────────────────────────────────────────────────────

/**
 * O que uma perna disputada produziu: a copa atualizada (resultados gravados
 * e, se a fase fechou, a seguinte já sorteada) e o prêmio de cada clube que
 * passou de fase NESTA perna.
 */
data class CupRoundOutcome(
    val cup: Cup,
    val prizes: Map<ClubId, Money>,
)

/**
 * Registra na fase corrente os placares de um encaixe já encerrado — a perna
 * em aberto de cada confronto — e avança a chave.
 *
 * - Jogo único: decide no placar ou nos pênaltis.
 * - Ida: só guarda o placar (empate é um placar como outro qualquer).
 * - Volta: decide pelo AGREGADO; empatado, pelos pênaltis cobrados ao fim da
 *   volta. NÃO existe gol fora de casa: a CBF não o usa na Copa do Brasil, e a
 *   UEFA e a CONMEBOL também o aboliram — ver `TwoLeggedTieResult`.
 *
 * Quando a fase fecha, a seguinte é SORTEADA (como na Copa do Brasil, que
 * sorteia fase a fase) entre os vencedores + os entrantes dela, com
 * `Random(drawSeed + índice da fase)`: determinístico e independente de
 * quando a fase acabou.
 *
 * Premiação "por fase avançada" (issue #61): quem passa de fase leva
 * [prizeOf] da fase. Só confrontos decididos NESTA chamada pagam, então
 * reaplicar o mesmo encaixe não premia duas vezes.
 *
 * Partida sem placar final, ou que deveria ter ido aos pênaltis e não foi
 * (encaixe simulado fora do modo mata-mata), deixa o confronto em aberto em
 * vez de estourar: o `TieResult`/`TwoLeggedTieResult` recusariam o resultado,
 * e uma exception aqui travaria o mundo depois do claim da rodada (issue #46).
 * A perna em aberto volta na próxima data de copa.
 */
fun Cup.recordRound(round: Round): CupRoundOutcome {
    val stage = currentStage ?: return CupRoundOutcome(this, emptyMap())
    val phaseIndex = stages.lastIndex
    // Só os jogos DESTA copa no encaixe (issue #125): um jogo de liga entre os
    // mesmos dois clubes no mesmo encaixe não pode decidir confronto nenhum.
    val played = round.matches
        .filter { it.status == RoundStatus.Finished && it.stage is RoundStage.Cup && it.competition == competitionId }
        .associateBy { it.homeClubId to it.awayClubId }

    val recorded = stage.copy(ties = stage.ties.map { it.record(played) })
    val prize = prizeOf(phaseIndex)
    // `zip` pareia antes × depois; passou de fase quem não tinha vencedor e agora tem.
    val prizes = stage.ties.zip(recorded.ties)
        .mapNotNull { (before, after) -> after.winner.takeIf { before.winner == null } }
        .associate { it.id to prize }

    val nextPhase = phases.getOrNull(phaseIndex + 1)?.takeIf { recorded.isComplete }
    val next = nextPhase?.let { phase ->
        val winners = recorded.ties.mapNotNull { it.winner }
        drawStage(phase, winners + phase.entrants, Random(drawSeed + phaseIndex + 1))
    }
    return CupRoundOutcome(
        cup = copy(stages = stages.dropLast(1) + recorded + listOfNotNull(next)),
        prizes = prizes,
    )
}

/**
 * Grava a perna em aberto deste confronto, se ela foi jogada em [played]. O
 * `when` sobre o `sealed` [CupTie] obriga a tratar jogo único e ida e volta.
 */
private fun CupTie.record(played: Map<Pair<ClubId, ClubId>, RoundMatch>): CupTie = when (this) {
    is CupTie.Single ->
        if (result != null) this else played[home.id to away.id]?.toTieResult()?.let { copy(result = it) } ?: this

    is CupTie.TwoLegged -> when {
        firstLeg == null ->
            played[home.id to away.id]?.let { copy(firstLeg = LegScore(it.homeGoals, it.awayGoals)) } ?: this
        // Na volta o mando se inverte: o jogo é away × home.
        secondLeg == null -> played[away.id to home.id]?.let { recordSecondLeg(firstLeg, it) } ?: this
        else -> this
    }
}

/** Volta jogada → confronto decidido, ou o mesmo confronto se o agregado empatou sem pênaltis. */
private fun CupTie.TwoLegged.recordSecondLeg(firstLeg: LegScore, match: RoundMatch): CupTie.TwoLegged {
    val secondLeg = LegScore(match.homeGoals, match.awayGoals)
    if (!secondLegNeedsShootout(firstLeg, secondLeg)) return copy(secondLeg = secondLeg)
    val penalties = match.penalties?.takeIf { it.home != it.away } ?: return this
    return copy(secondLeg = secondLeg, penalties = penalties)
}

/** Placar → resultado de jogo único; `null` se o placar não decide ninguém. */
private fun RoundMatch.toTieResult(): TieResult? =
    when {
        homeGoals != awayGoals -> TieResult(homeGoals, awayGoals)
        penalties != null && penalties.home != penalties.away -> TieResult(homeGoals, awayGoals, penalties)
        else -> null
    }

// ─── Jogos do encaixe e pênaltis ──────────────────────────────────────────

/**
 * Os jogos de copa do encaixe [matchday]: a perna em ABERTO de cada confronto
 * da fase corrente — a ida (ou o jogo único) enquanto ela não foi jogada, a
 * volta (mando invertido) depois. O `matchId` segue a fórmula da liga sem o
 * dígito da divisão — `encaixe × 1000 + índice + 1` —, que nenhuma partida de
 * liga usa (a delas começa em `× 1000 + 101`) enquanto a fase tiver até 100
 * confrontos: liga e copa podem até dividir um encaixe sem colidir. É também a
 * seed da simulação, então "mesma seed = mesma disputa" vale para os pênaltis.
 *
 * A `division` de cada partida é a do mandante — a copa mistura divisões, e é
 * esse o dado que deixa a UI mostrar o "clube pequeno" recebendo o grande.
 */
fun cupMatches(
    matchday: Matchday,
    cup: Cup,
    clubs: Collection<Club>,
    // Onde começa a faixa de `matchId` desta copa no encaixe. A continental
    // (issue #132) usa outra faixa ([CONTINENTAL_FIRST_MATCH_INDEX]) para nunca
    // colidir com a copa nacional.
    firstIndex: Int = 1,
): List<RoundMatch> {
    val stage = cup.currentStage ?: return emptyList()
    val divisionOf = clubs.associate { it.id to it.division }
    return stage.ties.mapNotNull { tie ->
        val leg = tie.openLeg ?: return@mapNotNull null
        val (home, away) = when (leg) {
            is CupLeg.Second -> tie.away to tie.home
            CupLeg.Single, CupLeg.First -> tie.home to tie.away
        }
        RoundMatch(
            matchId = matchday.serial * Matchday.MATCH_IDS_PER_MATCHDAY + tie.index + firstIndex,
            homeClubId = home.id,
            awayClubId = away.id,
            homeClubName = home.name,
            awayClubName = away.name,
            division = divisionOf[home.id] ?: Division.FIRST,
            stage = RoundStage.Cup(stage.name, leg),
            competition = cup.competitionId,
            competitionName = cup.competitionName,
        )
    }
}

/**
 * Um jogo desta perna que termina [homeGoals] × [awayGoals] vai aos pênaltis?
 *
 * - jogo único: se empatou;
 * - ida: nunca — empate na ida é só um placar;
 * - volta: se o AGREGADO empatou ([secondLegNeedsShootout], issue #124).
 */
fun CupLeg.needsShootout(homeGoals: Int, awayGoals: Int): Boolean = when (this) {
    CupLeg.Single -> homeGoals == awayGoals
    CupLeg.First -> false
    is CupLeg.Second -> secondLegNeedsShootout(firstLeg, LegScore(homeGoals, awayGoals))
}

/**
 * A pergunta que a engine faz no minuto 90 (`MatchSetup.decidesOnPenalties`):
 * este jogo, com este placar, vai aos pênaltis? Liga nunca; copa, pela perna.
 * Os serviços passam a referência `match::decidesOnPenalties` — uma extension
 * function também vira valor de função com o receptor já "amarrado".
 */
fun RoundMatch.decidesOnPenalties(homeGoals: Int, awayGoals: Int): Boolean = when (val stage = stage) {
    is RoundStage.League, is RoundStage.Group -> false
    is RoundStage.Cup -> stage.leg.needsShootout(homeGoals, awayGoals)
}
