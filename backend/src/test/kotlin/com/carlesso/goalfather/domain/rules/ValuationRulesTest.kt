package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Employment
import com.carlesso.goalfather.domain.model.GameCompetition
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.test.SERIE_B
import com.carlesso.goalfather.test.brazilCatalog
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Valor de mercado (issue #135): curva, monotonicidades e calibração — sem Spring. */
class ValuationRulesTest {

    private val today = LocalDate.of(2026, 3, 1)
    private val reference = MARKET_REFERENCE_STRENGTH

    private fun contract(years: Long, days: Long = 0) =
        Employment.UnderContract(ClubId(1), Money.dollars(1_000), today.minusYears(1), today.plusYears(years).plusDays(days))

    private fun value(overall: Int = 75, age: Int = 25, employment: Employment = contract(3), strength: Int = 70): Money =
        marketValue(makePlayer(1, overall = overall, age = age), employment, LeagueStrength(strength), today)

    @Test
    fun `livre vale zero de taxa`() {
        assertEquals(Money.ZERO, marketValue(makePlayer(1, overall = 92), Employment.FreeAgent, reference, today))
    }

    @Test
    fun `tabela de calibracao na escala da economia do jogo em USD`() {
        // Auge (25 anos), contrato longo, liga de referência. Faixas largas de
        // propósito: o teste trava a ORDEM DE GRANDEZA, não a constante.
        val cases = listOf(
            50 to 500L..1_500L,
            60 to 3_000L..7_000L,
            70 to 20_000L..30_000L,
            80 to 100_000L..200_000L,
            85 to 250_000L..500_000L,
            90 to 600_000L..1_200_000L,
        )
        for ((overall, dollars) in cases) {
            val v = value(overall = overall).cents / 100
            assertTrue(v in dollars, "overall $overall vale US$ $v, fora de $dollars")
        }
    }

    @Test
    fun `mais overall vale mais`() {
        for (age in listOf(18, 25, 33)) {
            // A partir de 50: abaixo disso um veterano já encosta no piso de US$ 100.
            (50..99).map { value(overall = it, age = age) }.zipWithNext().forEach { (lower, higher) ->
                assertTrue(higher > lower, "idade $age: $higher não é maior que $lower")
            }
        }
    }

    @Test
    fun `mais contrato restante vale mais ate 3 anos e depois estabiliza`() {
        // De mês em mês, do contrato que vence hoje ao de 3 anos: estritamente crescente.
        val byMonth = (0..36).map { months ->
            value(overall = 85, employment = Employment.UnderContract(ClubId(1), Money.dollars(1), today.minusYears(1), today.plusMonths(months.toLong())))
        }
        byMonth.zipWithNext().forEach { (shorter, longer) -> assertTrue(longer > shorter, "$longer não é maior que $shorter") }
        assertEquals(value(overall = 85, employment = contract(3)), value(overall = 85, employment = contract(5)))
    }

    @Test
    fun `menos de um ano de contrato derruba forte`() {
        val full = value(employment = contract(3)).cents.toDouble()
        assertTrue(value(employment = contract(1)).cents / full in 0.65..0.75)
        assertTrue(value(employment = contract(0, days = 90)).cents / full < 0.35)
        // Vencido e ainda no clube: o piso do deságio, não zero (zero é o livre).
        val expired = Employment.UnderContract(ClubId(1), Money.dollars(1), today.minusYears(2), today.minusDays(10))
        assertEquals(value(employment = contract(0)), value(employment = expired))
        assertTrue(value(employment = expired).isPositive)
    }

    @Test
    fun `liga mais forte vale mais`() {
        // Dentro da faixa em que o multiplicador não está preso no piso/teto (0,5–1,8).
        (55..83).map { value(overall = 85, strength = it) }.zipWithNext().forEach { (weaker, stronger) ->
            assertTrue(stronger > weaker, "$stronger não é maior que $weaker")
        }
        // Do pack do MVP: Premier League (78) ≈ 1,41×, Série B (62) ≈ 0,71× o Brasileirão (70).
        val serieA = value(strength = 70).cents.toDouble()
        assertTrue(value(strength = 78).cents / serieA in 1.38..1.44)
        assertTrue(value(strength = 62).cents / serieA in 0.68..0.74)
    }

    @Test
    fun `pico de valor entre 24 e 27 anos`() {
        val peak = value(age = 25)
        PEAK_VALUE_AGES.forEach { assertEquals(peak, value(age = it)) }
        // Fora do pico vale menos, e quanto mais longe, menos ainda.
        (15..23).map { value(age = it) }.zipWithNext().forEach { (younger, older) -> assertTrue(older > younger) }
        (28..40).map { value(age = it) }.zipWithNext().forEach { (younger, older) -> assertTrue(older < younger) }
        assertTrue(value(age = 23) < peak)
        assertTrue(value(age = 28) < peak)
    }

    @Test
    fun `todo jogador com contrato tem um valor minimo positivo`() {
        assertEquals(Money.dollars(100), value(overall = 1, age = 50, employment = contract(0), strength = 1))
    }

    @Test
    fun `valor de referencia e o de contrato pleno na liga de referencia`() {
        val player = makePlayer(1, overall = 81, age = 29)
        assertEquals(marketValue(player, contract(3), reference, today), marketReferenceValue(player))
    }

    @Test
    fun `valor no elenco usa a forca da liga do clube e o contrato do jogador`() {
        // Série B com força 62 no catálogo; clube sem liga cai na referência.
        val catalog = brazilCatalog().map { if (it.id == SERIE_B) it.copy(strength = LeagueStrength(62)) else it }
        val club = makeClub().copy(country = Countries.BRAZIL, division = Division(2))
        val player = club.squad.first()

        val employment = assertIs<Employment.UnderContract>(player.employment)
        assertEquals(LeagueStrength(62), catalog.leagueStrengthOf(club))
        assertEquals(marketValue(player, employment, LeagueStrength(62), today), squadMarketValue(player, club, catalog, today))
        assertEquals(reference, emptyList<GameCompetition>().leagueStrengthOf(club))
    }
}
