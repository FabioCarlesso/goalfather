package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.ContinentalClub
import com.carlesso.goalfather.domain.model.ContinentalCup
import com.carlesso.goalfather.domain.model.ContinentalGroup
import com.carlesso.goalfather.domain.model.ContinentalTitle
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.CupPhase
import com.carlesso.goalfather.domain.model.CupStage
import com.carlesso.goalfather.domain.model.CupTie
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.GroupResult
import com.carlesso.goalfather.domain.model.Legs
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Pairing
import com.carlesso.goalfather.domain.model.QualificationRule
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.PlayedMatch
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStage
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Tiebreaker
import com.carlesso.goalfather.domain.model.Venue
import kotlin.random.Random

/**
 * Regras da continental com fase de grupos e mata-mata — a Libertadores do MVP
 * (issue #132). Funções PURAS, como as da copa (`CupRules.kt`): o sorteio
 * recebe o `Random`, os sorteios seguintes saem da `drawSeed` da edição e o
 * avanço é determinístico.
 *
 * **Cortes do MVP** (ver `docs/ARQUITETURA.md`, seção da #132): sem fases
 * preliminares (os 32 entram direto nos grupos); o 3º do grupo é eliminado (a
 * ida à Sul-Americana é da #145); a final única não tem prorrogação — empate
 * no tempo normal vai direto aos pênaltis, porque a engine joga 90 minutos.
 */

/** Nomes dos grupos, na ordem do sorteio: `Grupo A`, `Grupo B`… */
fun groupName(index: Int): String = "Grupo ${'A' + index}"

/**
 * Onde começa a faixa de `matchId` da continental no encaixe: `encaixe × 1000 +
 * 50 + índice`. A copa nacional usa `+ 1..`, as ligas `+ 100..`; os grupos têm
 * 16 jogos por rodada e o mata-mata até 8 confrontos — cabe com folga.
 */
const val CONTINENTAL_FIRST_MATCH_INDEX: Int = 50

/**
 * A continental paga o DOBRO da copa nacional por fase avançada (a escala de
 * metades de [cupPhasePrize]): a final vale US$ 2 mi, a semifinal US$ 800 mil.
 * Proporção, não o valor real da CONMEBOL — a economia do jogo é a da #131.
 */
const val CONTINENTAL_PRIZE_FACTOR: Int = 2

/**
 * Seed do sorteio da continental [competition] na temporada [season]: a mesma
 * construção da copa nacional ([nationalCupDrawSeed]), com outro sal, para a
 * Libertadores e a Copa do Brasil do mesmo ano não sortearem igual.
 */
fun continentalDrawSeed(season: Int, competition: CompetitionId): Long =
    nationalCupDrawSeed(season, competition) xor CONTINENTAL_SEED_SALT

/** "LIB" em hexa. */
private const val CONTINENTAL_SEED_SALT: Long = 0x4C_4942L

// ─── Sorteio ──────────────────────────────────────────────────────────────

/** Resultado do sorteio da edição: valor, não exception — o dado pode não fechar. */
sealed interface ContinentalDraw {
    data class Drawn(val edition: ContinentalCup) : ContinentalDraw

    /** O número de inscritos não fecha com o formato (32 na Libertadores). */
    data class WrongSize(val expected: Int, val actual: Int) : ContinentalDraw

    /** Nenhuma distribuição em grupos separa os compatriotas. */
    data object Impossible : ContinentalDraw
}

/**
 * Sorteia a edição [season] da continental [game] entre [seeded] — do cabeça
 * de chave ao mais fraco: o campeão da edição anterior primeiro, os demais pela
 * força. Os potes saem dessa ordem ([drawGroupsApart]): 8 por pote, cada grupo
 * com um de cada pote e nunca dois clubes do mesmo país.
 */
fun drawContinental(
    season: Int,
    game: GameCompetition,
    seeded: List<Club>,
    finalVenue: Venue?,
    rng: Random,
): ContinentalDraw {
    val format = game.competition.format
    // `require` com contrato: depois dele `format` é `GroupsThenKnockout` (smart cast).
    require(format is CompetitionFormat.GroupsThenKnockout) {
        "Continental ${game.id.value} precisa de formato GroupsThenKnockout: ${format::class.simpleName}"
    }
    if (seeded.size != format.teams) return ContinentalDraw.WrongSize(format.teams, seeded.size)
    val byId = seeded.associateBy { it.id }

    val draw = drawGroupsApart(seeded.map { it.id }, format, { byId.getValue(it).country.code }, rng)
    // `when` sobre o sealed: o `Impossible` é tratado, não esquecido.
    val groups = when (draw) {
        is GroupDraw.Drawn -> draw.groups
        GroupDraw.Impossible -> return ContinentalDraw.Impossible
    }
    return ContinentalDraw.Drawn(
        ContinentalCup(
            season = season,
            competitionId = game.id,
            competitionName = game.competition.name,
            groups = groups.mapIndexed { i, ids ->
                ContinentalGroup(groupName(i), ids.map { byId.getValue(it).toContinentalClub() })
            },
            groupLegs = format.groupLegs,
            qualifiersPerGroup = format.qualifiersPerGroup,
            knockoutPlan = format.knockout.stages.map { CupPhase(it.name, it.teams, it.legs, emptyList()) },
            drawSeed = rng.nextLong(),
            finalVenue = finalVenue,
        ),
    )
}

private fun Club.toContinentalClub() = ContinentalClub(id, name, country.code)

/**
 * A ordem de cabeças de chave: o campeão da edição anterior ([titleholder])
 * primeiro, os demais pela força do elenco ([squadRating]) e, no empate, pelo
 * id — ordem total, sorteio determinístico.
 */
fun seededForContinental(clubs: Collection<Club>, titleholder: ClubId?): List<Club> =
    clubs.sortedWith(
        compareBy<Club> { it.id != titleholder }
            .thenByDescending { it.squadRating() }
            .thenBy { it.id.value },
    )

/**
 * Os inscritos da edição seguinte (issue #132), país a país, na COTA da edição
 * que acabou ([ContinentalCup.quotas] — a do pack, repetida ano a ano):
 *
 * - **o campeão** ([titleholder]) entra sempre, e conta na cota do país dele
 *   (assim a edição continua com 32);
 * - **país com liga no save** ([leagueCountries], o Brasil): os classificados
 *   pelas regras de vaga ([qualified], na ordem da cascata — campeão da copa,
 *   G-4, 5º e 6º); se faltarem (tabela pequena), completa com os mais fortes do
 *   país;
 * - **país sem liga** (Argentina, Uruguai…): os clubes mais fortes dele. A issue
 *   fala em "maior reputação", mas a reputação é dado do pack e não do save; a
 *   força do elenco é o que o jogo sabe deles hoje.
 */
fun continentalEntrants(
    previous: ContinentalCup,
    qualified: List<ClubId>,
    clubs: Collection<Club>,
    leagueCountries: Set<CountryCode>,
    titleholder: ClubId?,
): List<Club> {
    val byCountry = clubs.groupBy { it.country.code }
    val byId = clubs.associateBy { it.id }
    return previous.quotas.toSortedMap(compareBy { it.value }).flatMap { (country, quota) ->
        val own = byCountry[country].orEmpty()
        val strongest = own.sortedWith(compareByDescending<Club> { it.squadRating() }.thenBy { it.id.value })
        val ranked = buildList {
            titleholder?.let(byId::get)?.takeIf { it.country.code == country }?.let(::add)
            if (country in leagueCountries) addAll(qualified.mapNotNull(byId::get).filter { it.country.code == country })
            addAll(strongest)
        }
        // `distinctBy` preserva a ordem do primeiro encontro: o campeão e os
        // classificados vêm antes dos "mais fortes" de reserva.
        ranked.distinctBy { it.id }.take(quota)
    }
}

// ─── Fase de grupos ───────────────────────────────────────────────────────

/** Rodadas da fase de grupos: turno (e returno) do maior grupo. */
val ContinentalCup.groupRounds: Int
    get() = groups.maxOf { turnRounds(it.clubs.size) } * groupLegs

/** Encaixes que a edição inteira ocupa: grupos + uma perna por encaixe no mata-mata. */
val ContinentalCup.calendarRounds: Int
    get() = groupRounds + knockoutPlan.sumOf { it.legs.matches }

/** Os jogos de [group], rodada a rodada — turno e returno com o mando espelhado. */
fun ContinentalCup.fixturesOf(group: ContinentalGroup): List<List<Pairing>> =
    roundRobinSchedule(group.clubs.map { it.id }, groupLegs)

/** Todos os jogos de grupo já disputados? Então o mata-mata pode ser sorteado. */
val ContinentalCup.groupStageComplete: Boolean
    get() = results.size == groups.sumOf { group -> fixturesOf(group).sumOf { it.size } }

/**
 * A tabela de [group] com os desempates da competição ([tiebreakers]). O
 * sorteio (DRAWING_OF_LOTS) usa uma seed da edição + grupo: a mesma tabela
 * sai sempre igual.
 */
fun ContinentalCup.groupTable(group: ContinentalGroup, tiebreakers: List<Tiebreaker>): List<StandingRow> {
    val own = results.filter { it.group == group.name }
    val rows = group.clubs.map { club ->
        own.fold(StandingRow(position = 0, clubId = club.id, clubName = club.name)) { row, r ->
            when (club.id) {
                r.home -> row.afterGame(r.homeGoals, r.awayGoals)
                r.away -> row.afterGame(r.awayGoals, r.homeGoals)
                else -> row
            }
        }
    }
    return rankTable(
        rows = rows,
        tiebreakers = tiebreakers,
        results = own.map { PlayedMatch(it.home, it.away, it.homeGoals, it.awayGoals) },
        lots = Random(drawSeed xor group.name.hashCode().toLong()),
    )
}

/** A linha depois de um jogo: 3 pontos a vitória, 1 o empate (a regra da tabela da liga). */
private fun StandingRow.afterGame(own: Int, against: Int): StandingRow = copy(
    played = played + 1,
    wins = wins + if (own > against) 1 else 0,
    draws = draws + if (own == against) 1 else 0,
    losses = losses + if (own < against) 1 else 0,
    goalsFor = goalsFor + own,
    goalsAgainst = goalsAgainst + against,
    goalDifference = goalDifference + own - against,
    points = points + when {
        own > against -> 3
        own == against -> 1
        else -> 0
    },
)

// ─── Jogos do encaixe ─────────────────────────────────────────────────────

/**
 * Os jogos da rodada [round] (1-based) da edição no encaixe [matchday]: nas
 * [groupRounds] primeiras, a rodada de cada grupo; depois, a perna em aberto do
 * mata-mata (o motor da copa, [cupMatches]). Antes de o mata-mata existir (os
 * grupos ainda não acabaram) não há jogo de mata-mata a mostrar.
 *
 * A final única ([isNeutralFinal]) vai para a sede neutra da edição.
 */
fun continentalMatches(matchday: Matchday, edition: ContinentalCup, round: Int): List<RoundMatch> {
    require(round >= 1) { "Rodada da continental < 1: $round" }
    if (round > edition.groupRounds) {
        val knockout = edition.knockout ?: return emptyList()
        val venue = edition.finalVenue?.takeIf { knockout.isNeutralFinal }
        return cupMatches(matchday, knockout, emptyList(), CONTINENTAL_FIRST_MATCH_INDEX)
            .map { it.copy(neutralVenue = venue?.name) }
    }
    val byId = edition.clubs.associateBy { it.id }
    return edition.groups
        .flatMap { group ->
            edition.fixturesOf(group).getOrNull(round - 1).orEmpty().map { group to it }
        }
        .mapIndexed { i, (group, pairing) ->
            val home = byId.getValue(pairing.home)
            val away = byId.getValue(pairing.away)
            RoundMatch(
                matchId = matchday.serial * Matchday.MATCH_IDS_PER_MATCHDAY + CONTINENTAL_FIRST_MATCH_INDEX + i,
                homeClubId = home.id,
                awayClubId = away.id,
                homeClubName = home.name,
                awayClubName = away.name,
                division = Division.FIRST,
                stage = RoundStage.Group(group.name, round),
                competition = edition.competitionId,
                competitionName = edition.competitionName,
            )
        }
}

/** A fase em disputa é a final de jogo único? É ela que vai para a sede neutra. */
val Cup.isNeutralFinal: Boolean
    get() = currentPhase?.let { it == phases.last() && it.legs == Legs.SINGLE } ?: false

// ─── Avanço ───────────────────────────────────────────────────────────────

/** A edição depois de um encaixe + o prêmio de quem passou de fase nele. */
data class ContinentalRoundOutcome(
    val edition: ContinentalCup,
    val prizes: Map<ClubId, Money>,
)

/**
 * Registra os jogos DESTA edição num encaixe encerrado e avança:
 *
 * 1. jogos de grupo viram [GroupResult] (repetir o encaixe não duplica: o par
 *    mandante × visitante só existe uma vez no turno e returno);
 * 2. quando o último jogo de grupo entra, o mata-mata é montado
 *    ([startKnockout]) com os [ContinentalCup.qualifiersPerGroup] primeiros de
 *    cada grupo — o 3º e o 4º estão fora;
 * 3. jogos de mata-mata vão para a `Cup` (`Cup.recordRound`), que decide os
 *    confrontos, sorteia a fase seguinte e paga o prêmio — aqui multiplicado
 *    por [CONTINENTAL_PRIZE_FACTOR].
 */
fun ContinentalCup.recordRound(round: Round, tiebreakers: List<Tiebreaker>): ContinentalRoundOutcome {
    val own = round.matches.filter { it.competition == competitionId && it.status == RoundStatus.Finished }
    val known = results.mapTo(HashSet()) { it.home to it.away }
    val newResults = own.mapNotNull { match ->
        val stage = match.stage as? RoundStage.Group ?: return@mapNotNull null
        if (match.homeClubId to match.awayClubId in known) return@mapNotNull null
        GroupResult(stage.groupName, stage.matchday, match.homeClubId, match.awayClubId, match.homeGoals, match.awayGoals)
    }
    val withGroups = copy(results = results + newResults)
        .let { if (it.knockout == null && it.groupStageComplete) it.startKnockout(tiebreakers) else it }

    val knockout = withGroups.knockout ?: return ContinentalRoundOutcome(withGroups, emptyMap())
    val knockoutMatches = own.filter { it.isKnockout }
    if (knockoutMatches.isEmpty()) return ContinentalRoundOutcome(withGroups, emptyMap())
    val outcome = knockout.recordRound(round.copy(matches = knockoutMatches))
    return ContinentalRoundOutcome(
        edition = withGroups.copy(knockout = outcome.cup),
        prizes = outcome.prizes.mapValues { (_, prize) -> prize * CONTINENTAL_PRIZE_FACTOR },
    )
}

/**
 * Monta o mata-mata com os classificados dos grupos. Com 2 por grupo (o
 * formato da Libertadores), as oitavas cruzam 1º contra 2º de OUTRO grupo, e o
 * 1º decide em casa (o 2º recebe a ida). Com outro número de classificados, o
 * sorteio é livre. Seed: a da edição — o sorteio não depende de quando os
 * grupos acabaram.
 */
fun ContinentalCup.startKnockout(tiebreakers: List<Tiebreaker>): ContinentalCup {
    require(knockout == null) { "Mata-mata de ${competitionId.value} já sorteado" }
    val names = clubs.associate { it.id to it.toCupClub() }
    // Posição de cada classificado e o grupo dele.
    val placed = groups.flatMap { group ->
        groupTable(group, tiebreakers).take(qualifiersPerGroup).map { row -> Qualified(row.clubId, group.name, row.position) }
    }
    val rng = Random(drawSeed)
    // Parênteses no `if`: sem eles o `?:` se prenderia só ao ramo `else`.
    val pairings = (
        if (qualifiersPerGroup == 2) {
            crossGroupPairings(placed.filter { it.position == 1 }, placed.filter { it.position == 2 }, rng)
        } else {
            null
        }
        ) ?: drawKnockoutPairings(placed.map { it.club }, rng)

    val opening = knockoutPlan.first()
    val ties = pairings.mapIndexed { index, pairing ->
        val home = names.getValue(pairing.home)
        val away = names.getValue(pairing.away)
        when (opening.legs) {
            Legs.SINGLE -> CupTie.Single(index, home, away)
            Legs.TWO_LEGGED -> CupTie.TwoLegged(index, home, away)
        }
    }
    val phases = listOf(opening.copy(entrants = placed.map { names.getValue(it.club) })) + knockoutPlan.drop(1)
    return copy(
        knockout = Cup(
            season = season,
            competitionId = competitionId,
            competitionName = competitionName,
            phases = phases,
            stages = listOf(CupStage(opening.name, ties)),
            drawSeed = rng.nextLong(),
        ),
    )
}

private data class Qualified(val club: ClubId, val group: String, val position: Int)

/**
 * 1º × 2º de grupos diferentes, sorteado: o 2º recebe a ida (`home`), o 1º a
 * volta. Busca com volta atrás, como o sorteio dos grupos — o último 1º pode
 * sobrar só com o 2º do próprio grupo. `null` se não houver pareamento (não
 * acontece com 2+ grupos).
 */
private fun crossGroupPairings(winners: List<Qualified>, runnersUp: List<Qualified>, rng: Random): List<Pairing>? {
    val order = winners.shuffled(rng)
    val pool = runnersUp.shuffled(rng).toMutableList()
    val chosen = mutableListOf<Pairing>()
    fun pair(index: Int): Boolean {
        if (index == order.size) return true
        val winner = order[index]
        for (runner in pool.toList()) {
            if (runner.group == winner.group) continue
            pool -= runner
            chosen += Pairing(home = runner.club, away = winner.club)
            if (pair(index + 1)) return true
            chosen.removeAt(chosen.lastIndex)
            pool += runner
        }
        return false
    }
    return if (winners.size == runnersUp.size && pair(0)) chosen.toList() else null
}

// ─── Virada ───────────────────────────────────────────────────────────────

/**
 * Os clubes classificados para [competition] pelas tabelas finais de um país,
 * na ordem da cascata (campeão da copa, G-4, 5º e 6º — issue #128). Só as
 * regras de VAGA para esta competição entram; a de campeão sem campeão
 * conhecido (copa que não chegou à final) fica de fora em vez de estourar, e
 * a vaga dela desce para a tabela, como na cascata. Sem regras de movimentação,
 * o resultado nunca tem playoff pendente.
 */
fun continentalQualifiers(
    competition: CompetitionId,
    rules: List<QualificationRule>,
    finalStandings: List<Standings>,
    cupChampions: Map<CompetitionId, ClubId>,
): List<ClubId> {
    val tables = finalStandings.associateBy { it.competitionId }
    val own = rules.filter { rule ->
        when (rule) {
            is QualificationRule.TopN -> rule.to == competition && rule.from in tables
            is QualificationRule.Champion -> rule.to == competition && (rule.from in cupChampions || rule.from in tables)
            is QualificationRule.Relegation, is QualificationRule.Promotion, is QualificationRule.Playoff -> false
        }
    }
    return when (val transition = applyQualificationRules(own, tables, cupChampions)) {
        is SeasonTransition.Resolved -> transition.qualifications.map { it.club }
        is SeasonTransition.PlayoffsPending -> emptyList()
    }
}

/** O título de [edition] para a história, ou `null` se a final não foi disputada. */
fun ContinentalCup.title(): ContinentalTitle? =
    champion?.let { ContinentalTitle(competitionId, competitionName, it) }
