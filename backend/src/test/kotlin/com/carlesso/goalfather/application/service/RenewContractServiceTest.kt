package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.ContractTalksRepository
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.ContractOffer
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Slot
import com.carlesso.goalfather.domain.result.RenewalResult
import com.carlesso.goalfather.domain.rules.MARKET_REFERENCE_STRENGTH
import com.carlesso.goalfather.domain.rules.RENEWAL_COOLDOWN_WEEKS
import com.carlesso.goalfather.domain.rules.RefusalReason
import com.carlesso.goalfather.domain.rules.salaryDemand
import com.carlesso.goalfather.domain.rules.stature
import com.carlesso.goalfather.test.InMemoryCatalogRepository
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Renovação com o próprio elenco (issue #138) — serviço com repositórios falsos, sem Spring. */
class RenewContractServiceTest {

    /** Esfriamentos em memória: o fake mais simples do port. */
    private class InMemoryTalks : ContractTalksRepository {
        val cooldowns = mutableMapOf<Pair<PlayerId, ClubId>, LocalDate>()
        override suspend fun cooldownUntil(playerId: PlayerId, clubId: ClubId): LocalDate? = cooldowns[playerId to clubId]
        override suspend fun startCooldown(playerId: PlayerId, clubId: ClubId, until: LocalDate) {
            cooldowns[playerId to clubId] = until
        }
    }

    private val today = LocalDate.of(2027, 3, 10)
    private val clubRepo: ClubRepository = mockk()
    private val leagueRepo: LeagueRepository = mockk {
        coEvery { findLatest() } returns Round(Matchday.of(today, Slot.MIDWEEK), season = 2027, matches = emptyList())
        coEvery { currentStandings() } returns emptyList()
    }
    private val talks = InMemoryTalks()
    private val service = RenewContractService(clubRepo, leagueRepo, InMemoryCatalogRepository(emptyList()), talks)

    // Contrato atual: US$ 100 por mês (o `makePlayer` grava salário em centavos).
    private val club = makeClub(ownerId = 7)
    private val player = club.squad[3]
    private val demand = salaryDemand(player, club.stature(MARKET_REFERENCE_STRENGTH), club.stature(MARKET_REFERENCE_STRENGTH))

    private fun stubSave(): io.mockk.CapturingSlot<Club> {
        val saved = slot<Club>()
        coEvery { clubRepo.findById(club.id) } returns club
        coEvery { clubRepo.save(capture(saved)) } answers { ClubSaveResult.Saved(saved.captured) }
        return saved
    }

    @Test
    fun `renovacao aceita substitui o contrato e reinicia a duracao`() = runTest {
        val saved = stubSave()
        val before = assertNotNull(player.contract)

        val result = service.renew(club.id, player.id, ContractOffer(demand, years = 4))

        val renewed = assertIs<RenewalResult.Renewed>(result)
        val contract = assertNotNull(renewed.player.contract)
        assertEquals(today, contract.start)
        // 4 temporadas contando a corrente (Brasil, 2027): até 31/12/2030.
        assertEquals(LocalDate.of(2030, 12, 31), contract.end)
        assertEquals(demand, contract.salary)
        assertEquals(club.id, contract.clubId)
        assert(contract != before)
        // Mesmo lugar no elenco, mesmo tamanho: só o contrato mudou.
        assertEquals(club.squad.size, saved.captured.squad.size)
        assertEquals(renewed.player, saved.captured.squad[3])
    }

    @Test
    fun `recusa honesta nao esfria e o tecnico pode tentar de novo`() = runTest {
        stubSave()
        // 90% do pedido: recusa, mas sem ofensa.
        val offer = ContractOffer(Money(demand.cents * 9 / 10), years = 2)

        val refused = assertIs<RenewalResult.PlayerRefused>(service.renew(club.id, player.id, offer))

        assertEquals(RefusalReason.LOW_SALARY, refused.refusal.reason)
        assertEquals(demand, refused.refusal.demandedSalary)
        assertNull(refused.cooldownUntil)
        assertIs<RenewalResult.Renewed>(service.renew(club.id, player.id, ContractOffer(demand, years = 2)))
    }

    @Test
    fun `oferta ofensiva esfria e a proxima proposta e barrada ate a data`() = runTest {
        stubSave()

        val refused = assertIs<RenewalResult.PlayerRefused>(service.renew(club.id, player.id, ContractOffer(Money.dollars(1), years = 2)))
        val until = today.plusWeeks(RENEWAL_COOLDOWN_WEEKS)
        assertEquals(until, refused.cooldownUntil)

        // Nem a oferta certa é ouvida enquanto esfria.
        assertEquals(RenewalResult.TalksCoolingDown(player.id, until), service.renew(club.id, player.id, ContractOffer(demand, years = 2)))
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `jogador fora do elenco e PlayerNotInSquad`() = runTest {
        stubSave()
        assertEquals(RenewalResult.PlayerNotInSquad(PlayerId(999)), service.renew(club.id, PlayerId(999), ContractOffer(demand, 2)))
    }

    @Test
    fun `conflito de lock otimista e repetido e, se persistir, volta como Conflict`() = runTest {
        coEvery { clubRepo.findById(club.id) } returns club
        coEvery { clubRepo.save(any()) } returns ClubSaveResult.Conflict(club.id)

        assertEquals(RenewalResult.Conflict(club.id), service.renew(club.id, player.id, ContractOffer(demand, 2)))
        coVerify(exactly = MAX_CLUB_WRITE_ATTEMPTS) { clubRepo.save(any()) }
    }
}
