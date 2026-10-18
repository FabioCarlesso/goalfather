package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.repository.ClubJpaRepository
import com.carlesso.goalfather.application.port.out.ClubRepository
import com.carlesso.goalfather.application.port.out.ClubSaveResult
import com.carlesso.goalfather.application.service.ExpandStadiumService
import com.carlesso.goalfather.application.service.SetTicketPriceService
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.result.ExpandStadiumResult
import com.carlesso.goalfather.domain.result.TicketPriceResult
import com.carlesso.goalfather.domain.rules.stadiumExpansionCost
import com.carlesso.goalfather.test.makePlayer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Lock otimista do clube (issue #89) contra o H2 real. Antes, o `save`
 * recarregava a entidade e copiava o domínio por cima — o `@Version` comparava
 * contra a versão recém-lida, e uma escrita intercalada sumia em silêncio
 * (*lost update*). Cada teste usa um id de clube próprio (faixa 960_xxx), sem
 * colidir com o seed nem com os outros testes que compartilham o contexto.
 */
@SpringBootTest
class ClubOptimisticLockIntegrationTest {

    @Autowired private lateinit var clubRepo: ClubRepository

    @Autowired private lateinit var clubJpa: ClubJpaRepository

    private val owner = 4_242L

    private fun newClub(id: Long): Club = runBlocking {
        val saved = clubRepo.save(
            Club(
                id = ClubId(id),
                name = "Lock FC $id",
                cash = 1_000_000_00,
                stadiumCapacity = 10_000,
                squad = listOf(makePlayer(id * 10 + 1), makePlayer(id * 10 + 2)),
                ownerId = owner,
            ),
        )
        assertIs<ClubSaveResult.Saved>(saved).club
    }

    @Test
    fun `segunda gravacao a partir da MESMA leitura recebe Conflict e nao apaga a primeira`() = runBlocking {
        // Reproduz o cenário da issue de forma determinística: ampliação e
        // virada da rodada leem o clube na mesma versão; a rodada comita a
        // bilheteria; a ampliação, ainda com a leitura antiga, NÃO pode gravar.
        newClub(960_001)
        val readByBoth = clubRepo.findById(ClubId(960_001))!!

        val round = clubRepo.save(readByBoth.copy(cash = readByBoth.cash + 50_000_00))
        val expansion = clubRepo.save(
            readByBoth.copy(cash = readByBoth.cash - 100_000_00, stadiumCapacity = readByBoth.stadiumCapacity + 1_000),
        )

        assertIs<ClubSaveResult.Saved>(round)
        assertIs<ClubSaveResult.Conflict>(expansion)
        val fresh = clubRepo.findById(ClubId(960_001))!!
        assertEquals(readByBoth.cash + 50_000_00, fresh.cash, "o crédito da rodada sobrevive")
        assertEquals(readByBoth.stadiumCapacity, fresh.stadiumCapacity, "a gravação perdedora não aplicou nada")
    }

    @Test
    fun `conflito tambem vale para escrita que so mexe no elenco`() = runBlocking {
        // Desgaste/treino da rodada só tocam o elenco — a linha do clube não
        // muda. Sem o bump explícito da versão, duas escritas assim não se
        // enxergariam.
        newClub(960_002)
        val readByBoth = clubRepo.findById(ClubId(960_002))!!
        val tired = readByBoth.squad.map { it.copy(stamina = 60) }
        val treated = readByBoth.squad.map { it.copy(stamina = 100) }

        assertIs<ClubSaveResult.Saved>(clubRepo.save(readByBoth.copy(squad = tired)))
        assertIs<ClubSaveResult.Conflict>(clubRepo.save(readByBoth.copy(squad = treated)))

        assertTrue(clubRepo.findById(ClubId(960_002))!!.squad.all { it.stamina == 60 })
    }

    @Test
    fun `caminho normal - gravacoes encadeadas e releituras nunca conflitam`() = runBlocking {
        // Critério de aceite: sem concorrência, nada de StaleObjectStateException
        // nem de conflito espúrio — nem gravando a partir do clube devolvido
        // pelo `save`, nem a partir de uma releitura.
        val created = newClub(960_003)

        val first = assertIs<ClubSaveResult.Saved>(clubRepo.save(created.copy(cash = created.cash + 1))).club
        val second = assertIs<ClubSaveResult.Saved>(clubRepo.save(first.copy(cash = first.cash + 1))).club
        val reread = clubRepo.findById(ClubId(960_003))!!
        assertEquals(second.version, reread.version, "a versão devolvida pelo save é a que ficou no banco")
        assertIs<ClubSaveResult.Saved>(clubRepo.save(reread.copy(cash = reread.cash + 1)))

        assertEquals(created.cash + 3, clubRepo.findById(ClubId(960_003))!!.cash)
        assertEquals(clubJpa.findById(960_003).orElseThrow().version, clubRepo.findById(ClubId(960_003))!!.version)
    }

    @Test
    fun `duas abas ampliando o estadio - a que grava depois de a outra comitar recebe Conflict`() {
        // O cenário EXATO da issue, com threads reais: as duas leem a mesma
        // versão (barreira depois da leitura) e as gravações saem em fila — a
        // segunda só começa depois que a primeira COMITOU. Era aqui que o
        // adapter antigo perdia: ao gravar, ele relia a versão já nova, o
        // `@Version` batia e as duas "venciam" — o banco ficava com UMA
        // ampliação e o débito de duas sobrescrito em silêncio.
        expansionRace(clubId = 960_004, serializeSaves = true)
    }

    @Test
    fun `duas abas ampliando o estadio com gravacoes sobrepostas - uma vence, a outra recebe Conflict`() {
        // Mesma corrida, mas as duas transações de `save` correm juntas: aqui é
        // o lock de linha do compare-and-set que serializa — a segunda espera a
        // primeira comitar e encontra a versão nova.
        expansionRace(clubId = 960_006, serializeSaves = false)
    }

    private fun expansionRace(clubId: Long, serializeSaves: Boolean) {
        val initial = newClub(clubId)
        val repo = ReadBarrierClubRepository(clubRepo, CyclicBarrier(2), serializeSaves)
        val service = ExpandStadiumService(repo)

        val results = runConcurrently(2) { service.execute(initial.id, owner, 1_000) }

        assertEquals(1, results.count { it is ExpandStadiumResult.Success }, "exatamente uma ampliação vence: $results")
        assertEquals(1, results.count { it is ExpandStadiumResult.Conflict }, "a outra recebe conflito como valor")
        val fresh = runBlocking { clubRepo.findById(initial.id)!! }
        assertEquals(initial.stadiumCapacity + 1_000, fresh.stadiumCapacity)
        assertEquals(initial.cash - stadiumExpansionCost(1_000), fresh.cash)
    }

    @Test
    fun `preco do ingresso concorrente com ampliacao - o preco sempre entra e nada se perde`() {
        // Preço é UPDATE atômico de coluna: nunca conflita. Ampliação é
        // read-modify-write: se o preço mudou o clube depois da leitura dela,
        // devolve Conflict. Qualquer que seja a ordem, o estado final tem de
        // bater com os desfechos — nada some.
        val initial = newClub(960_005)
        val repo = ReadBarrierClubRepository(clubRepo, CyclicBarrier(2), serializeSaves = true)
        val expand = ExpandStadiumService(repo)
        val ticket = SetTicketPriceService(repo)

        val pool = Executors.newFixedThreadPool(2)
        try {
            val expansion = pool.submit(Callable { runBlocking { expand.execute(initial.id, owner, 1_000) } })
            val price = pool.submit(Callable { runBlocking { ticket.execute(initial.id, owner, 80_00) } })
            val expansionResult = expansion.get(10, TimeUnit.SECONDS)
            val priceResult = price.get(10, TimeUnit.SECONDS)

            assertIs<TicketPriceResult.Success>(priceResult, "o preço sempre entra (retry)")
            val fresh = runBlocking { clubRepo.findById(initial.id)!! }
            assertEquals(80_00, fresh.ticketPriceCents)
            when (expansionResult) {
                is ExpandStadiumResult.Success -> {
                    assertEquals(initial.stadiumCapacity + 1_000, fresh.stadiumCapacity)
                    assertEquals(initial.cash - stadiumExpansionCost(1_000), fresh.cash)
                }
                is ExpandStadiumResult.Conflict -> {
                    assertEquals(initial.stadiumCapacity, fresh.stadiumCapacity)
                    assertEquals(initial.cash, fresh.cash)
                }
                else -> error("desfecho inesperado: $expansionResult")
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `preco gravado por UPDATE de coluna sobe a versao e nunca conflita`() {
        // Revisão do PR #92: sob martelada contínua, o preço (read-modify-write
        // + retry) ainda esgotava as tentativas. Como UPDATE de uma coluna, 8
        // threads gravando ao mesmo tempo nunca conflitam — e uma leitura feita
        // antes delas fica velha: o `save` dela recebe Conflict.
        val initial = newClub(960_007)
        val stale = runBlocking { clubRepo.findById(initial.id)!! }

        val prices = runConcurrently(8) { clubRepo.updateTicketPrice(initial.id, 70_00L) }

        assertTrue(prices.all { it?.ticketPriceCents == 70_00L }, "toda gravação de preço entra")
        val fresh = runBlocking { clubRepo.findById(initial.id)!! }
        assertEquals(stale.version + 8, fresh.version, "cada UPDATE de coluna sobe a versão")
        assertEquals(initial.cash, fresh.cash, "o preço não toca o caixa")
        assertIs<ClubSaveResult.Conflict>(runBlocking { clubRepo.save(stale.copy(cash = 1)) })
    }

    /** Roda [block] em [threads] threads reais e devolve os desfechos. */
    private fun <R> runConcurrently(threads: Int, block: suspend () -> R): List<R> {
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val futures = List(threads) { pool.submit(Callable { runBlocking { block() } }) }
            return futures.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}

/**
 * Decorator de teste: segura as DUAS primeiras leituras numa barreira, o que
 * força as duas threads a lerem a mesma versão antes de qualquer gravação. As
 * leituras seguintes (o retry relendo o clube) passam direto — senão o retry
 * esperaria por uma parte que nunca chega.
 *
 * Com [serializeSaves], as gravações passam uma de cada vez por um `Mutex` de
 * coroutines: a segunda só começa depois que a primeira comitou. É o que
 * reproduz o *lost update* da issue de forma determinística.
 *
 * `ClubRepository by delegate` é *class delegation* do Kotlin: o compilador
 * gera os métodos repassando para [delegate], e só sobrescrevemos o que muda.
 * Em Java isso seria um proxy ou uma classe com todos os métodos à mão.
 */
private class ReadBarrierClubRepository(
    private val delegate: ClubRepository,
    private val barrier: CyclicBarrier,
    private val serializeSaves: Boolean,
) : ClubRepository by delegate {
    private val reads = AtomicInteger()
    private val saveLock = Mutex()

    override suspend fun save(club: Club): ClubSaveResult =
        if (serializeSaves) saveLock.withLock { delegate.save(club) } else delegate.save(club)

    override suspend fun findById(id: ClubId): Club? {
        val club = delegate.findById(id)
        if (reads.incrementAndGet() <= barrier.parties) barrier.await(10, TimeUnit.SECONDS)
        return club
    }
}
