package com.carlesso.goalfather.adapter.out.persistence

import com.carlesso.goalfather.adapter.out.persistence.entity.MarketEntryEntity
import com.carlesso.goalfather.adapter.out.persistence.mapper.toDomain
import com.carlesso.goalfather.adapter.out.persistence.repository.MarketEntryJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.PlayerJpaRepository
import com.carlesso.goalfather.application.port.out.MarketRepository
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Money
import com.carlesso.goalfather.domain.model.PlayerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class MarketPersistenceAdapter(
    private val marketRepo: MarketEntryJpaRepository,
    private val playerRepo: PlayerJpaRepository,
    private val claimTx: MarketClaimTransaction,
) : MarketRepository {

    /**
     * O pool inteiro em duas queries (issue #139): as entradas (cacheadas) e os
     * jogadores delas num `findAllById` só. Antes era um `findById` por
     * entrada — N+1 que não importava com 16 livres, mas importa com o pool de
     * centenas que a base e os vencimentos criam.
     */
    override suspend fun findAll(): List<MarketEntry> =
        withContext(Dispatchers.IO) {
            val entries = marketRepo.findAll()
            val players = playerRepo.findAllById(entries.map { it.playerId }).associateBy { it.id }
            entries.mapNotNull { entry ->
                players[entry.playerId]?.let { player ->
                    MarketEntry(
                        player = player.toDomain(),
                        price = Money(entry.price),
                        seasonsWithoutClub = entry.seasonsWithoutClub,
                    )
                }
            }
        }

    override suspend fun findEntry(playerId: PlayerId): MarketEntry? = withContext(Dispatchers.IO) {
        val entry = marketRepo.findById(playerId.value).orElse(null) ?: return@withContext null
        val player = playerRepo.findById(playerId.value).orElse(null) ?: return@withContext null
        MarketEntry(player = player.toDomain(), price = Money(entry.price), seasonsWithoutClub = entry.seasonsWithoutClub)
    }

    @Transactional
    override suspend fun add(entry: MarketEntry) {
        withContext(Dispatchers.IO) {
            // Player ja deve existir; só libera de clube (clubId = null) e
            // cria a entry de mercado.
            val player = playerRepo.findById(entry.player.id.value).orElse(null)
            if (player != null) {
                player.releaseFromClub()
                playerRepo.save(player)
            }
            marketRepo.save(
                MarketEntryEntity(
                    playerId = entry.player.id.value,
                    price = entry.price.cents,
                    seasonsWithoutClub = entry.seasonsWithoutClub,
                ),
            )
        }
    }

    @Transactional
    override suspend fun updateAll(entries: List<MarketEntry>) {
        withContext(Dispatchers.IO) {
            val byId = entries.associateBy { it.player.id.value }
            // Carrega as linhas gerenciadas (com a versão) e só então altera:
            // quem saiu do mercado no meio do caminho não volta por aqui.
            val rows = marketRepo.findAllById(byId.keys).onEach { row ->
                val entry = byId.getValue(row.playerId)
                row.price = entry.price.cents
                row.seasonsWithoutClub = entry.seasonsWithoutClub
            }
            marketRepo.saveAll(rows)
        }
    }

    /**
     * Compra concorrente resolvida por lock otimista (issue #21). A unidade
     * transacional roda em [MarketClaimTransaction] (método **não-suspend**):
     * `@Transactional` sobre função `suspend` é frágil — o `withContext` troca
     * de thread e a transação é thread-bound. Manter a transação síncrona faz
     * o lock disparar de verdade no commit (mesmo padrão do claim de clube).
     *
     * A falha de lock ([OptimisticLockingFailureException], lançada no commit)
     * é capturada e vira `false` — o perdedor da corrida recebe
     * `PlayerNotAvailable` no use case, sem mexer no clube.
     */
    override suspend fun claim(playerId: PlayerId): Boolean = withContext(Dispatchers.IO) {
        try {
            claimTx.claim(playerId.value)
        } catch (_: OptimisticLockingFailureException) {
            false
        }
    }
}

/**
 * Transação síncrona do claim de mercado (issue #21). Bean separado para que
 * `@Transactional` passe pelo proxy do Spring — self-invocation não passaria.
 */
@Component
class MarketClaimTransaction(
    private val marketRepo: MarketEntryJpaRepository,
) {
    @Transactional
    fun claim(playerId: Long): Boolean {
        // A linha já carregada é apagada com checagem de versão; se outro
        // comprador já venceu (linha sumiu), `findById` devolve vazio → false.
        val entry = marketRepo.findById(playerId).orElse(null) ?: return false
        marketRepo.delete(entry) // @Version: conflito vira OptimisticLock... no commit
        return true
    }
}
