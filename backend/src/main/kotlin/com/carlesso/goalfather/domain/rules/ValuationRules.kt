package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Employment
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.leagueOf
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlin.math.roundToLong

/*
 * Valor de mercado (issue #135). Funções PURAS, sem Spring e sem `Random`: o
 * mesmo jogador, no mesmo contrato e na mesma liga, vale sempre o mesmo.
 *
 * O valor é o produto de quatro fatores independentes — cada um testável
 * sozinho e cada um com a sua monotonicidade:
 *
 *   valor = base(overall) × idade(age) × contrato(restante) × liga(força)
 *
 * Calibração (USD, ordem de grandeza do futebol real, constantes NOSSAS — não
 * copiadas de site nenhum): um overall 70 no auge, com contrato longo e numa
 * liga de força 70, vale US$ 2,5 milhões; cada 4 pontos de overall DOBRAM o
 * valor (80 ≈ US$ 14 mi, 85 ≈ US$ 34 mi, 90 ≈ US$ 80 mi; 60 ≈ US$ 440 mil).
 */

/** Overall de referência da curva: [BASE_VALUE_AT_REFERENCE] vale exatamente aqui. */
private const val REFERENCE_OVERALL: Int = 70

/** US$ 2,5 milhões para o overall de referência (no auge, contrato longo, liga 70). */
private const val BASE_VALUE_AT_REFERENCE_CENTS: Double = 2_500_000_00.0

/** Cada 4 pontos de overall dobram o valor: o mercado paga MUITO mais pelo topo. */
private const val OVERALL_DOUBLING_POINTS: Double = 4.0

/** Pico de valor: dos 24 aos 27 anos o fator de idade é 1. */
val PEAK_VALUE_AGES: IntRange = 24..27

/** Antes do pico, cada ano a menos tira 7% (composto): 18 anos ≈ 0,65. */
private const val YOUTH_FACTOR_PER_YEAR: Double = 0.93

/** Depois do pico, cada ano a mais tira 18% (composto): 30 ≈ 0,55, 33 ≈ 0,30. */
private const val VETERAN_FACTOR_PER_YEAR: Double = 0.82

/**
 * Contrato a partir do qual o tempo restante não valoriza mais: 3 anos. Abaixo
 * de 1 ano o valor despenca (o clube comprador sabe que o jogador sai de graça
 * logo), entre 1 e 3 anos cai devagar.
 */
private const val FULL_CONTRACT_DAYS: Long = 3 * 365

private const val ONE_YEAR_DAYS: Long = 365

/** Fator com exatamente 1 ano restante. */
private const val CONTRACT_FACTOR_AT_ONE_YEAR: Double = 0.70

/** Fator de um contrato que vence hoje (ou já venceu e o jogador ainda não saiu). */
private const val CONTRACT_FACTOR_EXPIRING: Double = 0.15

/**
 * Força de liga de referência: multiplicador 1. É também a régua do MERCADO
 * (ver [marketReferenceValue]) — o Brasileirão do pack do MVP tem força 70.
 */
val MARKET_REFERENCE_STRENGTH: LeagueStrength = LeagueStrength(70)

/** Cada 16 pontos de força de liga dobram o valor (Premier 78 ≈ 1,41×; Série B 62 ≈ 0,71×). */
private const val LEAGUE_DOUBLING_POINTS: Double = 16.0

/**
 * Teto e piso do multiplicador de liga. O TETO é o que garante o anti-arbitragem
 * entre ligas (ver `TransferRules`): `SALE_PAYOUT_PERCENT × teto` tem de ficar
 * abaixo de `MARKET_LISTING_PERCENT × 1` — com 60% × 1,8 = 108% < 125%, comprar
 * no mercado e revender de um clube da liga mais forte possível ainda perde.
 */
private val LEAGUE_FACTOR_RANGE: ClosedFloatingPointRange<Double> = 0.5..1.8

/** Menor valor de um jogador com contrato: US$ 10 mil. */
private val MIN_MARKET_VALUE: Money = Money.dollars(10_000)

/** Passo do arredondamento: US$ 1 mil — a tela não mostra centavos de valor. */
private const val VALUE_ROUNDING_CENTS: Long = 1_000_00

/**
 * Valor de mercado de [player], em USD, no vínculo [employment], numa liga de
 * força [leagueStrength], na data [today] (que decide o contrato restante).
 *
 * `when` exaustivo sobre o `sealed` [Employment]: o livre vale ZERO de taxa —
 * ninguém paga transferência por quem não tem clube. Uma terceira forma de
 * vínculo (empréstimo, por exemplo) não compila até decidir quanto vale.
 *
 * Nunca lê `player.salary`: o salário é que sai do valor (#136), não o contrário.
 */
fun marketValue(
    player: Player,
    employment: Employment,
    leagueStrength: LeagueStrength,
    today: LocalDate,
): Money = when (employment) {
    Employment.FreeAgent -> Money.ZERO
    is Employment.UnderContract ->
        valueOf(player, contractFactor(employment.end, today), leagueFactor(leagueStrength))
}

/**
 * Valor do jogador ANUNCIADO no mercado: contrato pleno e liga de referência.
 * O mercado do jogo é global — qualquer clube do mundo compra dele —, então o
 * anúncio não carrega o prêmio de liga de quem vendeu. É essa régua única que
 * fecha a arbitragem entre ligas (ver [LEAGUE_FACTOR_RANGE]).
 */
fun marketReferenceValue(player: Player): Money =
    valueOf(player, contractFactor = 1.0, leagueFactor = leagueFactor(MARKET_REFERENCE_STRENGTH))

/**
 * Valor de [player] no elenco de [club]: o contrato (provisório até a #136) e a
 * força da liga do clube no [catalog] do save.
 */
fun squadMarketValue(player: Player, club: Club, catalog: List<GameCompetition>, today: LocalDate): Money =
    marketValue(player, provisionalEmployment(player, club.id, today), catalog.leagueStrengthOf(club), today)

/**
 * Força da liga de [club] no catálogo. Clube sem liga no save (só joga a
 * continental, issue #132) ou catálogo sem a força: vale a referência — o
 * mesmo multiplicador 1 do mercado.
 */
fun List<GameCompetition>.leagueStrengthOf(club: Club): LeagueStrength =
    leagueOf(club)?.strength ?: MARKET_REFERENCE_STRENGTH

/**
 * Contrato PADRÃO PROVISÓRIO: enquanto o [Player] não tem `employment` (#136),
 * todo jogador de elenco é tratado como tendo assinado hoje um contrato de 3
 * anos com o salário atual. Um helper só, para a integração da #136 ter um
 * único ponto a trocar.
 */
// #136: substituir por player.employment
fun provisionalEmployment(player: Player, clubId: ClubId, today: LocalDate): Employment =
    Employment.UnderContract(
        clubId = clubId,
        salary = player.salary,
        start = today,
        end = today.plusYears(PROVISIONAL_CONTRACT_YEARS),
    )

private const val PROVISIONAL_CONTRACT_YEARS: Long = 3

// ─── Os fatores ──────────────────────────────────────────────────────────────

private fun valueOf(player: Player, contractFactor: Double, leagueFactor: Double): Money {
    val raw = overallBase(player.overall) * ageFactor(player.age) * contractFactor * leagueFactor
    return Money((raw / VALUE_ROUNDING_CENTS).roundToLong() * VALUE_ROUNDING_CENTS).coerceAtLeast(MIN_MARKET_VALUE)
}

/** Curva exponencial no overall, em centavos. `internal`: visível aos testes do módulo. */
internal fun overallBase(overall: Int): Double =
    BASE_VALUE_AT_REFERENCE_CENTS * 2.0.pow((overall - REFERENCE_OVERALL) / OVERALL_DOUBLING_POINTS)

/**
 * Fator de idade: 1 no pico, decaimento composto dos dois lados. `when` com
 * faixas (`in`) é o `switch` de intervalos que o Java não tem.
 */
internal fun ageFactor(age: Int): Double = when {
    age in PEAK_VALUE_AGES -> 1.0
    age < PEAK_VALUE_AGES.first -> YOUTH_FACTOR_PER_YEAR.pow(PEAK_VALUE_AGES.first - age)
    else -> VETERAN_FACTOR_PER_YEAR.pow(age - PEAK_VALUE_AGES.last)
}

/**
 * Deságio pelo contrato restante (em dias, para a curva ser contínua): linear
 * de 0,15 (vence hoje) a 0,70 (1 ano) e de 0,70 a 1 (3 anos); acima disso, 1.
 * `coerceIn` trata o contrato já vencido como "vence hoje".
 */
internal fun contractFactor(end: LocalDate, today: LocalDate): Double {
    val days = ChronoUnit.DAYS.between(today, end).coerceIn(0, FULL_CONTRACT_DAYS)
    return if (days < ONE_YEAR_DAYS) {
        CONTRACT_FACTOR_EXPIRING + (CONTRACT_FACTOR_AT_ONE_YEAR - CONTRACT_FACTOR_EXPIRING) * days / ONE_YEAR_DAYS
    } else {
        CONTRACT_FACTOR_AT_ONE_YEAR +
            (1.0 - CONTRACT_FACTOR_AT_ONE_YEAR) * (days - ONE_YEAR_DAYS) / (FULL_CONTRACT_DAYS - ONE_YEAR_DAYS)
    }
}

/** Multiplicador da liga: exponencial na força, preso em [LEAGUE_FACTOR_RANGE]. */
internal fun leagueFactor(strength: LeagueStrength): Double =
    2.0.pow((strength.value - MARKET_REFERENCE_STRENGTH.value) / LEAGUE_DOUBLING_POINTS)
        .coerceIn(LEAGUE_FACTOR_RANGE)

/** O teto de [LEAGUE_FACTOR_RANGE], exposto para o teste do anti-arbitragem. */
internal val MAX_LEAGUE_FACTOR: Double get() = LEAGUE_FACTOR_RANGE.endInclusive
