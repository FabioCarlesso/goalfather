package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.domain.rules.salaryForValue
import com.carlesso.goalfather.domain.rules.marketValue
import com.carlesso.goalfather.domain.rules.NEUTRAL_LEAGUE_STRENGTH
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
    private val service = BuyPlayerService(clubRepo, marketRepo, leagueRepo, squadMaxSize = 25)

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

        val result = service.execute(ClubId(1), PlayerId(99))

        assertIs<TransferResult.Success>(result)
        assertEquals(600_000_00.cents, result.club.cash)
        assertEquals(12, result.club.squad.size)
        // Livre do mercado vira contratado (issue #136): contrato com o
        // comprador, 3 temporadas contando a corrente (2027) e salário do valor.
        val contract = assertNotNull(result.player.contract)
        assertEquals(ClubId(1), contract.clubId)
        assertEquals(LocalDate.of(2027, 3, 10), contract.start)
        assertEquals(LocalDate.of(2029, 12, 31), contract.end)
        assertEquals(salaryForValue(marketValue(target, contract, NEUTRAL_LEAGUE_STRENGTH, contract.start)), contract.salary)
        assertEquals(target.id, result.player.id)
        assertEquals(result.player, result.club.squad.last())

        coVerify(exactly = 1) { clubRepo.save(any()) }
        coVerify(exactly = 1) { marketRepo.claim(PlayerId(99)) }
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

        val result = service.execute(ClubId(1), PlayerId(99))

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

        val result = service.execute(ClubId(1), PlayerId(99))

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

        val result = service.execute(ClubId(1), PlayerId(99))

        assertIs<TransferResult.SquadFull>(result)
        assertEquals(25, result.maxSize)

        coVerify(exactly = 0) { clubRepo.save(any()) }
    }

    @Test
    fun `clube inexistente retorna PlayerNotAvailable`() = runTest {
        coEvery { clubRepo.findById(ClubId(999)) } returns null

        val result = service.execute(ClubId(999), PlayerId(1))

        assertIs<TransferResult.PlayerNotAvailable>(result)
        assertEquals(PlayerId(1), result.playerId)
    }

    @Test
    fun `jogador fora do mercado retorna PlayerNotAvailable`() = runTest {
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub()
        coEvery { marketRepo.findEntry(PlayerId(99)) } returns null

        val result = service.execute(ClubId(1), PlayerId(99))

        assertIs<TransferResult.PlayerNotAvailable>(result)
    }

    @Test
    fun `conflito no clube devolve o jogador ao mercado e volta como valor (issue 89)`() = runTest {
        // O claim já tirou o jogador do mercado; se o save do clube perde o lock
        // otimista, sem compensação o jogador sumiria (fora do mercado e de
        // qualquer elenco).
        val target = makePlayer(99, Position.FW, overall = 88)
        val entry = MarketEntry(player = target, price = 400_000_00.cents)
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub(cash = 1_000_000_00)
        coEvery { marketRepo.findEntry(PlayerId(99)) } returns entry
        coEvery { marketRepo.claim(PlayerId(99)) } returns true
        coEvery { clubRepo.save(any()) } returns ClubSaveResult.Conflict(ClubId(1))
        coEvery { marketRepo.add(any()) } just Runs

        val result = service.execute(ClubId(1), PlayerId(99))

        assertIs<TransferResult.Conflict>(result)
        assertEquals(ClubId(1), result.clubId)
        coVerify(exactly = 1) { marketRepo.add(entry) }
    }
}
