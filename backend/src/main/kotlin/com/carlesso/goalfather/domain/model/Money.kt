package com.carlesso.goalfather.domain.model

import com.carlesso.goalfather.domain.serialization.MoneySerializer
import kotlinx.serialization.Serializable

/**
 * Dinheiro do jogo, em **centavos de dólar (USD)** (issue #122).
 *
 * Antes todo valor monetário era um `Long` cru, e nada impedia somar caixa com
 * id de clube ou passar um salário onde se esperava capacidade de estádio.
 * `@JvmInline value class` resolve isso sem custo: em runtime o JVM vê só o
 * `long` primitivo (sem objeto extra), mas o compilador trata `Money` como um
 * tipo distinto — o mesmo idioma de [PlayerId]/[ClubId], agora para unidade.
 *
 * Por que não `BigDecimal`: centavos inteiros já representam dinheiro exato,
 * as contas do jogo nunca dividem centavo, e o contrato OpenAPI fala em
 * inteiro. `BigDecimal` traria escala e arredondamento para resolver um
 * problema que o jogo não tem.
 *
 * O valor pode ser NEGATIVO de propósito: um saldo de rodada ou um déficit é
 * dinheiro com sinal. "Caixa não fica negativo" é invariante do [Club], não
 * da unidade.
 *
 * No JSON sai como inteiro cru (ver [MoneySerializer]) — a forma do contrato
 * não muda.
 */
@JvmInline
@Serializable(with = MoneySerializer::class)
value class Money(val cents: Long) : Comparable<Money> {

    // `operator fun` habilita a sintaxe `a + b`, `a - b`, `a * 3`: o Kotlin
    // traduz o operador para a chamada. Em Java seria `a.plus(b)` explícito.
    operator fun plus(other: Money): Money = Money(cents + other.cents)

    operator fun minus(other: Money): Money = Money(cents - other.cents)

    operator fun times(factor: Int): Money = Money(cents * factor)

    operator fun times(factor: Long): Money = Money(cents * factor)

    operator fun unaryMinus(): Money = Money(-cents)

    /** `<`, `>`, `<=`, `>=` e `coerceAtLeast` saem daqui (via `Comparable`). */
    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    val isNegative: Boolean get() = cents < 0

    val isPositive: Boolean get() = cents > 0

    companion object {
        val ZERO: Money = Money(0)

        /** Dólares inteiros → [Money]. `Money.dollars(50)` = US$ 50,00. */
        fun dollars(amount: Long): Money = Money(amount * 100)
    }
}

/**
 * Extension properties para ler literais como dinheiro: `500_000_00.cents`.
 *
 * Em Java isso exigiria um método estático utilitário (`Money.ofCents(x)`);
 * em Kotlin a "propriedade" é pendurada no próprio `Int`/`Long`, sem mexer na
 * classe original — o literal continua sendo o centavo que o contrato usa.
 */
val Long.cents: Money get() = Money(this)

val Int.cents: Money get() = Money(toLong())

/** Soma uma coleção de [Money] — o `sumOf` da stdlib não conhece o tipo. */
fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, m -> acc + m }

/** `sumOf` para [Money]: `squad.sumOfMoney { it.salary }`. */
inline fun <T> Iterable<T>.sumOfMoney(selector: (T) -> Money): Money =
    fold(Money.ZERO) { acc, item -> acc + selector(item) }
