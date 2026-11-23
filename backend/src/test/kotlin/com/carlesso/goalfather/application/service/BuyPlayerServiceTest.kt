package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.test.GENEROUS_TERMS
import com.carlesso.goalfather.domain.model.ContractOffer
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.rules.RefusalReason
import com.carlesso.goalfather.domain.rules.salaryDemand
import com.carlesso.goalfather.domain.rules.stature
import com.carlesso.goalfather.domain.rules.MARKET_REFERENCE_STRENGTH
import com.carlesso.goalfather.test.InMemoryCatalogRepository
import kotlin.test.assertNotNull
import java.time.LocalDate
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Slot
import com.carlesso.goalfather.domain.model.Matchday
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.cents
import com.carlesso.goalfather.domain.result.TransferResult
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BuyPlayerServiceTest {

    private val clubRepo: ClubRepository = mockk()
    private val marketRepo: MarketRepository = mockk()
    // Jogo em 10/03/2027, sem tabelas: a compra assina pela temporada da data.
    private val leagueRepo: LeagueRepository = mockk {
        coEvery { findLatest() } returns Round(Matchday.of(LocalDate.of(2027, 3, 10), Slot.MIDWEEK), season = 2027, matches = emptyList())
        coEvery { currentStandings() } returns emptyList()
    }
    private val service = BuyPlayerService(clubRepo, marketRepo, leagueRepo, InMemoryCatalogRepository(emptyList()), squadMaxSize = 25)

    @Test
    fun `compra bem-sucedida debita caixa, adiciona ao elenco e remove do mercado`() = runTest {
        val club = makeClub(cash = 1_000_000_00, squadSize = 11)
        val target = makePlayer(99, Position.FW, overall = 88)
        val entry = MarketEntry(player = target, price = 400_000_00.cents)

        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { marketRepo.findEntry(PlayerId(99)) } returns entry
        coEvery { marketRepo.claim(PlayerId(99)) } returns true
        val savedClub = slot<com.carlesso.goalfather.domain.model.Club>()
        coEvery { clubRepo.save(capture(savedClub)) } answers { ClubSaveResult.Saved(savedClub.captured) }

        // Termos que ele aceita (issue #138): o que pede, 3 temporadas, com cláusula.
        val terms = ContractOffer(Money.dollars(50_000), years = 3, releaseClause = Money.dollars(2_000_000))
        val result = service.execute(ClubId(1), PlayerId(99), terms)

        assertIs<TransferResult.Success>(result)
        assertEquals(600_000_00.cents, result.club.cash)
        assertEquals(12, result.club.squad.size)
        // Livre do mercado vira contratado com os termos NEGOCIADOS: contrato
        // com o comprador, 3 temporadas contando a corrente (2027), o salário e
        // a cláusula oferecidos.
        val contract = assertNotNull(result.player.contract)
        assertEquals(ClubId(1), contract.clubId)
        assertEquals(LocalDate.of(2027, 3, 10), contract.start)
        assertEquals(LocalDate.of(2029, 12, 31), contract.end)
        assertEquals(Money.dollars(50_000), contract.salary)
        assertEquals(Money.dollars(2_000_000), contract.releaseClause)
        assertEquals(target.id, result.player.id)
        assertEquals(result.player, result.club.squad.last())

        coVerify(exactly = 1) { clubRepo.save(any()) }
        coVerify(exactly = 1) { marketRepo.claim(PlayerId(99)) }
    }

    @Test
    fun `livre que recusa os termos devolve PlayerRefused e nao sai do mercado`() = runTest {
        val club = makeClub(cash = 1_000_000_00, squadSize = 11)
        val target = makePlayer(99, Position.FW, overall = 88, age = 26)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { marketRepo.findEntry(PlayerId(99)) } returns MarketEntry(player = target, price = 400_000_00.cents)

        val result = service.execute(ClubId(1), PlayerId(99), ContractOffer(Money.dollars(500), years = 3))

        val refused = assertIs<TransferResult.PlayerRefused>(result)
        assertEquals(RefusalReason.LOW_SALARY, refused.refusal.reason)
        // O pedido é o do domínio: livre não tem clube atual, então sem degrau.
        val expected = salaryDemand(target, club.stature(MARKET_REFERENCE_STRENGTH), current = null)
        assertEquals(expected, refused.refusal.demandedSalary)
        coVerify(exactly = 0) { marketRepo.claim(any()) }
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `claim perdido na corrida retorna PlayerNotAvailable e nao mexe no clube`() = runTest {
        // Lock otimista (issue #21): outro comprador venceu a corrida, então
        // `claim` devolve false. O perdedor não pode debitar caixa nem salvar.
        val club = makeClub(cash = 1_000_000_00, squadSize = 11)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { marketRepo.findEntry(PlayerId(99)) } returns
            MarketEntry(player = makePlayer(99), price = 400_000_00.cents)
        coEvery { marketRepo.claim(PlayerId(99)) } returns false

        val result = service.execute(ClubId(1), PlayerId(99), GENEROUS_TERMS)

        assertIs<TransferResult.PlayerNotAvailable>(result)
        assertEquals(PlayerId(99), result.playerId)
        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `caixa insuficiente retorna InsufficientFunds e nao persiste`() = runTest {
        val club = makeClub(cash = 100_000_00)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { marketRepo.findEntry(PlayerId(99)) } returns
            MarketEntry(player = makePlayer(99), price = 500_000_00.cents)

        val result = service.execute(ClubId(1), PlayerId(99), GENEROUS_TERMS)

        assertIs<TransferResult.InsufficientFunds>(result)
        assertEquals(100_000_00.cents, result.available)
        assertEquals(500_000_00.cents, result.required)

        coVerify(exactly = 0) { clubRepo.save(any()) }
        coVerify(exactly = 0) { marketRepo.claim(any()) }
    }

    @Test
    fun `elenco cheio retorna SquadFull`() = runTest {
        val club = makeClub(cash = 10_000_000_00, squadSize = 25)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { marketRepo.findEntry(PlayerId(99)) } returns
            MarketEntry(player = makePlayer(99), price = 100_00.cents)

        val result = service.execute(ClubId(1), PlayerId(99), GENEROUS_TERMS)

        assertIs<TransferResult.SquadFull>(result)
        assertEquals(25, result.maxSize)

        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `clube inexistente retorna PlayerNotAvailable`() = runTest {
        coEvery { clubRepo.findById(ClubId(999)) } returns null

        val result = service.execute(ClubId(999), PlayerId(1), GENEROUS_TERMS)

        assertIs<TransferResult.PlayerNotAvailable>(result)
        assertEquals(PlayerId(1), result.playerId)
    }

    @Test
    fun `jogador fora do mercado retorna PlayerNotAvailable`() = runTest {
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub()
        coEvery { marketRepo.findEntry(PlayerId(99)) } returns null

        val result = service.execute(ClubId(1), PlayerId(99), GENEROUS_TERMS)

        assertIs<TransferResult.PlayerNotAvailable>(result)
    }

    @Test
    fun `conflito no clube devolve o jogador ao mercado e volta como valor (issue 89)`() = runTest {
        // O claim já tirou o jogador do mercado; se o save do clube perde o lock
        // otimista, sem compensação o jogador sumiria (fora do mercado e de
        // qualquer elenco).
        val target = makePlayer(99, Position.FW, overall = 88)
        // Com tempo de pool (issue #139): a compensação não pode zerar o contador.
        val entry = MarketEntry(player = target, price = 400_000_00.cents, seasonsWithoutClub = 1)
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub(cash = 1_000_000_00)
        coEvery { marketRepo.findEntry(PlayerId(99)) } returns entry
        coEvery { marketRepo.claim(PlayerId(99)) } returns true
        coEvery { clubRepo.save(any()) } returns ClubSaveResult.Conflict(ClubId(1))
        coEvery { marketRepo.add(any()) } just Runs

        val result = service.execute(ClubId(1), PlayerId(99), GENEROUS_TERMS)

        assertIs<TransferResult.Conflict>(result)
        assertEquals(ClubId(1), result.clubId)
        coVerify(exactly = 1) { marketRepo.add(entry) }
    }
}
