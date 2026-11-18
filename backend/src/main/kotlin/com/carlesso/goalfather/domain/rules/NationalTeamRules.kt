package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.engine.MatchSetup
import com.carlesso.goalfather.domain.engine.QuickMatchSimulator
import com.carlesso.goalfather.domain.event.MatchEvent
import com.carlesso.goalfather.domain.model.Availability
import com.carlesso.goalfather.domain.model.CalledUpPlayer
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.Employment
import com.carlesso.goalfather.domain.model.FifaWindow
import com.carlesso.goalfather.domain.model.Formation
import com.carlesso.goalfather.domain.model.Lineup
import com.carlesso.goalfather.domain.model.NationalMatch
import com.carlesso.goalfather.domain.model.NationalMatchKind
import com.carlesso.goalfather.domain.model.NationalSquad
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import kotlin.random.Random

/**
 * Seleções nas datas FIFA (issue #134). Funções PURAS, sem Spring: a
 * aleatoriedade entra por parâmetro e cada sorteio tem seed derivada da janela
 * (e do país, ou do jogo), para que todo cliente — multiplayer, replay — veja
 * a mesma lista e os mesmos placares.
 *
 * O fluxo de uma janela, orquestrado pelo serviço de aplicação:
 *
 * 1. **Entrada** — [worldCallUps]: cada seleção chama os melhores da sua
 *    nacionalidade; [calledUpFor] marca o jogador como convocado e o clube o
 *    perde em todos os jogos da janela.
 * 2. **Saída** — [windowFixtures] + [playWindow]: Eliminatórias da CONMEBOL e
 *    amistosos, pela simulação rápida (#129); [returnFromDuty] devolve cada
 *    convocado ao clube com a stamina gasta e, às vezes, lesionado.
 * 3. **Leitura** — [nationalTable] (classificação das Eliminatórias) e
 *    [nationalRanking] (pontos por resultado), sempre recalculados dos jogos:
 *    nada de tabela persistida que possa divergir dos placares.
 */

// ─── Convocação ───────────────────────────────────────────────────────────

/** Tamanho da lista: entre 23 e 26, sorteado por janela e país. */
val NATIONAL_SQUAD_SIZE: IntRange = 23..26

/**
 * Cota mínima por posição (3 goleiros, 7 defensores, 7 meias, 4 atacantes =
 * 21): as vagas restantes vão para os melhores que sobrarem, de qualquer
 * posição. `mapOf` preserva a ordem de inserção — é a ordem da lista.
 */
val NATIONAL_SQUAD_QUOTAS: Map<Position, Int> = mapOf(
    Position.GK to 3,
    Position.CB to 7,
    Position.MF to 7,
    Position.FW to 4,
)

/**
 * Corte de quem tem seleção: o país precisa de ao menos 11 jogadores aptos no
 * mundo do save. Abaixo disso, ninguém é convocado (o jogador fica no clube) —
 * exceto nas Eliminatórias, cujas 10 seleções jogam sempre (ver
 * [ESTIMATED_PLAYER_OVERALL]).
 */
const val MIN_PLAYERS_FOR_NATIONAL_TEAM: Int = 11

/**
 * Overall dos "reforços estimados": jogadores GERADOS na hora, nunca
 * persistidos, que completam uma seleção com menos de 23 convocados (a Bolívia
 * do pack não tem nenhum jogador no mundo). É a força estimada de quem joga
 * fora das ligas do save — um time mediano, abaixo dos titulares das ligas.
 */
const val ESTIMATED_PLAYER_OVERALL: Int = 62

/**
 * Seed da convocação de [country] na [window]. Empacota a data (dias desde
 * 1970) e o código do país em faixas disjuntas do `Long` — mesmo cuidado de
 * `fitnessSeed` para duas entradas não colidirem.
 */
fun callUpSeed(window: FifaWindow, country: CountryCode): Long =
    (window.start.toEpochDay() shl 32) xor country.value.hashCode().toLong()

/**
 * A lista de uma seleção entre os [candidates] (jogadores da nacionalidade
 * dela). Só quem está apto é chamado — lesionado fica no clube.
 *
 * Ordem por overall; o [rng] desempata overalls iguais (embaralha antes do
 * `sortedByDescending`, que é ESTÁVEL: entre iguais, vale a ordem embaralhada)
 * e sorteia o tamanho da lista em [NATIONAL_SQUAD_SIZE]. Primeiro as cotas por
 * posição ([NATIONAL_SQUAD_QUOTAS]), depois os melhores que sobraram. Sem
 * jogadores suficientes, a lista é o que houver.
 */
fun List<Player>.callUp(rng: Random): List<Player> {
    val size = NATIONAL_SQUAD_SIZE.random(rng)
    val ranked = filter { it.availability == Availability.Available }
        .sortedBy { it.id.value } // a entrada não promete ordem; o sorteio parte de uma fixa
        .shuffled(rng)
        .sortedByDescending { it.overall }
    val quotas = NATIONAL_SQUAD_QUOTAS.flatMap { (position, n) -> ranked.filter { it.position == position }.take(n) }
    val rest = ranked.filterNot { it in quotas }.take((size - quotas.size).coerceAtLeast(0))
    return (quotas + rest).sortedWith(compareBy<Player> { it.position.ordinal }.thenByDescending { it.overall })
}

/**
 * As convocações do mundo para a [window], com o elenco de cada clube como está
 * AGORA. Tem seleção quem passa do corte [MIN_PLAYERS_FOR_NATIONAL_TEAM] ou
 * disputa as Eliminatórias ([CONMEBOL_QUALIFIERS]). Ordenadas pelo código do
 * país, para a saída não depender da ordem dos clubes.
 *
 * Sem estado: chamada antes da janela, é a PRÉVIA que o técnico vê ("3
 * jogadores convocados"); na entrada da janela, é a convocação de fato — com o
 * mesmo resultado, se nada mudou no elenco nesse meio-tempo.
 */
fun worldCallUps(window: FifaWindow, clubs: List<Club>): List<NationalSquad> {
    val clubOf = HashMap<PlayerId, Club>()
    val byCountry = HashMap<CountryCode, MutableList<Player>>()
    for (club in clubs) {
        for (player in club.squad) {
            clubOf[player.id] = club
            byCountry.getOrPut(player.nationality.code) { mutableListOf() } += player
        }
    }
    val fit = byCountry.mapValues { (_, players) -> players.count { it.availability == Availability.Available } }
    val teams = (fit.filterValues { it >= MIN_PLAYERS_FOR_NATIONAL_TEAM }.keys + CONMEBOL_QUALIFIERS)
        .sortedBy { it.value }

    return teams.map { country ->
        val called = byCountry[country].orEmpty().callUp(Random(callUpSeed(window, country)))
        NationalSquad(
            country = country,
            players = called.map { player ->
                val club = clubOf.getValue(player.id)
                CalledUpPlayer(player.id, player.name, player.position, player.overall, club.id, club.name)
            },
        )
    }
}

/** O jogador sai para a seleção de [country] até o fim da [window]. */
fun Player.calledUpFor(country: CountryCode, window: FifaWindow): Player =
    copy(availability = Availability.OnInternationalDuty(country, window.end))

/**
 * O que a janela fez com UM convocado: quantos jogos ele começou e a lesão mais
 * longa que sofreu pela seleção, se sofreu.
 */
data class DutyReport(val starts: Int = 0, val injuryRounds: Int? = null)

/**
 * O convocado volta ao clube (issue #134), reusando os números de
 * `FitnessRules`: cada jogo que ele começou pela seleção custa o desgaste de um
 * titular ([STARTER_STAMINA_LOSS], piso [STAMINA_MATCH_FLOOR]); a lesão
 * sofrida lá vira [Availability.Injured] com a duração que a engine sorteou —
 * é a "chance de lesão" da volta, que sai do próprio jogo e não de um dado à
 * parte. Quem não está convocado passa intacto.
 */
fun Player.returnFromDuty(report: DutyReport?, rng: Random): Player {
    if (!onInternationalDuty) return this
    val starts = report?.starts ?: 0
    val stamina = if (starts == 0) {
        stamina
    } else {
        (stamina - (1..starts).sumOf { STARTER_STAMINA_LOSS.random(rng) }).coerceAtLeast(STAMINA_MATCH_FLOOR)
    }
    val availability = report?.injuryRounds?.let { Availability.Injured(it) } ?: Availability.Available
    // `minOf`: o piso de desgaste nunca DEVOLVE stamina a quem já foi abaixo dele.
    return copy(stamina = minOf(this.stamina, stamina), availability = availability)
}

/** Seed do desgaste da volta de um jogador: janela + jogador, faixas disjuntas. */
fun dutyReturnSeed(window: FifaWindow, player: PlayerId): Long =
    (window.end.toEpochDay() shl 40) xor player.value

// ─── Eliminatórias da CONMEBOL ────────────────────────────────────────────

/** As 10 seleções da CONMEBOL, que jogam as Eliminatórias em turno e returno. */
val CONMEBOL_QUALIFIERS: List<CountryCode> =
    listOf("ARG", "BOL", "BRA", "CHI", "COL", "ECU", "PAR", "PER", "URU", "VEN").map(::CountryCode)

/** 10 seleções em turno e returno: 9 rodadas por turno, 18 no total. */
const val QUALIFIER_ROUNDS: Int = 18

/**
 * Primeiro ano de um ciclo de Eliminatórias no jogo. Os ciclos têm DOIS anos
 * civis (20 jogos de datas FIFA): as 18 rodadas ocupam os 18 primeiros, e os
 * 2 últimos (novembro do segundo ano) são amistosos. Corte do jogo: o ciclo
 * real dura ~2 anos e meio e termina antes da Copa; alinhar com a Copa do
 * Mundo é da issue #144.
 */
const val FIRST_QUALIFIER_CYCLE: Int = 2026

/** O ciclo (ano de início) a que [year] pertence. `floorMod` acerta também anos antes de 2026. */
fun qualifierCycleOf(year: Int): Int = year - Math.floorMod(year - FIRST_QUALIFIER_CYCLE, 2)

/**
 * A rodada das Eliminatórias de cada jogo da [window] (`null` = amistoso),
 * contando os jogos das janelas do ciclo em ordem: rodada `k` é o k-ésimo jogo
 * de data FIFA do ciclo. [windowsOf] dá as janelas de um ano — injetado para os
 * testes e para, no futuro, ler do pack. Janela fora das listas não tem
 * Eliminatórias.
 */
fun qualifierRoundsOf(window: FifaWindow, windowsOf: (Int) -> List<FifaWindow>): List<Int?> {
    val cycle = qualifierCycleOf(window.start.year)
    val windows = (windowsOf(cycle) + windowsOf(cycle + 1)).sortedBy { it.start }
    val index = windows.indexOf(window)
    if (index < 0) return List(window.matchSlots) { null }
    val before = windows.take(index).sumOf { it.matchSlots }
    return (1..window.matchSlots).map { slot -> (before + slot).takeIf { it <= QUALIFIER_ROUNDS } }
}

/**
 * As 18 rodadas de um ciclo: Berger em turno e returno (o mesmo gerador das
 * ligas, `roundRobinSchedule`), com a ordem das seleções sorteada pelo ano do
 * ciclo — cada ciclo tem a sua tabela. O Berger trabalha com `ClubId`; aqui o
 * "id" é só a posição sorteada, traduzida de volta para o país.
 */
fun qualifierSchedule(cycle: Int): List<List<Pair<CountryCode, CountryCode>>> {
    val order = CONMEBOL_QUALIFIERS.shuffled(Random(cycle.toLong()))
    val ids = order.indices.map { ClubId(it + 1L) }
    return roundRobinSchedule(ids, legs = 2).map { round ->
        round.map { order[(it.home.value - 1).toInt()] to order[(it.away.value - 1).toInt()] }
    }
}

// ─── Jogos da janela ──────────────────────────────────────────────────────

/** Um jogo marcado para a janela, ainda sem placar. */
data class NationalFixture(
    val home: CountryCode,
    val away: CountryCode,
    val kind: NationalMatchKind,
    val matchNumber: Int,
    val qualifierRound: Int? = null,
)

/**
 * Os jogos da [window] entre as [teams] (as seleções convocadas). Em cada um
 * dos [FifaWindow.matchSlots] jogos: a rodada das Eliminatórias, se houver
 * (só entre as 10 da CONMEBOL), e amistosos entre as demais, pareadas por
 * sorteio com seed da janela + jogo. Número ímpar: uma seleção folga.
 */
fun windowFixtures(
    window: FifaWindow,
    teams: List<CountryCode>,
    windowsOf: (Int) -> List<FifaWindow> = ::interimFifaWindows,
): List<NationalFixture> {
    val rounds = qualifierRoundsOf(window, windowsOf)
    val schedule by lazy { qualifierSchedule(qualifierCycleOf(window.start.year)) }
    val sorted = teams.sortedBy { it.value }
    return rounds.flatMapIndexed { index, round ->
        val matchNumber = index + 1
        val qualifiers = round?.let { r ->
            schedule[r - 1]
                .filter { (home, away) -> home in teams && away in teams }
                .map { (home, away) -> NationalFixture(home, away, NationalMatchKind.QUALIFIER, matchNumber, r) }
        }.orEmpty()
        val busy = qualifiers.flatMapTo(HashSet()) { listOf(it.home, it.away) }
        val friendlies = sorted.filterNot { it in busy }
            .shuffled(Random(friendlySeed(window, matchNumber)))
            .chunked(2)
            .filter { it.size == 2 }
            .map { (home, away) -> NationalFixture(home, away, NationalMatchKind.FRIENDLY, matchNumber) }
        qualifiers + friendlies
    }
}

private fun friendlySeed(window: FifaWindow, matchNumber: Int): Long =
    (window.start.toEpochDay() shl 8) + matchNumber

/** Seed de um jogo da janela: a data e a posição do jogo na lista. */
private fun nationalMatchSeed(window: FifaWindow, index: Int): Long =
    (window.start.toEpochDay() shl 20) + index

/** Placares da janela + o que ela fez com cada convocado real. */
data class WindowOutcome(
    val matches: List<NationalMatch>,
    val duty: Map<PlayerId, DutyReport>,
)

/**
 * Joga os [fixtures] da [window] pela simulação rápida (#129). Cada seleção
 * entra com o melhor 4-4-2 da lista ([inStartingOrder]), completada por
 * reforços estimados ([ESTIMATED_PLAYER_OVERALL]) quando faltar gente — os
 * reforços têm id negativo e nenhum efeito sai deles. Seed por jogo: o
 * resultado não depende de quem rodou antes.
 *
 * Todo jogo usa a mesma lista (sem desgaste entre os jogos da janela): a
 * fadiga é cobrada uma vez, na volta ([returnFromDuty]).
 */
fun playWindow(
    window: FifaWindow,
    squads: Map<CountryCode, List<Player>>,
    fixtures: List<NationalFixture>,
    simulator: QuickMatchSimulator = QuickMatchSimulator(),
): WindowOutcome {
    val starts = HashMap<PlayerId, Int>()
    val injuries = HashMap<PlayerId, Int>()
    val matches = fixtures.mapIndexed { index, fixture ->
        val home = squads[fixture.home].orEmpty().nationalLineup(fixture.home, idOffset = 0)
        val away = squads[fixture.away].orEmpty().nationalLineup(fixture.away, idOffset = FILLERS_PER_SIDE)
        val result = simulator.simulate(
            MatchSetup(home = home, away = away, homeName = fixture.home.value, awayName = fixture.away.value),
            Random(nationalMatchSeed(window, index)),
        )
        (home.players + away.players).filter { it.id.value > 0 }.forEach { starts.merge(it.id, 1, Int::plus) }
        result.highlights.filterIsInstance<MatchEvent.Injury>()
            .filter { it.playerId.value > 0 }
            .forEach { injuries.merge(it.playerId, it.roundsOut, ::maxOf) }
        NationalMatch(
            home = fixture.home,
            away = fixture.away,
            kind = fixture.kind,
            matchNumber = fixture.matchNumber,
            homeGoals = result.homeGoals,
            awayGoals = result.awayGoals,
            qualifierRound = fixture.qualifierRound,
        )
    }
    val duty = (starts.keys + injuries.keys).associateWith { DutyReport(starts[it] ?: 0, injuries[it]) }
    return WindowOutcome(matches, duty)
}

/** Faixa de ids negativos de cada lado, para os reforços dos dois times não colidirem. */
private const val FILLERS_PER_SIDE: Int = 100

/** O melhor 4-4-2 da lista, com reforços estimados onde faltar jogador. */
private fun List<Player>.nationalLineup(country: CountryCode, idOffset: Int): Lineup {
    val squad = this + estimatedFillers(country, idOffset)
    return Lineup(players = squad.inStartingOrder(Formation.F_4_4_2).take(11), formation = Formation.F_4_4_2)
}

/**
 * Reforços estimados: o que falta para cada cota de posição e, depois, para o
 * mínimo de 23. Gerados na hora, nunca persistidos (id negativo). A
 * nacionalidade fica no default do `Player` — nenhuma regra a lê aqui.
 */
private fun List<Player>.estimatedFillers(country: CountryCode, idOffset: Int): List<Player> {
    val byQuota = NATIONAL_SQUAD_QUOTAS.flatMap { (position, n) ->
        List((n - count { it.position == position }).coerceAtLeast(0)) { position }
    }
    val total = (NATIONAL_SQUAD_SIZE.first - size).coerceAtLeast(byQuota.size)
    val positions = byQuota + List(total - byQuota.size) { Position.MF }
    return positions.mapIndexed { i, position ->
        Player(
            id = PlayerId(-(idOffset + i + 1L)),
            name = "Reforço ${country.value} ${i + 1}",
            position = position,
            overall = ESTIMATED_PLAYER_OVERALL,
            pace = ESTIMATED_PLAYER_OVERALL,
            shooting = ESTIMATED_PLAYER_OVERALL,
            passing = ESTIMATED_PLAYER_OVERALL,
            defending = ESTIMATED_PLAYER_OVERALL,
            // Estimado, nunca persistido: não tem clube nem contrato.
            employment = Employment.FreeAgent,
            age = 26,
        )
    }
}

// ─── Classificação e ranking ──────────────────────────────────────────────

/** Uma linha de tabela de seleções, calculada dos placares. */
data class NationalTableRow(
    val country: CountryCode,
    val played: Int = 0,
    val won: Int = 0,
    val drawn: Int = 0,
    val lost: Int = 0,
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
    val points: Int = 0,
) {
    val goalDifference: Int get() = goalsFor - goalsAgainst

    /** A linha depois de um jogo com [scored] x [conceded], valendo [weight] vezes os pontos. */
    fun after(scored: Int, conceded: Int, weight: Int = 1): NationalTableRow {
        val win = scored > conceded
        val draw = scored == conceded
        return copy(
            played = played + 1,
            won = won + if (win) 1 else 0,
            drawn = drawn + if (draw) 1 else 0,
            lost = lost + if (!win && !draw) 1 else 0,
            goalsFor = goalsFor + scored,
            goalsAgainst = goalsAgainst + conceded,
            points = points + weight * if (win) 3 else if (draw) 1 else 0,
        )
    }
}

/** Pontos, saldo, gols pró e, por fim, o código do país — ordem total e estável. */
private val TABLE_ORDER: Comparator<NationalTableRow> =
    compareByDescending<NationalTableRow> { it.points }
        .thenByDescending { it.goalDifference }
        .thenByDescending { it.goalsFor }
        .thenBy { it.country.value }

/**
 * Tabela dos [matches] entre os [teams] (todos aparecem, mesmo sem jogo).
 * [weight] dá o peso de cada jogo nos pontos: 1 na classificação das
 * Eliminatórias.
 */
fun nationalTable(
    teams: Collection<CountryCode>,
    matches: List<NationalMatch>,
    weight: (NationalMatch) -> Int = { 1 },
): List<NationalTableRow> {
    val rows = teams.associateWithTo(LinkedHashMap()) { NationalTableRow(it) }
    for (match in matches) {
        val w = weight(match)
        rows[match.home] = (rows[match.home] ?: NationalTableRow(match.home)).after(match.homeGoals, match.awayGoals, w)
        rows[match.away] = (rows[match.away] ?: NationalTableRow(match.away)).after(match.awayGoals, match.homeGoals, w)
    }
    return rows.values.sortedWith(TABLE_ORDER)
}

/** Peso das Eliminatórias no ranking: jogo oficial vale o dobro do amistoso. */
const val QUALIFIER_RANKING_WEIGHT: Int = 2

/**
 * Ranking simples de seleções (issue #134): pontos por resultado (3/1/0), com
 * jogo de Eliminatórias valendo [QUALIFIER_RANKING_WEIGHT] vezes. Acumula todos
 * os jogos já disputados; é a semente para sorteios futuros (issue #144).
 */
fun nationalRanking(matches: List<NationalMatch>): List<NationalTableRow> =
    nationalTable(emptyList(), matches) { if (it.kind == NationalMatchKind.QUALIFIER) QUALIFIER_RANKING_WEIGHT else 1 }
