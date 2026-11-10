package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.LegScore
import com.carlesso.goalfather.domain.model.Legs
import com.carlesso.goalfather.domain.model.Pairing
import com.carlesso.goalfather.domain.model.SwissPlacement
import kotlin.random.Random

/**
 * Geradores de jogos dos formatos de competição (issue #124). Funções PURAS:
 * o que é sorteio recebe o `Random` por parâmetro, e as entradas são ordenadas
 * por id antes de qualquer embaralhamento — a mesma lista em outra ordem gera
 * os mesmos jogos (o repositório não promete ordem).
 *
 * A saída é sempre `List<List<Pairing>>`: uma lista de RODADAS, cada uma com
 * os pareamentos em que nenhum clube aparece duas vezes. Encaixar essas rodadas
 * em datas do calendário é trabalho da issue #125.
 */

// ─── Pontos corridos (Berger) ─────────────────────────────────────────────

/**
 * Pareamentos da rodada [matchday] (1-based) de um turno único entre [clubs],
 * pelo método de Berger: o clube de menor id fica fixo e os demais giram.
 *
 * Com nº ímpar de clubes, um "fantasma" completa a roda e quem o enfrenta folga
 * naquela rodada — por isso o turno de N ímpar tem N rodadas, e o de N par, N-1.
 *
 * O mando segue a regra que a liga do jogo sempre usou (`order[i]` recebe
 * `order[n-1-i]`), para que o seed atual gere exatamente os mesmos jogos.
 * O turno fica desequilibrado em mando, mas o returno espelha: no total cada
 * clube recebe metade dos jogos. Alternar o mando DENTRO do turno é refinamento
 * do agendador (issue #125).
 */
fun roundRobinMatchday(clubs: List<ClubId>, matchday: Int): List<Pairing> {
    require(clubs.size >= 2) { "Pontos corridos precisa de ao menos 2 clubes: ${clubs.size}" }
    require(clubs.distinct().size == clubs.size) { "Clube repetido na competição: $clubs" }
    val rounds = turnRounds(clubs.size)
    require(matchday in 1..rounds) { "Rodada $matchday fora do turno 1..$rounds" }

    // `null` é o fantasma: `List<ClubId?>` deixa a folga explícita no tipo.
    val ordered: List<ClubId?> = clubs.sortedBy { it.value } + if (clubs.size % 2 == 1) listOf(null) else emptyList()
    val n = ordered.size
    val fixed = ordered.first()
    val rotating = ordered.drop(1)
    val offset = (matchday - 1) % (n - 1)
    val order = listOf(fixed) + rotating.drop(offset) + rotating.take(offset)

    return (0 until n / 2).mapNotNull { i ->
        val home = order[i]
        val away = order[n - 1 - i]
        if (home == null || away == null) null else Pairing(home, away)
    }
}

/** Rodadas de UM turno: N-1 para N par, N para N ímpar (uma folga por rodada). */
fun turnRounds(clubCount: Int): Int = if (clubCount % 2 == 0) clubCount - 1 else clubCount

/**
 * Tabela completa de pontos corridos com [legs] turnos. Os turnos pares
 * espelham os ímpares com o mando invertido — no turno e returno cada clube
 * recebe e visita cada adversário exatamente uma vez.
 */
fun roundRobinSchedule(clubs: List<ClubId>, legs: Int): List<List<Pairing>> {
    require(legs >= 1) { "Ao menos 1 turno: $legs" }
    val turn = (1..turnRounds(clubs.size)).map { roundRobinMatchday(clubs, it) }
    return (0 until legs).flatMap { leg ->
        if (leg % 2 == 0) turn else turn.map { round -> round.map(Pairing::reversed) }
    }
}

// ─── Mata-mata ────────────────────────────────────────────────────────────

/**
 * Sorteia os confrontos de uma fase de mata-mata entre [clubs] (nº par). O
 * primeiro de cada par sorteado tem o mando da ida — no jogo único, o mando.
 */
fun drawKnockoutPairings(clubs: List<ClubId>, rng: Random): List<Pairing> {
    require(clubs.size >= 2 && clubs.size % 2 == 0) { "Fase de mata-mata precisa de nº par de clubes: ${clubs.size}" }
    require(clubs.distinct().size == clubs.size) { "Clube repetido no sorteio: $clubs" }
    // `chunked(2)` + desestruturação: cada par vira um confronto.
    return clubs.sortedBy { it.value }.shuffled(rng).chunked(2).map { (home, away) -> Pairing(home, away) }
}

/** Os jogos de uma fase: só a ida no jogo único; ida e volta com mando invertido. */
fun knockoutLegs(pairings: List<Pairing>, legs: Legs): List<List<Pairing>> = when (legs) {
    Legs.SINGLE -> listOf(pairings)
    Legs.TWO_LEGGED -> listOf(pairings, pairings.map(Pairing::reversed))
}

/**
 * O jogo de volta terminou com o agregado empatado? Então a disputa vai aos
 * pênaltis (a engine os cobra com `penaltyShootout`, issue #61) — e só no
 * jogo de volta: a ida empatada é só um placar.
 */
fun secondLegNeedsShootout(firstLeg: LegScore, secondLeg: LegScore): Boolean =
    firstLeg.homeGoals + secondLeg.awayGoals == firstLeg.awayGoals + secondLeg.homeGoals

// ─── Grupos ───────────────────────────────────────────────────────────────

/**
 * Sorteia os grupos por POTES: [seeded] vem ordenado do mais forte para o mais
 * fraco e é cortado em potes de [groups] clubes; cada grupo recebe um clube de
 * cada pote. É assim que Libertadores e Copa do Mundo evitam um "grupo da morte"
 * de cabeças de chave.
 *
 * Aqui a ordem de [seeded] IMPORTA (é o ranking), então ela não é reordenada
 * por id. Restrições extras do sorteio (sem dois clubes do mesmo país no mesmo
 * grupo) entram com a Libertadores, na issue #132.
 */
fun drawGroups(seeded: List<ClubId>, format: CompetitionFormat.GroupsThenKnockout, rng: Random): List<List<ClubId>> {
    require(seeded.size == format.teams) { "Fase de grupos espera ${format.teams} clubes: ${seeded.size}" }
    require(seeded.distinct().size == seeded.size) { "Clube repetido no sorteio: $seeded" }
    val pots = seeded.chunked(format.groups).map { it.shuffled(rng) }
    return (0 until format.groups).map { group -> pots.map { pot -> pot[group] } }
}

/** Rodadas da fase de grupos: os pontos corridos de cada grupo, rodada a rodada, juntos. */
fun groupStageSchedule(groups: List<List<ClubId>>, legs: Int): List<List<Pairing>> {
    val perGroup = groups.map { roundRobinSchedule(it, legs) }
    val rounds = perGroup.maxOf { it.size }
    return (0 until rounds).map { r -> perGroup.flatMap { it.getOrNull(r).orEmpty() } }
}

// ─── Fase de liga suíça ───────────────────────────────────────────────────

/**
 * Tabela da fase de liga suíça: cada clube faz `matchesPerTeam` jogos contra
 * adversários diferentes, metade em casa.
 *
 * O sorteio real da UEFA usa potes e um software de restrições. A versão do
 * jogo usa um **grafo circulante**, que garante as propriedades estruturais
 * por construção, sem tentativa e erro: os clubes são embaralhados numa roda e
 * cada um enfrenta os vizinhos a distâncias `d₁..dₖ`, recebendo quem está
 * `+d` à frente e visitando quem está `-d` atrás. Cada distância rende um
 * mandante e um visitante para cada clube — `k/2` distâncias dão `k` jogos,
 * `k/2` em casa.
 *
 * As distâncias escolhidas são as que formam ciclos de comprimento PAR
 * (`n / gcd(n, d)` par): um ciclo par se divide em duas rodadas alternando as
 * arestas, e em cada rodada todo clube joga exatamente uma vez. Potes e "sem
 * dois clubes do mesmo país" ficam fora; documentado como corte da issue #124.
 */
fun swissLeagueSchedule(
    clubs: List<ClubId>,
    format: CompetitionFormat.SwissLeague,
    rng: Random,
): List<List<Pairing>> {
    require(clubs.size == format.teams) { "Fase de liga espera ${format.teams} clubes: ${clubs.size}" }
    require(clubs.distinct().size == clubs.size) { "Clube repetido no sorteio: $clubs" }
    val n = clubs.size
    val wheel = clubs.sortedBy { it.value }.shuffled(rng)
    val distances = (1 until n / 2).filter { d -> (n / gcd(n, d)) % 2 == 0 }.take(format.matchesPerTeam / 2)
    require(distances.size == format.matchesPerTeam / 2) {
        "$n clubes não comportam ${format.matchesPerTeam} jogos por clube na roda suíça"
    }

    return distances.flatMap { d ->
        val cycles = gcd(n, d)
        val cycleLength = n / cycles
        // Aresta k de cada ciclo: posição (start + k·d) recebe (start + (k+1)·d).
        // Arestas de índice par numa rodada, ímpar na outra.
        (0..1).map { parity ->
            (0 until cycles).flatMap { start ->
                (parity until cycleLength step 2).map { k ->
                    Pairing(home = wheel[(start + k * d) % n], away = wheel[(start + (k + 1) * d) % n])
                }
            }
        }
    }
}

/** Onde a [position] (1-based) da fase de liga leva o clube. */
fun swissPlacement(format: CompetitionFormat.SwissLeague, position: Int): SwissPlacement {
    require(position in 1..format.teams) { "Posição fora de 1..${format.teams}: $position" }
    return when {
        position <= format.directQualifiers -> SwissPlacement.DIRECT_TO_KNOCKOUT
        position in format.playoffRange -> SwissPlacement.PLAYOFF
        else -> SwissPlacement.ELIMINATED
    }
}

/**
 * MDC de Euclides. `tailrec` pede ao compilador que transforme a recursão em
 * laço — sem risco de estourar a pilha e sem escrever o `while` à mão.
 */
private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
