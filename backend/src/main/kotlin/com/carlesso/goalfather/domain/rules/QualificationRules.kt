package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Pairing
import com.carlesso.goalfather.domain.model.QualificationRule
import com.carlesso.goalfather.domain.model.SeasonCompetitions
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.TableZone
import com.carlesso.goalfather.domain.model.TieResult
import com.carlesso.goalfather.domain.model.TwoLeggedTieResult

/**
 * Aplicação das [QualificationRule] na virada (issue #128). Funções PURAS em
 * `domain/rules`, como o resto: tabelas finais entram, a recomposição da
 * temporada seguinte sai — testável com tabelas fabricadas, sem Spring.
 *
 * Substitui o antigo `PromotionRelegationRules.kt` (issue #47): a troca entre
 * divisões adjacentes virou um caso particular — um par [QualificationRule.Relegation]
 * + [QualificationRule.Promotion] por degrau da pirâmide ([pyramidRules]).
 */

/** Nº padrão de vagas trocadas entre divisões adjacentes da liga do jogo. */
const val PROMOTION_RELEGATION_SPOTS = 2

/** Uma vaga ganha: quem, para onde, em que fase entra e por qual regra. */
data class Qualification(
    val club: ClubId,
    val competition: CompetitionId,
    val entryStage: String,
    val via: QualificationRule,
)

/**
 * Um playoff a disputar: os clubes que as posições da [rule] apontaram nas
 * tabelas finais. O de baixo recebe a ida; o de cima decide em casa — no jogo
 * único, o de baixo tem o mando (a vantagem do de cima é a de ir para o
 * playoff já classificado numa divisão melhor).
 */
data class PlayoffTie(val rule: QualificationRule.Playoff, val upperClub: ClubId, val lowerClub: ClubId) {

    /** Os jogos do playoff, rodada a rodada — o mesmo `knockoutLegs` do mata-mata. */
    fun matches(): List<List<Pairing>> = knockoutLegs(listOf(Pairing(home = lowerClub, away = upperClub)), rule.format)

    /** Vencedor de um playoff de ida e volta (ida na casa de [lowerClub]). */
    fun winnerOf(result: TwoLeggedTieResult): ClubId {
        require(result.first == lowerClub && result.second == upperClub) {
            "Resultado ${result.first.value} × ${result.second.value} não é o playoff ${lowerClub.value} × ${upperClub.value}"
        }
        return result.winner
    }

    /** Vencedor de um playoff de jogo único (mando de [lowerClub]). */
    fun winnerOf(result: TieResult): ClubId = if (result.homeWon) lowerClub else upperClub
}

/**
 * Resultado da virada. `sealed` porque há dois estados com dados diferentes,
 * e o chamador PRECISA tratar os dois: com playoff pendente não existe
 * recomposição ainda — o tipo não deixa ninguém ler `memberships` cedo demais.
 */
sealed interface SeasonTransition {

    /** Há playoffs sem vencedor: jogue os [ties] e chame de novo com os vencedores. */
    data class PlayoffsPending(val ties: List<PlayoffTie>) : SeasonTransition

    /**
     * Virada resolvida: [memberships] dá a liga da temporada seguinte de CADA
     * clube das tabelas (inclusive quem não se move — o mapa é total), e
     * [qualifications], as vagas em outras competições, na ordem de concessão.
     */
    data class Resolved(
        val memberships: Map<ClubId, CompetitionId>,
        val qualifications: List<Qualification>,
    ) : SeasonTransition
}

/**
 * Aplica [rules] às tabelas finais.
 *
 * - **Movimentação** ([QualificationRule.Relegation], [QualificationRule.Promotion],
 *   [QualificationRule.Playoff]): os clubes trocam de liga; um clube movido por
 *   duas regras é erro de configuração.
 * - **Vagas** ([QualificationRule.Champion] antes, [QualificationRule.TopN] depois,
 *   cada grupo na ordem da lista): um clube recebe no máximo UMA vaga — no MVP
 *   as competições de destino são exclusivas (quem vai à Libertadores não vai à
 *   Sul-Americana). Daí a **cascata**: `TopN` pula quem já tem vaga, então o
 *   campeão da copa no G-6 abre espaço para o 7º. Campeão já classificado por
 *   OUTRO título deixa a vaga vaga; ela desce para a última `TopN` do mesmo
 *   destino (um clube a mais da tabela). Sem `TopN` para aquele destino, a vaga
 *   se perde — limitação documentada.
 *
 * @param tables tabelas finais por competição de pontos corridos, com `position` resolvida.
 * @param champions campeão das competições sem tabela (copas). Para uma liga, o líder é o campeão.
 * @param playoffWinners vencedor de cada playoff já disputado.
 */
fun applyQualificationRules(
    rules: List<QualificationRule>,
    tables: Map<CompetitionId, Standings>,
    champions: Map<CompetitionId, ClubId> = emptyMap(),
    playoffWinners: Map<QualificationRule.Playoff, ClubId> = emptyMap(),
): SeasonTransition {
    // Valida as faixas de todas as tabelas: posição fora da tabela ou em duas faixas é erro de dado.
    tables.forEach { (id, table) -> tableZones(id, table.rows.size, rules) }
    val ranked = tables.mapValues { (_, table) -> table.rows.sortedBy { it.position }.map(StandingRow::clubId) }
    fun table(id: CompetitionId): List<ClubId> = requireNotNull(ranked[id]) { "Regra cita ${id.value}, sem tabela final" }

    val ties = rules.filterIsInstance<QualificationRule.Playoff>().map { rule ->
        PlayoffTie(rule, upperClub = table(rule.upper)[rule.upperPosition - 1], lowerClub = table(rule.lower)[rule.lowerPosition - 1])
    }
    val pending = ties.filter { it.rule !in playoffWinners }
    if (pending.isNotEmpty()) return SeasonTransition.PlayoffsPending(pending)

    return SeasonTransition.Resolved(
        memberships = memberships(rules, ranked, ties, playoffWinners, ::table),
        qualifications = qualifications(rules, champions, ranked, ::table),
    )
}

private fun memberships(
    rules: List<QualificationRule>,
    ranked: Map<CompetitionId, List<ClubId>>,
    ties: List<PlayoffTie>,
    playoffWinners: Map<QualificationRule.Playoff, ClubId>,
    table: (CompetitionId) -> List<ClubId>,
): Map<ClubId, CompetitionId> {
    val next = ranked.flatMap { (id, clubs) -> clubs.map { it to id } }.toMap(mutableMapOf())
    val moved = mutableSetOf<ClubId>()
    fun move(club: ClubId, to: CompetitionId) {
        require(moved.add(club)) { "Clube ${club.value} movido por duas regras na mesma virada" }
        next[club] = to
    }

    // `when` exaustivo sem `else`: uma regra nova obriga a decidir aqui se ela move clube.
    for (rule in rules) {
        when (rule) {
            is QualificationRule.Relegation -> table(rule.from).takeLast(rule.bottomN).forEach { move(it, rule.to) }
            is QualificationRule.Promotion -> table(rule.from).take(rule.topN).forEach { move(it, rule.to) }
            is QualificationRule.Playoff -> {
                val tie = ties.first { it.rule == rule }
                val winner = playoffWinners.getValue(rule)
                require(winner == tie.upperClub || winner == tie.lowerClub) {
                    "Vencedor ${winner.value} não disputou o playoff ${tie.lowerClub.value} × ${tie.upperClub.value}"
                }
                // Só quem troca de liga se move: o de cima que vence fica onde está.
                if (winner == tie.lowerClub) {
                    move(tie.lowerClub, rule.upper)
                    move(tie.upperClub, rule.lower)
                }
            }
            is QualificationRule.TopN, is QualificationRule.Champion -> Unit
        }
    }
    return next
}

private fun qualifications(
    rules: List<QualificationRule>,
    champions: Map<CompetitionId, ClubId>,
    ranked: Map<CompetitionId, List<ClubId>>,
    table: (CompetitionId) -> List<ClubId>,
): List<Qualification> {
    val granted = mutableListOf<Qualification>()
    fun qualified(club: ClubId) = granted.any { it.club == club }

    // Campeões primeiro: o título tem prioridade sobre a tabela.
    val vacated = mutableMapOf<CompetitionId, Int>()
    for (rule in rules.filterIsInstance<QualificationRule.Champion>()) {
        // Copa: campeão informado; liga: o líder da tabela final.
        val champion = requireNotNull(champions[rule.from] ?: ranked[rule.from]?.firstOrNull()) {
            "Sem campeão de ${rule.from.value} para a vaga em ${rule.to.value}"
        }
        if (qualified(champion)) {
            // `merge` soma 1 à contagem (ou cria com 1): o idioma do Map para contador.
            vacated.merge(rule.to, 1, Int::plus)
        } else {
            granted += Qualification(champion, rule.to, rule.entryStage, rule)
        }
    }

    val topNs = rules.filterIsInstance<QualificationRule.TopN>()
    val lastTopNByTarget = topNs.associateBy { it.to } // `associateBy` guarda o ÚLTIMO de cada chave
    for (rule in topNs) {
        val spots = rule.n + if (lastTopNByTarget[rule.to] === rule) vacated[rule.to] ?: 0 else 0
        table(rule.from)
            .filterNot(::qualified)
            .take(spots)
            .forEach { granted += Qualification(it, rule.to, rule.entryStage, rule) }
    }
    return granted
}

/**
 * Faixa de cada posição (índice = posição − 1) da tabela de [competition] com
 * [tableSize] clubes, segundo [rules]. `TopN` da mesma tabela se empilham na
 * ordem da lista; `Promotion` pinta o topo, `Relegation` o fundo e `Playoff` a
 * posição de cada lado. Posição fora da tabela ou em duas faixas é erro de
 * configuração (`require`): uma regra assim não tem leitura possível.
 */
fun tableZones(competition: CompetitionId, tableSize: Int, rules: List<QualificationRule>): List<TableZone> {
    val zones = MutableList(tableSize) { TableZone.NONE }
    fun paint(positions: IntRange, zone: TableZone, rule: QualificationRule) {
        require(positions.first >= 1 && positions.last <= tableSize) {
            "Regra $rule passa do tamanho da tabela de ${competition.value} ($tableSize)"
        }
        for (p in positions) {
            require(zones[p - 1] == TableZone.NONE) { "Posição $p de ${competition.value} em duas faixas: $rule" }
            zones[p - 1] = zone
        }
    }

    var qualified = 0
    for (rule in rules) {
        when (rule) {
            is QualificationRule.TopN -> if (rule.from == competition) {
                paint(qualified + 1..qualified + rule.n, TableZone.QUALIFICATION, rule)
                qualified += rule.n
            }
            is QualificationRule.Promotion ->
                if (rule.from == competition) paint(1..rule.topN, TableZone.PROMOTION, rule)
            is QualificationRule.Relegation ->
                if (rule.from == competition) paint(tableSize - rule.bottomN + 1..tableSize, TableZone.RELEGATION, rule)
            is QualificationRule.Playoff -> {
                if (rule.upper == competition) paint(rule.upperPosition..rule.upperPosition, TableZone.PLAYOFF, rule)
                if (rule.lower == competition) paint(rule.lowerPosition..rule.lowerPosition, TableZone.PLAYOFF, rule)
            }
            // O campeão da copa não tem posição nesta tabela; o de liga é o 1º, já pintado (ou não) por TopN.
            is QualificationRule.Champion -> Unit
        }
    }
    return zones
}

/**
 * A tabela com a faixa de cada linha (pela posição) e os contadores de
 * acesso/rebaixamento do contrato, tirados das mesmas [rules].
 */
fun Standings.withZones(competition: CompetitionId, rules: List<QualificationRule>): Standings {
    val zones = tableZones(competition, rows.size, rules)
    return copy(
        rows = rows.map { it.copy(zone = zones[it.position - 1]) },
        promotionSpots = zones.count { it == TableZone.PROMOTION },
        relegationSpots = zones.count { it == TableZone.RELEGATION },
    )
}

// ─── A pirâmide de divisões do mundo atual ────────────────────────────────

/**
 * As regras da liga do jogo (issue #47) no modelo novo: em cada par de
 * divisões adjacentes, os últimos N da de cima descem e os primeiros N da de
 * baixo sobem. O N efetivo é limitado pelo tamanho das duas tabelas, o que
 * mantém as divisões com o MESMO número de clubes após a troca.
 *
 * `zipWithNext` percorre os pares adjacentes (1ª↔2ª, 2ª↔3ª…) sem indexação
 * manual. Os tiers devem ser CONTÍGUOS: um buraco na pirâmide (divisões 1 e 3)
 * é erro de seed, não algo para a regra "pontear" em silêncio.
 */
fun pyramidRules(tables: List<Standings>, spots: Int = PROMOTION_RELEGATION_SPOTS): List<QualificationRule> {
    require(spots >= 0) { "spots não pode ser negativo: $spots" }
    val ordered = tables.sortedBy { it.division }
    require(ordered.map { it.division }.toSet().size == ordered.size) {
        "Tabelas com divisão duplicada: ${ordered.map { it.division.value }}"
    }
    return ordered.zipWithNext().flatMap { (upper, lower) ->
        require(lower.division.value == upper.division.value + 1) {
            "Divisões não contíguas na pirâmide: ${upper.division.value} → ${lower.division.value}"
        }
        val exchanged = minOf(spots, upper.rows.size, lower.rows.size)
        val upperId = SeasonCompetitions.leagueId(upper.division)
        val lowerId = SeasonCompetitions.leagueId(lower.division)
        if (exchanged == 0) {
            emptyList()
        } else {
            listOf(
                QualificationRule.Relegation(from = upperId, bottomN = exchanged, to = lowerId),
                QualificationRule.Promotion(from = lowerId, topN = exchanged, to = upperId),
            )
        }
    }
}

/** As tabelas da pirâmide com as faixas de acesso/rebaixamento de [pyramidRules]. */
fun List<Standings>.withPyramidZones(spots: Int = PROMOTION_RELEGATION_SPOTS): List<Standings> {
    val rules = pyramidRules(this, spots)
    return map { it.withZones(SeasonCompetitions.leagueId(it.division), rules) }
}

/**
 * A virada da pirâmide do jogo: divisão da PRÓXIMA temporada de cada clube
 * (inclusive dos que não se movem). Mesmo nome e contrato da regra antiga da
 * issue #47 — quem chama (`PlayRoundService.startNextSeason`) não muda —, agora
 * por [pyramidRules] + [applyQualificationRules].
 *
 * Determinística por construção: as tabelas chegam ordenadas por `position`, e
 * a ordem de entrada das tabelas não importa.
 */
fun applyPromotionRelegation(
    tables: List<Standings>,
    spots: Int = PROMOTION_RELEGATION_SPOTS,
): Map<ClubId, Division> {
    val rules = pyramidRules(tables, spots)
    val divisionOf = tables.associate { SeasonCompetitions.leagueId(it.division) to it.division }
    val byCompetition = tables.associateBy { SeasonCompetitions.leagueId(it.division) }
    // `when` exaustivo sobre o `sealed`: a pirâmide do jogo não declara playoff,
    // então o pendente é inalcançável — `error` documenta a invariante.
    return when (val transition = applyQualificationRules(rules, byCompetition)) {
        is SeasonTransition.Resolved -> transition.memberships.mapValues { (_, league) -> divisionOf.getValue(league) }
        is SeasonTransition.PlayoffsPending -> error("A pirâmide do jogo não tem playoff: ${transition.ties}")
    }
}
