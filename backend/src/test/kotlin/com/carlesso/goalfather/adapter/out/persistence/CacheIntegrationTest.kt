package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.application.port.out.LeagueRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.cache.interceptor.SimpleKey
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifica o cache (issue #13) pelo comportamento — popular após leitura,
 * invalidar após mutação — em vez de cronometrar a expiração (que é
 * responsabilidade da spec do Caffeine, não da nossa lógica). Roda contra o
 * `CacheManager` real do contexto Spring.
 */
@SpringBootTest
class CacheIntegrationTest {

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Autowired
    private lateinit var leagueRepo: LeagueRepository

    @Autowired
    private lateinit var marketRepo: MarketRepository

    private fun cachedEntry(name: String) =
        cacheManager.getCache(name)?.get(SimpleKey.EMPTY)?.get()

    @Test
    fun `standings e cacheada na leitura e invalidada ao salvar`() = runBlocking {
        cacheManager.getCache("standings")?.clear()

        val standings = leagueRepo.currentStandings()
        assertNotNull(cachedEntry("standings"), "primeira leitura deve popular o cache")

        // Uma mutação na tabela precisa invalidar (senão serviríamos dados velhos).
        leagueRepo.saveStandings(standings.copy(round = standings.round + 1))
        assertNull(cachedEntry("standings"), "saveStandings deve invalidar o cache")
    }

    @Test
    fun `market e cacheado na leitura e invalidado ao adicionar`() = runBlocking {
        cacheManager.getCache("market")?.clear()

        marketRepo.findAll()
        assertNotNull(cachedEntry("market"), "primeira leitura deve popular o cache")

        marketRepo.add(
            MarketEntry(
                player = Player(
                    id = PlayerId(9001), name = "Cache Tester", position = Position.MF,
                    overall = 70, pace = 70, shooting = 70, passing = 70, defending = 70,
                    salary = 10_000_00, age = 25,
                ),
                price = 100_000_00,
            ),
        )
        assertNull(cachedEntry("market"), "add deve invalidar o cache")
    }
}
