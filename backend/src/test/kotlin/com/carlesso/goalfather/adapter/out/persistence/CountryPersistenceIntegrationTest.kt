package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Countries
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * País do clube e nacionalidade do jogador persistidos (issue #123) contra o H2
 * real, com a migration V16 aplicada, e os países do pack no save (issue #131, V19).
 */
@SpringBootTest
class CountryPersistenceIntegrationTest {

    @Autowired private lateinit var clubRepo: ClubRepository
    @Autowired private lateinit var marketRepo: MarketRepository

    @Test
    fun `mundo do pack nasce com clubes do Brasil e da Inglaterra e nacionalidades do pack`() = runBlocking {
        val clubs = clubRepo.findAll()
        assertEquals(setOf("BRA", "ENG"), clubs.map { it.country.code.value }.toSet())

        // Países fora do catálogo fixo do domínio (issue #131) voltam do banco
        // como o país do pack, e não como o default (Brasil).
        val known = Countries.all.map { it.code }.toSet()
        val fromPack = clubs.flatMap { it.squad }.map { it.nationality }.filter { it.code !in known }
        assertTrue(fromPack.isNotEmpty(), "o pack do E2E deveria ter nacionalidades fora do catálogo fixo")
        assertTrue(fromPack.all { it.name.isNotBlank() && it != Countries.DEFAULT })
        // Os ingleses dos clubes ingleses não são estrangeiros por engano.
        val english = clubs.filter { it.country.code.value == "ENG" }.flatMap { it.squad }
        assertTrue(english.any { it.nationality.code.value == "ENG" })
    }

    @Test
    fun `pais e nacionalidade atravessam o save e a releitura`() = runBlocking {
        // Clube da Série B (ids 7..12 no pack do E2E), fora do caminho dos testes de claim.
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
