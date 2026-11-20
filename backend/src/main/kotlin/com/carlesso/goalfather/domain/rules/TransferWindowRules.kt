package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.Confederation
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.CountryCode
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.world.TransferWindow
import com.carlesso.goalfather.domain.model.world.TransferWindowScope
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.Month

/**
 * Janelas de transferência (issue #137): o mercado abre e fecha por
 * federação e por data. Funções PURAS sobre a lista de janelas do save — quem
 * lê o banco e a data do relógio é o `TransferWindowGuard` da aplicação.
 *
 * **A janela que vale é a do clube COMPRADOR** (quem registra o jogador). Na
 * venda rápida ao mercado não há comprador: vale a do vendedor.
 *
 * **Granularidade de dia.** A issue fala em semana, mas o relógio do jogo já
 * tem dia — o do encaixe corrente (quarta ou domingo) —, e as bordas reais
 * caem no meio da semana (a CBF fecha numa terça, 03/03, e abre a doméstica
 * na quarta, 04/03). Por semana, as duas janelas se sobreporiam.
 *
 * **Anos sem dado.** O pack traz 2026 e 2027; o jogo segue além. Para um ano
 * que a federação não tem no pack, vale o último ano conhecido dela repetido
 * no mesmo dia e mês ([projectedFor]) — o mesmo espírito das datas FIFA
 * interinas (`FifaCalendar.kt`). Federação sem janela nenhuma no pack fica com
 * o mercado fechado e sem data de abertura.
 */

/**
 * A situação do mercado de uma federação numa data. `sealed` porque os dois
 * casos carregam dados diferentes — quem consome faz `when` exaustivo e não
 * consegue ler `closes` de um mercado fechado.
 */
sealed interface WindowStatus {
    /** Aberta até [closes] (inclusivo); [scope] diz se aceita transferência de fora do país. */
    data class Open(val closes: LocalDate, val scope: TransferWindowScope) : WindowStatus

    /** Fechada; [nextOpens] é a próxima abertura, ou `null` se a federação não tem calendário. */
    data class Closed(val nextOpens: LocalDate?) : WindowStatus
}

/**
 * Por que uma transferência não pode acontecer agora. Erro de negócio como
 * valor: cada serviço traduz para o seu resultado (`TransferResult`,
 * `OfferResult`), o domínio não sabe qual é.
 */
sealed interface TransferBlock {
    /** Janela do comprador fechada (ou só doméstica para uma transferência de fora). */
    data class WindowClosed(val nextOpens: LocalDate?) : TransferBlock

    /** O jogador já esgotou o limite de registros — ver [RegistrationLimit]. */
    data class LimitReached(val limit: RegistrationLimit) : TransferBlock
}

/**
 * Limite simplificado de registros (issue #137; a FIFA permite 3 clubes por
 * temporada, jogando por 2). Registro = o jogador entrar num clube: compra no
 * mercado ou proposta aceita. A venda ao mercado não registra ninguém.
 * `@Serializable` porque viaja dentro do `TransferResult` (que é serializável).
 */
@Serializable
enum class RegistrationLimit(val max: Int) {
    /** No máximo um registro por janela. */
    ONE_PER_WINDOW(1),

    /** No máximo dois registros por temporada. */
    TWO_PER_SEASON(2),
}

/**
 * As janelas de [federation] no ano [year]: as do pack, se houver; senão as do
 * ano conhecido mais próximo (o último anterior, ou o primeiro se [year] vier
 * antes de todos), deslocadas para [year]. `plusYears` cuida do 29/02.
 *
 * `toSortedSet()` devolve um `TreeSet` — `lastOrNull { }` sobre ele percorre
 * em ordem, então "o último ano anterior" sai sem `sorted()` à parte.
 */
fun List<TransferWindow>.projectedFor(federation: CountryCode, year: Int): List<TransferWindow> {
    val own = filter { it.country.code == federation }
    val known = own.mapTo(sortedSetOf()) { it.start.year }
    if (known.isEmpty()) return emptyList()
    if (year in known) return own.filter { it.start.year == year }
    val source = known.lastOrNull { it < year } ?: known.first()
    val shift = (year - source).toLong()
    return own
        .filter { it.start.year == source }
        .map { it.copy(start = it.start.plusYears(shift), end = it.end.plusYears(shift)) }
}

/** As janelas de [federation] que importam para [date]: o ano dela e os vizinhos, em ordem. */
private fun List<TransferWindow>.around(federation: CountryCode, date: LocalDate): List<TransferWindow> =
    (date.year - 1..date.year + 1)
        .flatMap { projectedFor(federation, it) }
        .sortedBy { it.start }

/**
 * A janela de [federation] aberta em [date], ou `null`. Se duas se
 * sobrepõem (não acontece no pack), a internacional ganha: é a mais permissiva.
 */
fun List<TransferWindow>.openOn(federation: CountryCode, date: LocalDate): TransferWindow? =
    around(federation, date)
        .filter { date in it }
        .maxByOrNull { it.scope == TransferWindowScope.INTERNATIONAL }

/**
 * A próxima abertura de [federation] DEPOIS de [date]; com [internationalOnly],
 * só a de uma janela internacional (o que interessa a quem foi barrado pela
 * janela só doméstica). Busca até dois anos à frente.
 */
fun List<TransferWindow>.nextOpening(
    federation: CountryCode,
    date: LocalDate,
    internationalOnly: Boolean = false,
): LocalDate? =
    (date.year..date.year + 2)
        .flatMap { projectedFor(federation, it) }
        .filter { it.start > date && (!internationalOnly || it.scope == TransferWindowScope.INTERNATIONAL) }
        .minOfOrNull { it.start }

/**
 * A regra da issue: `windowStatus(federação, data)`. O `?.let { } ?: ` é o
 * "se achou, Open; senão, Closed" numa expressão só, sem `if`/`return`.
 */
fun windowStatus(federation: CountryCode, date: LocalDate, windows: List<TransferWindow>): WindowStatus =
    windows.openOn(federation, date)
        ?.let { WindowStatus.Open(closes = it.end, scope = it.scope) }
        ?: WindowStatus.Closed(nextOpens = windows.nextOpening(federation, date))

/**
 * A janela permite registrar no clube de [buyer] um jogador vindo de
 * [origin] (`null` = sem clube de origem: o mercado)? Fechada → bloqueio com a
 * próxima abertura. Só doméstica e [origin] de outro país → bloqueio com a
 * próxima abertura INTERNACIONAL. Do mercado não há federação de origem, então
 * a janela doméstica aceita (a transferência internacional de verdade é a #142).
 */
fun windowBlocker(
    buyer: CountryCode,
    origin: CountryCode?,
    date: LocalDate,
    windows: List<TransferWindow>,
): TransferBlock.WindowClosed? {
    val open = windows.openOn(buyer, date)
        ?: return TransferBlock.WindowClosed(windows.nextOpening(buyer, date))
    val international = origin != null && origin != buyer
    return if (international && open.scope == TransferWindowScope.DOMESTIC_ONLY) {
        TransferBlock.WindowClosed(windows.nextOpening(buyer, date, internationalOnly = true))
    } else {
        null
    }
}

/**
 * A temporada de registro de [confederation] que contém [date]. No ano civil
 * (Brasil), janeiro a dezembro. Na UEFA a temporada é agosto–maio, mas a
 * janela de verão abre em junho, ANTES do primeiro jogo: a temporada de
 * registro começa em 1º de junho, para a janela de verão contar na temporada
 * que ela prepara.
 */
fun registrationSeason(confederation: Confederation, date: LocalDate): ClosedRange<LocalDate> =
    when (SeasonSpan.of(confederation)) {
        SeasonSpan.CALENDAR_YEAR -> LocalDate.of(date.year, Month.JANUARY, 1)..LocalDate.of(date.year, Month.DECEMBER, 31)
        SeasonSpan.AUGUST_TO_MAY -> {
            val startYear = if (date.month >= Month.JUNE) date.year else date.year - 1
            LocalDate.of(startYear, Month.JUNE, 1)..LocalDate.of(startYear + 1, Month.MAY, 31)
        }
    }

/**
 * Tudo o que barra registrar um jogador em [buyer] em [date]: a janela
 * ([windowBlocker]) e o limite de registros, contado sobre as datas dos
 * registros anteriores do jogador ([previous]). O registro na janela corrente
 * esgota [RegistrationLimit.ONE_PER_WINDOW]; dois na temporada de registro do
 * comprador esgotam [RegistrationLimit.TWO_PER_SEASON]. `null` = pode.
 */
fun registrationBlocker(
    buyer: Country,
    origin: CountryCode?,
    date: LocalDate,
    windows: List<TransferWindow>,
    previous: List<LocalDate>,
): TransferBlock? {
    windowBlocker(buyer.code, origin, date, windows)?.let { return it }
    // `windowBlocker` nulo garante janela aberta: o `?:` nunca cai no `return null`.
    val window = windows.openOn(buyer.code, date) ?: return null
    val season = registrationSeason(buyer.confederation, date)
    return when {
        previous.count { it in window.start..date } >= RegistrationLimit.ONE_PER_WINDOW.max ->
            TransferBlock.LimitReached(RegistrationLimit.ONE_PER_WINDOW)
        previous.count { it in season } >= RegistrationLimit.TWO_PER_SEASON.max ->
            TransferBlock.LimitReached(RegistrationLimit.TWO_PER_SEASON)
        else -> null
    }
}
