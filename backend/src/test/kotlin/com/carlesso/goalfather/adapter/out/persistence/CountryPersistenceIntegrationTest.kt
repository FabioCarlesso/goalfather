package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.PlayerId
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * País do clube e nacionalidade do jogador persistidos (issue #123) contra o H2
 * real, com a migration V16 aplicada.
 */
@SpringBootTest
class CountryPersistenceIntegrationTest {

    @Autowired private lateinit var clubRepo: ClubRepository
    @Autowired private lateinit var marketRepo: MarketRepository

    @Test
    fun `seed nasce com clubes brasileiros e estrangeiros no mercado`() = runBlocking {
        assertEquals(setOf(Countries.BRAZIL), clubRepo.findAll().map { it.country }.toSet())

        val argentine = assertNotNull(marketRepo.findEntry(PlayerId(103))).player
        assertEquals(Countries.ARGENTINA, argentine.nationality)
        assertEquals(Countries.ITALY, argentine.secondNationality)
    }

    @Test
    fun `pais e nacionalidade atravessam o save e a releitura`() = runBlocking {
        // Clube da 2ª divisão (ids 7..12), fora do caminho dos testes de claim.
        val club = assertNotNull(clubRepo.findById(ClubId(11)))
        val foreigner = club.squad.first().copy(nationality = Countries.URUGUAY, secondNationality = Countries.SPAIN)
        val changed = club.copy(country = Countries.PORTUGAL, squad = listOf(foreigner) + club.squad.drop(1))

        assertIs<ClubSaveResult.Saved>(clubRepo.save(changed))

        val reloaded = assertNotNull(clubRepo.findById(ClubId(11)))
        assertEquals(Countries.PORTUGAL, reloaded.country)
        val player = reloaded.squad.first { it.id == foreigner.id }
        assertEquals(Countries.URUGUAY, player.nationality)
        assertEquals(Countries.SPAIN, player.secondNationality)

        // Devolve o estado original para não vazar para outras suítes.
        assertIs<ClubSaveResult.Saved>(clubRepo.save(club.copy(version = reloaded.version)))
    }
}
