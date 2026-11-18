package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.sumOfMoney
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.domain.model.world.Reputation
import kotlin.math.roundToLong

/*
 * Economia INICIAL de um mundo novo (issue #131): caixa e preço do
 * ingresso de cada clube quando o jogo nasce do pack. O pack só traz fatos
 * reais (nome, estádio, capacidade, reputação); dinheiro é SIMULADO, por regra
 * nossa — política de direitos em `docs/WORLD_DATA.md`. Tudo em USD (`Money`,
 * centavos), funções puras e sem `Random`: o mesmo pack dá a mesma economia.
 *
 * Calibração: a escala é a do jogo antes da #131 (craque de overall 88 ganhava
 * US$ 11 mil por folha, o clube do usuário tinha US$ 160 mil de caixa), para
 * mercado, prêmios e ampliação de estádio continuarem fazendo sentido.
 */

// O salário inicial saía daqui (`initialSalary`, pelo overall). Com a #136 ele
// é o salário do CONTRATO, derivado do valor de mercado: ver `ContractRules`.

/**
 * Caixa inicial: um número de FOLHAS SALARIAIS que cresce com a reputação —
 * `4 + reputação / 10` folhas (rep 20 → 6, rep 50 → 9, rep 90 → 13).
 *
 * Amarrar à folha (e não a um valor fixo por liga) é o que deixa o mundo
 * equilibrado de saída: a força da liga já está no overall do elenco (o
 * `AttributeGenerator` parte dela) e, portanto, na folha. Um clube grande da
 * Premier League e um pequeno da Série B nascem com o mesmo "fôlego" relativo,
 * e o grande nasce mais rico em dólares. Arredondado a US$ 1.000.
 */
fun initialCash(reputation: Reputation, squad: List<Player>): Money {
    val payroll = squad.sumOfMoney { it.salary }
    val payrolls = CASH_BASE_PAYROLLS + reputation.value / CASH_REPUTATION_PER_PAYROLL
    return Money(roundTo(payroll.cents.toDouble() * payrolls, 1_000_00))
}

private const val CASH_BASE_PAYROLLS: Double = 4.0
private const val CASH_REPUTATION_PER_PAYROLL: Double = 10.0

/**
 * Preço inicial do ingresso: o preço JUSTO da curva de demanda
 * ([fairTicketPriceCents]) para a força esperada do clube, com um prêmio (ou
 * desconto) de até 20% pela reputação — torcida de clube grande paga mais.
 *
 * Partir do preço justo importa porque a IA nunca mexe no ingresso: um preço
 * longe dele esvaziaria (ou lotaria de graça) o estádio do clube pela
 * temporada inteira. A força esperada é a do titular típico no auge
 * ([AttributeGenerator.expectedOverall] aos 27 anos): liga + reputação.
 * Arredondado ao dólar inteiro e dentro da faixa permitida ([TICKET_PRICE_RANGE]).
 * Dólar inteiro, e não US$ 0,50: é o passo do preço que o técnico digita na
 * UI (`TicketPricePanel`, `step={1}`) e o que o `formatMoney` exibe. Um
 * US$ 11,50 aparecia como "US$ 12" e a conta da bilheteria na tela não
 * fechava (revisão da PR #154).
 */
fun initialTicketPrice(leagueStrength: LeagueStrength, reputation: Reputation): Money {
    val strength = AttributeGenerator.expectedOverall(Position.MF, PEAK_AGE, leagueStrength, reputation).toDouble()
    val premium = 1.0 + (reputation.value - Reputation.AVERAGE.value) / TICKET_REPUTATION_DIVISOR
    val raw = fairTicketPriceCents(strength).cents * premium
    return Money(roundTo(raw, CENTS_PER_DOLLAR)).coerceIn(TICKET_PRICE_RANGE)
}

private const val PEAK_AGE: Int = 27

/** `(rep − 50) / 250`: de −20% (rep 1) a +20% (rep 100). */
private const val TICKET_REPUTATION_DIVISOR: Double = 250.0

/** Passo do ingresso inicial: dólar inteiro, o mesmo da UI. */
private const val CENTS_PER_DOLLAR: Long = 100

/** Arredonda [value] (centavos) ao múltiplo mais próximo de [step] centavos. */
private fun roundTo(value: Double, step: Long): Long = (value / step).roundToLong() * step
