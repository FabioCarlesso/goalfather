package com.carlesso.goalfather.config

import com.carlesso.goalfather.adapter.out.persistence.SavedCountryCatalog
import com.carlesso.goalfather.adapter.out.persistence.TransferWindowEntity
import com.carlesso.goalfather.adapter.out.persistence.TransferWindowJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.ClubJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.CompetitionCatalogJpaRepository
import com.carlesso.goalfather.application.port.out.PackLoadError
import com.carlesso.goalfather.application.port.out.PackLoadResult
import com.carlesso.goalfather.application.port.out.WorldDataProvider
import com.carlesso.goalfather.application.seed.InitialWorld
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.world.TransferWindow
import com.carlesso.goalfather.domain.model.world.WorldPack
import com.carlesso.goalfather.domain.rules.WorldStart
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.time.LocalDate

/**
 * Quando o boot grava o mundo (issue #131). Sem Spring: repositórios, seed e
 * fábrica do mundo são mocks do MockK — o que interessa aqui é a decisão
 * (banco vazio, mundo existente, save antigo), não a gravação.
 */
class DataInitializerTest {

    private val clubRepo = mockk<ClubJpaRepository>()
    private val catalogRepo = mockk<CompetitionCatalogJpaRepository>()
    private val newWorld = mockk<NewWorldFactory>()
    private val seed = mockk<SeedWriter>(relaxed = true)
    private val countries = mockk<SavedCountryCatalog>(relaxed = true)
    private val transferWindowRepo = mockk<TransferWindowJpaRepository>(relaxed = true)
    private val initializer = DataInitializer(clubRepo, catalogRepo, newWorld, seed, countries, transferWindowRepo)

    private val world = InitialWorld(year = 2026, countries = emptyList(), competitions = emptyList(), clubs = emptyList(), market = emptyList())
    private val start = WorldStart(firstRound = null, standings = emptyList(), cups = emptyList())

    @Test
    fun `banco vazio grava o mundo montado do pack e carrega os paises do save`() {
        every { clubRepo.count() } returns 0
        every { newWorld.create() } returns NewWorld(world, start)

        initializer.run()

        // `verifyOrder`: os países só são recarregados DEPOIS da gravação.
        verifyOrder {
            seed.write(world, start)
            countries.reload()
        }
    }

    @Test
    fun `mundo existente nao e regravado`() {
        every { clubRepo.count() } returns 136
        every { catalogRepo.count() } returns 8
        every { transferWindowRepo.count() } returns 30

        initializer.run()

        verify(exactly = 0) { seed.write(any(), any()) }
        verify(exactly = 0) { newWorld.create() }
        verify { countries.reload() }
    }

    @Test
    fun `save anterior as janelas de transferencia ganha as do pack sem regravar o mundo`() {
        val window = TransferWindow(Countries.BRAZIL, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 3, 3))
        every { clubRepo.count() } returns 136
        every { catalogRepo.count() } returns 8
        every { transferWindowRepo.count() } returns 0
        every { newWorld.create() } returns NewWorld(world.copy(transferWindows = listOf(window)), start)

        initializer.run()

        verify(exactly = 0) { seed.write(any(), any()) }
        verify { transferWindowRepo.saveAll(match<List<TransferWindowEntity>> { it.single().startDate == window.start }) }
    }

    @Test
    fun `save antigo sem catalogo derruba o boot dizendo como zerar o banco`() {
        every { clubRepo.count() } returns 12
        every { catalogRepo.count() } returns 0

        val error = assertFailsWith<IllegalStateException> { initializer.run() }

        assertTrue("rm -rf backend/data/" in error.message.orEmpty(), error.message)
        verify(exactly = 0) { seed.write(any(), any()) }
    }
}

/** A fábrica do mundo: pack inválido derruba o boot listando os erros; o mundo fica em cache. */
class NewWorldFactoryTest {

    private val provider = mockk<WorldDataProvider>()
    private val factory = NewWorldFactory(provider, WorldDataProperties(path = "/pack/de/teste"))

    @Test
    fun `pack invalido vira excecao com todos os erros`() {
        coEvery { provider.load() } returns PackLoadResult.Invalid(
            listOf(PackLoadError.MissingResource("countries.json"), PackLoadError.MissingResource("competitions.json")),
        )

        val error = assertFailsWith<IllegalStateException> { factory.create() }

        val message = error.message.orEmpty()
        assertTrue("/pack/de/teste" in message, message)
        assertTrue("countries.json" in message && "competitions.json" in message, message)
    }

    @Test
    fun `o mundo e montado uma vez so e reaproveitado (reset do e2e)`() {
        val pack = WorldPack(emptyList(), emptyList(), emptyList(), emptyList())
        coEvery { provider.load() } returns PackLoadResult.Loaded(pack)

        val first = factory.create()
        val second = factory.create()

        assertEquals(first, second)
        coVerify(exactly = 1) { provider.load() }
    }
}
