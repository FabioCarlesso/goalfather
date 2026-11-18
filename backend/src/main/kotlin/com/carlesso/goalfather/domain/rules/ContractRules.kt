package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.Employment
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import java.time.LocalDate
import kotlin.math.roundToLong
import kotlin.random.Random

/*
 * Contratos (issue #136): duração, término, salário inicial e vencimento.
 * Funções PURAS — sem Spring, sem relógio de parede: a data de hoje é sempre
 * a do jogo, recebida por parâmetro, e todo sorteio recebe a seed de fora.
 *
 * O contrato é do VÍNCULO ([Employment.UnderContract] guarda o `clubId`), e o
 * término coincide sempre com o fim de uma temporada da liga do clube: no
 * Brasil, 31 de dezembro; na Europa, 31 de maio (a janela `SeasonSpan` do jogo).
 * É isso que deixa o vencimento acontecer na virada daquela liga, e não na de
 * outra (ver [contractTurnover]).
 */

/** Duração permitida de um contrato, em temporadas (regra da FIFA): de 1 a 5. */
val CONTRACT_SEASONS: IntRange = 1..5

/** Abaixo desta idade o jogador é menor (regra da FIFA). */
const val ADULT_AGE: Int = 18

/** Menor de 18 assina no máximo 3 temporadas (regulamento da FIFA, art. 18). */
const val MAX_MINOR_CONTRACT_SEASONS: Int = 3

/** Temporadas que um contrato de [age] anos pode ter. */
fun allowedContractSeasons(age: Int): IntRange =
    if (age < ADULT_AGE) CONTRACT_SEASONS.first..MAX_MINOR_CONTRACT_SEASONS else CONTRACT_SEASONS

/**
 * A janela de temporada que manda nos contratos de um clube de [country]: a da
 * confederação, a mesma regra com que o seed monta o `span` das ligas do país
 * (`SeasonSpan.of`). Uma fonte só para o término do contrato e o calendário.
 */
fun contractSpanOf(country: Country): SeasonSpan = SeasonSpan.of(country.confederation)

/** Atalho: a janela dos contratos do elenco de [this]. */
val Club.contractSpan: SeasonSpan get() = contractSpanOf(country)

/**
 * A temporada em curso (ou a próxima, na pausa entre temporadas) em [date]: a
 * primeira cujo fim não ficou para trás. Toda janela do jogo termina no ano em
 * que começa ou no seguinte, então só há dois candidatos — `first` sobre a
 * faixa evita o `if` aninhado.
 */
fun SeasonSpan.seasonOn(date: LocalDate): Int =
    (date.year - 1..date.year).first { !end(it).isBefore(date) }

/** Por que um contrato foi recusado. `sealed`: o `when` do chamador é exaustivo. */
sealed interface ContractRejection {
    /** Duração fora do permitido para a idade (1–5; menor de 18, 1–3). */
    data class InvalidDuration(val seasons: Int, val allowed: IntRange) : ContractRejection
}

/**
 * Resultado de uma assinatura: erro de negócio como VALOR (CLAUDE.md), não
 * exception. Um contrato de 4 anos para um garoto de 17 não é "excepcional" —
 * é uma proposta que a regra recusa.
 */
sealed interface ContractResult {
    data class Signed(val contract: Employment.UnderContract) : ContractResult
    data class Rejected(val reason: ContractRejection) : ContractResult
}

/**
 * Assina um contrato de [seasons] temporadas entre [player] e [clubId], a
 * partir de [start] (a data do jogo), na temporada [season] da janela [span].
 * A temporada corrente conta como a primeira: um contrato de 1 temporada
 * assinado em março termina em dezembro (Brasil).
 */
fun signContract(
    player: Player,
    clubId: ClubId,
    salary: Money,
    start: LocalDate,
    season: Int,
    span: SeasonSpan,
    seasons: Int,
    releaseClause: Money? = null,
): ContractResult {
    val allowed = allowedContractSeasons(player.age)
    if (seasons !in allowed) return ContractResult.Rejected(ContractRejection.InvalidDuration(seasons, allowed))
    return ContractResult.Signed(
        Employment.UnderContract(
            clubId = clubId,
            salary = salary,
            start = start,
            end = span.end(season + seasons - 1),
            releaseClause = releaseClause,
        ),
    )
}

/**
 * Duração SORTEADA de um contrato que já existe quando o mundo nasce (seed do
 * pack) ou que a IA renova: jovem assina longo, veterano assina curto. Sempre
 * dentro de [allowedContractSeasons] — o `coerceIn` corta o sorteio na regra.
 */
fun typicalContractSeasons(age: Int, rng: Random): Int {
    val band = when {
        age < ADULT_AGE -> 2..3
        age <= 23 -> 2..5
        age <= 29 -> 1..4
        age <= 32 -> 1..3
        else -> 1..2
    }
    val allowed = allowedContractSeasons(age)
    return band.random(rng).coerceIn(allowed.first, allowed.last)
}

/**
 * Duração de um contrato assinado pelo técnico (compra no mercado) enquanto
 * não há negociação de termos (#138): 3 temporadas, que cabem em qualquer idade.
 */
const val DEFAULT_SIGNING_SEASONS: Int = 3

// ─── Salário a partir do valor ─────────────────────────────────────────────

/**
 * Salário MENSAL = valor de mercado / [VALUE_PER_MONTHLY_SALARY]. Calibrado
 * junto com a curva de `marketValue` (#135), na escala da economia do jogo: um
 * 80 no auge vale ~US$ 141 mil e ganha ~US$ 7 mil; um 70, ~US$ 1,25 mil. Se a
 * escala da curva mudar, é este divisor que acompanha — o salário continua
 * saindo do valor, nunca o contrário.
 */
const val VALUE_PER_MONTHLY_SALARY: Long = 20

/** Piso: ninguém do elenco principal ganha menos de US$ 500 por mês. */
val MIN_CONTRACT_SALARY: Money = Money.dollars(500)

/** Salário de quem vale [value], arredondado a US$ 10 e com piso. */
fun salaryForValue(value: Money): Money {
    val raw = value.cents.toDouble() / VALUE_PER_MONTHLY_SALARY
    return Money((raw / SALARY_STEP_CENTS).roundToLong() * SALARY_STEP_CENTS).coerceAtLeast(MIN_CONTRACT_SALARY)
}

private const val SALARY_STEP_CENTS: Long = 10_00

/**
 * Contrato com salário DERIVADO do valor (issue #136): monta o contrato com
 * salário zero só para perguntar a `marketValue` quanto o jogador vale com
 * aquele vínculo (o deságio por contrato restante é da #135), e então fixa o
 * salário a partir do valor. `copy` reaproveita a validação do `init`.
 */
fun Employment.UnderContract.pricedFor(player: Player, leagueStrength: LeagueStrength, today: LocalDate): Employment.UnderContract {
    val draft = copy(salary = Money.ZERO)
    return draft.copy(salary = salaryForValue(marketValue(player, draft, leagueStrength, today)))
}

// ─── Vínculo do jogador ────────────────────────────────────────────────────

/** O jogador sem clube: sai do vínculo e, com ele, do salário e da cláusula. */
fun Player.released(): Player = copy(employment = Employment.FreeAgent)

/** O jogador com [contract] — trocar de clube é trocar de contrato. */
fun Player.signed(contract: Employment.UnderContract): Player = copy(employment = contract)

/**
 * O jogador transferido para [club] (proposta aceita entre técnicos): o
 * vínculo passa a ser com o comprador, que assume salário e cláusula, a partir
 * de [today]. O término é realinhado ao fim de temporada da liga do comprador
 * que cobre o término antigo — um contrato brasileiro até dezembro vira, na
 * Europa, um até o maio seguinte. Livre (ou [today] desconhecido) não muda.
 */
fun Player.transferredTo(club: Club, today: LocalDate?): Player {
    val current = contract ?: return this
    val start = today ?: current.start
    val span = club.contractSpan
    val end = maxOf(span.end(span.seasonOn(current.end)), span.end(span.seasonOn(start.plusDays(1))))
    return signed(current.copy(clubId = club.id, start = start, end = end))
}

/**
 * O contrato está na última temporada em [today]? Como todo término cai num
 * fim de temporada e uma temporada dura um ano, basta [today] ter passado do
 * término menos um ano (o fim da temporada anterior). Independe da liga.
 */
fun Employment.UnderContract.isInFinalSeason(today: LocalDate): Boolean = today.isAfter(end.minusYears(1))

/** Vence até [seasonEnd] (inclusive)? O livre não tem o que vencer. */
fun Player.contractExpiresBy(seasonEnd: LocalDate): Boolean = contract?.let { !it.end.isAfter(seasonEnd) } ?: false

// ─── Vencimento na virada da temporada ─────────────────────────────────────

/** Elenco depois dos vencimentos + quem saiu livre + quem a IA renovou. */
data class ContractTurnover(
    val squad: List<Player>,
    val released: List<Player>,
    val renewed: List<Player>,
)

/**
 * Vencimentos de [club] na virada da temporada [endedSeason] da liga DELE
 * (issue #136): vence quem tem contrato terminando até o fim daquela temporada,
 * na janela do clube ([contractSpan]). Quem chama é a virada do país do clube —
 * por isso o Brasil não derruba contratos europeus em dezembro.
 *
 * - **Clube de técnico humano:** o contrato vencido vira [Employment.FreeAgent]
 *   e o jogador sai do elenco (sem taxa: quem quiser, contrata de graça). A
 *   renovação negociada, com exigência do jogador, é a #138.
 * - **Clube da IA:** renova sozinho, pelo mesmo salário, por uma duração
 *   típica da idade ([typicalContractSeasons]). Provisório até a IA ativa
 *   (#143): sem isso os elencos da IA, que não compram ninguém, encolheriam a
 *   cada virada até não fechar um time.
 *
 * A seed vem de fora ([renewalSeed]): a mesma virada dá as mesmas renovações.
 */
fun contractTurnover(club: Club, endedSeason: Int): ContractTurnover {
    val span = club.contractSpan
    val seasonEnd = span.end(endedSeason)
    // `partition` divide a lista em duas por um predicado — o par (venceu, segue).
    val (expiring, staying) = club.squad.partition { it.contractExpiresBy(seasonEnd) }
    if (expiring.isEmpty()) return ContractTurnover(club.squad, released = emptyList(), renewed = emptyList())

    if (club.ownerId != null) {
        return ContractTurnover(squad = staying, released = expiring.map { it.released() }, renewed = emptyList())
    }
    val renewed = expiring.map { player -> player.renewedByAi(seasonEnd, endedSeason, span) }
    val byId = renewed.associateBy { it.id }
    // Mantém a ordem do elenco: só troca o contrato de quem renovou.
    return ContractTurnover(squad = club.squad.map { byId[it.id] ?: it }, released = emptyList(), renewed = renewed)
}

private fun Player.renewedByAi(seasonEnd: LocalDate, endedSeason: Int, span: SeasonSpan): Player {
    // `contract` não é nulo aqui (só vence quem tem contrato): `requireNotNull`
    // documenta o invariante sem `!!`.
    val current = requireNotNull(contract) { "renovação de jogador sem contrato: $id" }
    val seasons = typicalContractSeasons(age, Random(renewalSeed(endedSeason, id)))
    return signed(current.copy(start = seasonEnd.plusDays(1), end = span.end(endedSeason + seasons)))
}

/** Seed da renovação automática: temporada + jogador, disjunta das outras. */
fun renewalSeed(season: Int, player: PlayerId): Long = (season.toLong() * 1_000_003L) xor player.value xor RENEWAL_SALT

private const val RENEWAL_SALT: Long = 0x0136_C0DEL
