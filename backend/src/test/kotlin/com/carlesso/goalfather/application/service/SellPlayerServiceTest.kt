package com.carlesso.goalfather.application.service

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.cents
import com.carlesso.goalfather.domain.result.TransferResult
import com.carlesso.goalfather.test.InMemoryCatalogRepository
import com.carlesso.goalfather.test.makeClub
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
import kotlin.test.assertTrue
import com.carlesso.goalfather.application.seed.MVP_START_YEAR
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.domain.rules.marketListingPrice
import com.carlesso.goalfather.domain.rules.marketReferenceValue
import com.carlesso.goalfather.domain.rules.salePayout
import com.carlesso.goalfather.domain.rules.squadMarketValue
import com.carlesso.goalfather.test.SERIE_B
import com.carlesso.goalfather.test.brazilCatalog
import java.time.LocalDate

class SellPlayerServiceTest {

    private val clubRepo: ClubRepository = mockk()
    private val marketRepo: MarketRepository = mockk()
    private val noRound: LeagueRepository = mockk { coEvery { findLatest() } returns null }
    private val service = SellPlayerService(
        clubRepo, marketRepo, InMemoryCatalogRepository(), noRound,
        salePriceCalc = { 200_000_00.cents }, // fixo para determinismo
        listingPriceCalc = { 300_000_00.cents },
    )

    @Test
    fun `venda bem-sucedida credita caixa, remove do elenco e listou no mercado`() = runTest {
        val club = makeClub(cash = 500_000_00, squadSize = 11)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { clubRepo.save(any()) } answers { ClubSaveResult.Saved(firstArg()) }
        coEvery { marketRepo.add(any()) } just Runs

        val targetId = club.squad.first().id
        val result = service.execute(ClubId(1), targetId)

        assertIs<TransferResult.Success>(result)
        assertEquals(700_000_00.cents, result.club.cash)
        assertEquals(10, result.club.squad.size)
        assertEquals(targetId, result.player.id)

        coVerify(exactly = 1) { clubRepo.save(any()) }
        coVerify(exactly = 1) { marketRepo.add(any()) }
    }

    @Test
    fun `jogador fora do elenco retorna PlayerNotAvailable`() = runTest {
        coEvery { clubRepo.findById(ClubId(1)) } returns makeClub()

        val result = service.execute(ClubId(1), PlayerId(999))

        assertIs<TransferResult.PlayerNotAvailable>(result)
        coVerify(exactly = 0) { clubRepo.save(any()) }
        coVerify(exactly = 0) { marketRepo.add(any()) }
    }

    @Test
    fun `clube inexistente retorna PlayerNotAvailable`() = runTest {
        coEvery { clubRepo.findById(ClubId(999)) } returns null

        val result = service.execute(ClubId(999), PlayerId(1))

        assertIs<TransferResult.PlayerNotAvailable>(result)
    }

    @Test
    fun `conflito no clube nao anuncia o jogador no mercado (issue 89)`() = runTest {
        // Clube é gravado ANTES do mercado: perdido o lock, o jogador continua
        // no elenco — anunciá-lo o deixaria em dois lugares.
        val club = makeClub(cash = 500_000_00, squadSize = 11)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { clubRepo.save(any()) } returns ClubSaveResult.Conflict(ClubId(1))

        val result = service.execute(ClubId(1), club.squad.first().id)

        assertIs<TransferResult.Conflict>(result)
        coVerify(exactly = 0) { marketRepo.add(any()) }
    }

    @Test
    fun `o jogador vendido e anunciado pelo preco de anuncio, acima do que o clube recebeu`() = runTest {
        // Recebe 200k e anuncia por 300k: quem recompra paga mais do que o
        // vendedor ganhou — sem arbitragem compra/venda (revisão do PR #92).
        val club = makeClub(cash = 500_000_00, squadSize = 11)
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { clubRepo.save(any()) } answers { ClubSaveResult.Saved(firstArg()) }
        val listed = slot<MarketEntry>()
        coEvery { marketRepo.add(capture(listed)) } just Runs

        val result = service.execute(ClubId(1), club.squad.first().id)

        assertIs<TransferResult.Success>(result)
        assertEquals(700_000_00.cents, result.club.cash)
        assertEquals(300_000_00.cents, listed.captured.price)
    }

    @Test
    fun `com as regras reais, recebe pelo valor no clube e anuncia pelo valor de referencia (issue 135)`() = runTest {
        // Clube da Série B com força 62: o pagamento sai do valor COM o deságio
        // da liga; o anúncio, da régua única do mercado. Quem recompra paga mais.
        val catalog = brazilCatalog().map { if (it.id == SERIE_B) it.copy(strength = LeagueStrength(62)) else it }
        val real = SellPlayerService(clubRepo, marketRepo, InMemoryCatalogRepository(catalog), noRound)
        val club = makeClub(cash = 0, squadSize = 11, division = Division(2))
        val player = club.squad.first()
        coEvery { clubRepo.findById(ClubId(1)) } returns club
        coEvery { clubRepo.save(any()) } answers { ClubSaveResult.Saved(firstArg()) }
        val listed = slot<MarketEntry>()
        coEvery { marketRepo.add(capture(listed)) } just Runs

        val result = real.execute(ClubId(1), player.id)

        assertIs<TransferResult.Success>(result)
        val today = LocalDate.of(MVP_START_YEAR, 1, 1)
        assertEquals(salePayout(squadMarketValue(player, club, catalog, today)), result.club.cash)
        assertEquals(marketListingPrice(marketReferenceValue(player)), listed.captured.price)
        assertTrue(listed.captured.price > result.club.cash)
    }
}
