package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.ContractOffer
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.cents
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.test.makePlayer
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Termos pessoais (issue #138): regra pura e determinística — sem Spring, sem `Random`. */
class ContractNegotiationRulesTest {

    private val today = LocalDate.of(2027, 3, 10)
    private val reference = LeagueStrength(70)

    private fun club(id: Long, reputation: Int = 70, league: Int = 70) =
        ClubStature(ClubId(id), reputation, LeagueStrength(league))

    private val buyer = club(1)

    @Test
    fun `exigencia na referencia e o salario do valor com contrato pleno`() {
        // 75 de overall, 25 anos, liga 70, clube de reputação 70: todos os fatores extras valem 1.
        val player = makePlayer(1, overall = 75, age = 25)
        assertEquals(salaryForValue(fullContractValue(player, reference)), salaryDemand(player, buyer, current = null))
        // Âncora numérica da calibragem: US$ 59.460 de valor → US$ 2.970 por mês.
        assertEquals(Money.dollars(2_970), salaryDemand(player, buyer, current = null))
    }

    @Test
    fun `oferta abaixo da exigencia e recusada com LOW_SALARY e aceita na exigencia`() {
        val player = makePlayer(1, overall = 75, age = 25)
        val demand = salaryDemand(player, buyer, current = null)

        val low = assertIs<PlayerResponse.Refuses>(playerResponse(player, ContractOffer(demand - 10.cents, 3), buyer, null))
        assertEquals(RefusalReason.LOW_SALARY, low.reason)
        assertEquals(demand, low.demandedSalary)
        assertEquals(1..4, low.acceptedYears)

        assertEquals(PlayerResponse.Accepts, playerResponse(player, ContractOffer(demand, 3), buyer, null))
    }

    @ParameterizedTest(name = "{0} anos aceita de {1} a {2} temporadas")
    @CsvSource("16, 1, 3", "17, 1, 3", "18, 2, 5", "23, 2, 5", "24, 1, 4", "29, 1, 4", "30, 2, 4", "32, 2, 4", "33, 1, 2", "36, 1, 2")
    fun `duracao aceita depende da idade e cabe no permitido pela FIFA`(age: Int, first: Int, last: Int) {
        val accepted = acceptedContractYears(age)
        assertEquals(first..last, accepted)
        assertTrue(accepted.first >= allowedContractSeasons(age).first && accepted.last <= allowedContractSeasons(age).last)
    }

    @ParameterizedTest(name = "{0} anos, {1} temporadas → {2}")
    @CsvSource("20, 1, TOO_SHORT", "26, 5, TOO_LONG", "34, 3, TOO_LONG", "31, 1, TOO_SHORT", "17, 4, TOO_LONG", "26, 0, TOO_SHORT")
    fun `duracao fora do que ele topa e recusada mesmo com salario de sobra`(age: Int, years: Int, reason: RefusalReason) {
        val player = makePlayer(1, age = age)
        val response = playerResponse(player, ContractOffer(Money.dollars(1_000_000), years), buyer, null)
        assertEquals(reason, assertIs<PlayerResponse.Refuses>(response).reason)
    }

    @Test
    fun `jogador de clube forte recusa clube muito mais fraco salvo com salario bem acima`() {
        val star = makePlayer(1, overall = 82, age = 27)
        val strong = club(2, reputation = 80)
        val weak = club(1, reputation = 80 - WEAKER_CLUB_GAP)
        val base = salaryDemand(star, weak, strong)

        // O salário-base (que um livre aceitaria) não basta: o degrau cobra +50%.
        val refused = assertIs<PlayerResponse.Refuses>(playerResponse(star, ContractOffer(base, 3), weak, strong))
        assertEquals(RefusalReason.WEAKER_CLUB, refused.reason)
        assertEquals(roundedSalary(base.cents * WEAKER_CLUB_PREMIUM), refused.demandedSalary)
        assertEquals(PlayerResponse.Accepts, playerResponse(star, ContractOffer(refused.demandedSalary, 3), weak, strong))

        // Livre (sem clube atual) aceita o mesmo salário-base do mesmo clube fraco.
        assertEquals(PlayerResponse.Accepts, playerResponse(star, ContractOffer(base, 3), weak, current = null))
        // Abaixo até do salário-base, o motivo é o salário.
        val low = assertIs<PlayerResponse.Refuses>(playerResponse(star, ContractOffer(base - 10.cents, 3), weak, strong))
        assertEquals(RefusalReason.LOW_SALARY, low.reason)
        assertEquals(refused.demandedSalary, low.demandedSalary)
    }

    @Test
    fun `diferenca de reputacao abaixo do degrau nao cobra premio`() {
        val player = makePlayer(1, overall = 78, age = 26)
        val from = club(2, reputation = 75)
        val to = club(1, reputation = 75 - WEAKER_CLUB_GAP + 1)
        val base = salaryDemand(player, to, from)
        assertEquals(PlayerResponse.Accepts, playerResponse(player, ContractOffer(base, 2), to, from))
    }

    @Test
    fun `descer para liga muito mais fraca cobra LOWER_LEAGUE e os dois degraus se multiplicam`() {
        val player = makePlayer(1, overall = 80, age = 26)
        val premier = club(2, reputation = 76, league = 78)
        val serieB = club(1, reputation = 76, league = 78 - LOWER_LEAGUE_GAP)
        val base = salaryDemand(player, serieB, premier)

        val league = assertIs<PlayerResponse.Refuses>(playerResponse(player, ContractOffer(base, 2), serieB, premier))
        assertEquals(RefusalReason.LOWER_LEAGUE, league.reason)
        assertEquals(roundedSalary(base.cents * LOWER_LEAGUE_PREMIUM), league.demandedSalary)

        val both = club(1, reputation = 76 - WEAKER_CLUB_GAP, league = 78 - LOWER_LEAGUE_GAP)
        val bothBase = salaryDemand(player, both, premier)
        val refused = assertIs<PlayerResponse.Refuses>(playerResponse(player, ContractOffer(bothBase, 2), both, premier))
        assertEquals(RefusalReason.WEAKER_CLUB, refused.reason)
        assertEquals(roundedSalary(bothBase.cents * WEAKER_CLUB_PREMIUM * LOWER_LEAGUE_PREMIUM), refused.demandedSalary)
    }

    @Test
    fun `renovacao nao tem degrau e nao aceita reducao de salario`() {
        // Ganha muito acima do que vale hoje (US$ 50 mil por mês): renovar pede pelo menos isso.
        val player = makePlayer(1, overall = 70, age = 26, salary = 50_000_00)
        val own = club(1, reputation = 60, league = 60)
        assertEquals(Money.dollars(50_000), salaryDemand(player, own, current = own))
        val refused = assertIs<PlayerResponse.Refuses>(playerResponse(player, ContractOffer(Money.dollars(49_990), 2), own, own))
        assertEquals(RefusalReason.LOW_SALARY, refused.reason)
        assertEquals(PlayerResponse.Accepts, playerResponse(player, ContractOffer(Money.dollars(50_000), 2), own, own))
    }

    @Test
    fun `clube grande paga em prestigio e veterano nao aceita que o salario despenque com o valor`() {
        val player = makePlayer(1, overall = 78, age = 26)
        val big = salaryDemand(player, club(1, reputation = 82), null)
        val small = salaryDemand(player, club(1, reputation = 60), null)
        assertTrue(big < salaryDemand(player, buyer, null), "clube grande: $big")
        assertTrue(small > salaryDemand(player, buyer, null), "clube pequeno: $small")

        // O valor de um 33 anos é ~30% do de um 27; o salário pedido não cai tanto.
        val peak = makePlayer(1, overall = 78, age = 27)
        val veteran = makePlayer(2, overall = 78, age = 33)
        val valueRatio = fullContractValue(veteran, reference).cents.toDouble() / fullContractValue(peak, reference).cents
        val demandRatio = salaryDemand(veteran, buyer, null).cents.toDouble() / salaryDemand(peak, buyer, null).cents
        assertTrue(demandRatio > valueRatio + 0.1, "valor $valueRatio, pedido $demandRatio")
        // E o garoto aceita menos que o salário do valor.
        val kid = makePlayer(3, overall = 70, age = 19)
        assertTrue(salaryDemand(kid, buyer, null) < salaryForValue(fullContractValue(kid, reference)))
    }

    @Test
    fun `resposta e deterministica`() {
        val player = makePlayer(1, overall = 77, age = 28)
        val offer = ContractOffer(Money.dollars(1_000), 2)
        val responses = (1..5).map { playerResponse(player, offer, buyer, club(2, reputation = 79)) }
        assertEquals(1, responses.toSet().size)
    }

    @Test
    fun `oferta ofensiva esfria a renovacao por quatro semanas e a honesta nao`() {
        val refusal = PlayerResponse.Refuses(RefusalReason.LOW_SALARY, Money.dollars(4_000), 1..4)

        // 74% do pedido: ofensiva.
        assertEquals(today.plusWeeks(4), renewalCooldown(ContractOffer(Money.dollars(2_960), 2), refusal, today))
        // 75%: no limite, não esfria.
        assertNull(renewalCooldown(ContractOffer(Money.dollars(3_000), 2), refusal, today))
        // Recusa só pela duração, com salário cheio: não esfria.
        assertNull(renewalCooldown(ContractOffer(Money.dollars(4_000), 5), refusal.copy(reason = RefusalReason.TOO_LONG), today))
    }

    @Test
    fun `esfriamento vale ate o dia anterior ao fim`() {
        val until = today.plusWeeks(4)
        assertTrue(isCoolingDown(until, today))
        assertTrue(isCoolingDown(until, until.minusDays(1)))
        assertFalse(isCoolingDown(until, until))
        assertFalse(isCoolingDown(null, today))
    }

    @Test
    fun `reputacao do elenco e a media dos 11 melhores`() {
        val squad = (1L..11L).map { makePlayer(it, overall = 80) } + (12L..20L).map { makePlayer(it, overall = 50) }
        assertEquals(80, squadReputation(squad))
        assertEquals(65, squadReputation(listOf(makePlayer(1, overall = 60), makePlayer(2, overall = 70))))
        assertEquals(0, squadReputation(emptyList()))
    }
}
