package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Employment
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.cents
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Regras puras de contrato (issue #136) — sem Spring, sem relógio. */
class ContractRulesTest {

    private val march2027 = LocalDate.of(2027, 3, 10)

    private fun contract(end: LocalDate, club: Long = 1, salary: Money = 10_000_00.cents) =
        Employment.UnderContract(ClubId(club), salary, start = LocalDate.of(2024, 1, 1), end = end)

    @Test
    fun `menor de 18 com mais de 3 temporadas e recusado`() {
        val kid = makePlayer(1, age = 17)
        val result = signContract(kid, ClubId(1), 1_000_00.cents, march2027, 2027, SeasonSpan.CALENDAR_YEAR, seasons = 4)

        val rejected = assertIs<ContractResult.Rejected>(result)
        assertEquals(ContractRejection.InvalidDuration(seasons = 4, allowed = 1..3), rejected.reason)
        // 3 temporadas ele pode.
        assertIs<ContractResult.Signed>(signContract(kid, ClubId(1), 1_000_00.cents, march2027, 2027, SeasonSpan.CALENDAR_YEAR, 3))
    }

    @Test
    fun `adulto assina de 1 a 5 temporadas e nada fora disso`() {
        val adult = makePlayer(1, age = 25)
        fun sign(seasons: Int) = signContract(adult, ClubId(1), Money.ZERO, march2027, 2027, SeasonSpan.CALENDAR_YEAR, seasons)

        (1..5).forEach { assertIs<ContractResult.Signed>(sign(it), "$it temporadas") }
        assertIs<ContractResult.Rejected>(sign(0))
        assertIs<ContractResult.Rejected>(sign(6))
        assertEquals(1..5, allowedContractSeasons(18))
        assertEquals(1..3, allowedContractSeasons(17))
    }

    @Test
    fun `termino coincide com o fim da temporada da liga do clube`() {
        val player = makePlayer(1, age = 25)
        // Brasil: a temporada corrente conta como a 1ª — 2 temporadas a partir de 2027 = até 31/12/2028.
        val brazil = assertIs<ContractResult.Signed>(
            signContract(player, ClubId(1), Money.ZERO, march2027, 2027, SeasonSpan.CALENDAR_YEAR, 2),
        ).contract
        assertEquals(LocalDate.of(2028, 12, 31), brazil.end)
        assertEquals(march2027, brazil.start)
        // Europa: temporada 2026 = 2026/27; 2 temporadas = até 31/05/2028.
        val europe = assertIs<ContractResult.Signed>(
            signContract(player, ClubId(1), Money.ZERO, march2027, 2026, SeasonSpan.AUGUST_TO_MAY, 2),
        ).contract
        assertEquals(LocalDate.of(2028, 5, 31), europe.end)
    }

    @Test
    fun `temporada em curso numa data respeita a janela da liga`() {
        assertEquals(2027, SeasonSpan.CALENDAR_YEAR.seasonOn(march2027))
        // Março de 2027 é a temporada 2026/27 na Europa; julho já é a próxima (pausa).
        assertEquals(2026, SeasonSpan.AUGUST_TO_MAY.seasonOn(march2027))
        assertEquals(2027, SeasonSpan.AUGUST_TO_MAY.seasonOn(LocalDate.of(2027, 7, 1)))
        assertEquals(SeasonSpan.AUGUST_TO_MAY, contractSpanOf(Countries.ENGLAND))
        assertEquals(SeasonSpan.CALENDAR_YEAR, contractSpanOf(Countries.BRAZIL))
    }

    @Test
    fun `duracao tipica sorteada fica sempre dentro da regra da idade`() {
        val rng = Random(136)
        for (age in 15..40) {
            repeat(50) {
                val seasons = typicalContractSeasons(age, rng)
                assertTrue(seasons in allowedContractSeasons(age), "idade $age: $seasons temporadas")
            }
        }
        // Veterano assina curto.
        repeat(50) { assertTrue(typicalContractSeasons(35, rng) <= 2) }
    }

    @Test
    fun `salario sai do valor arredondado a 10 dolares e com piso`() {
        assertEquals(Money.dollars(6_400), salaryForValue(Money.dollars(64_000_000)))
        assertEquals(MIN_CONTRACT_SALARY, salaryForValue(Money.dollars(1_000)))
        assertEquals(0L, salaryForValue(Money.dollars(12_345_678)).cents % 10_00)
        // Valor maior, salário maior: o salário segue o valor, nunca o contrário.
        assertTrue(salaryForValue(Money.dollars(80_000_000)) > salaryForValue(Money.dollars(40_000_000)))
    }

    @Test
    fun `contrato precificado usa o valor de mercado`() {
        val player = makePlayer(1, overall = 80)
        val priced = contract(LocalDate.of(2028, 12, 31)).pricedFor(player, NEUTRAL_LEAGUE_STRENGTH, march2027)
        assertEquals(salaryForValue(marketValue(player, priced, NEUTRAL_LEAGUE_STRENGTH, march2027)), priced.salary)
    }

    @Test
    fun `ultima temporada do contrato e a que termina no termino`() {
        val endsDec2027 = contract(LocalDate.of(2027, 12, 31))
        assertTrue(endsDec2027.isInFinalSeason(march2027))
        assertTrue(endsDec2027.isInFinalSeason(LocalDate.of(2027, 1, 1)))
        assertFalse(endsDec2027.isInFinalSeason(LocalDate.of(2026, 12, 31)))
        assertFalse(contract(LocalDate.of(2028, 12, 31)).isInFinalSeason(march2027))
    }

    @Test
    fun `virada do clube humano solta quem venceu e mantem o resto`() {
        val end2026 = LocalDate.of(2026, 12, 31)
        val club = makeClub(id = 1, squadSize = 2, ownerId = 7).let { c ->
            c.copy(squad = c.squad + makePlayer(9).copy(employment = contract(end2026)))
        }

        val turn = contractTurnover(club, endedSeason = 2026)

        assertEquals(listOf(1L, 2L), turn.squad.map { it.id.value })
        val released = turn.released.single()
        assertEquals(9L, released.id.value)
        assertEquals(Employment.FreeAgent, released.employment)
        assertEquals(Money.ZERO, released.salary, "livre não tem salário")
        assertTrue(turn.renewed.isEmpty())
    }

    @Test
    fun `virada do clube da IA renova quem venceu pelo mesmo salario`() {
        val end2026 = LocalDate.of(2026, 12, 31)
        val club = makeClub(id = 2, squadSize = 2).let { c ->
            c.copy(squad = c.squad + makePlayer(9, age = 30).copy(employment = contract(end2026, club = 2)))
        }

        val turn = contractTurnover(club, endedSeason = 2026)

        assertTrue(turn.released.isEmpty())
        assertEquals(3, turn.squad.size)
        val renewed = assertNotNull(turn.squad.last().contract)
        assertEquals(LocalDate.of(2027, 1, 1), renewed.start)
        assertTrue(renewed.end in (1..3).map { LocalDate.of(2026 + it, 12, 31) }, "30 anos: 1 a 3 temporadas")
        assertEquals(10_000_00.cents, renewed.salary)
        // Determinístico: a mesma virada renova igual.
        assertEquals(turn, contractTurnover(club, endedSeason = 2026))
    }

    @Test
    fun `vencimento usa o fim da temporada da janela do proprio clube`() {
        // Clube inglês na virada da temporada 2025 (2025/26, termina em 31/05/2026).
        val endMay2026 = LocalDate.of(2026, 5, 31)
        val endMay2027 = LocalDate.of(2027, 5, 31)
        val english = makeClub(id = 3, squadSize = 0, ownerId = 7).copy(
            country = Countries.ENGLAND,
            squad = listOf(
                makePlayer(1).copy(employment = contract(endMay2026, club = 3)),
                makePlayer(2).copy(employment = contract(endMay2027, club = 3)),
            ),
        )

        val turn = contractTurnover(english, endedSeason = 2025)

        assertEquals(listOf(1L), turn.released.map { it.id.value })
        assertEquals(listOf(2L), turn.squad.map { it.id.value })
    }

    @Test
    fun `transferencia troca o vinculo e realinha o termino a liga do comprador`() {
        val seller = contract(LocalDate.of(2027, 12, 31), club = 1).copy(releaseClause = 5_000_000_00.cents)
        val player = makePlayer(9).copy(employment = seller)
        val buyer = makeClub(id = 5, squadSize = 0).copy(country = Countries.SPAIN)

        val moved = assertNotNull(player.transferredTo(buyer, today = march2027).contract)

        assertEquals(ClubId(5), moved.clubId)
        assertEquals(march2027, moved.start)
        // Dezembro de 2027 cai na temporada 2027/28 da Europa: vai até maio de 2028.
        assertEquals(LocalDate.of(2028, 5, 31), moved.end)
        assertEquals(seller.salary, moved.salary)
        assertEquals(seller.releaseClause, moved.releaseClause)
    }
}
