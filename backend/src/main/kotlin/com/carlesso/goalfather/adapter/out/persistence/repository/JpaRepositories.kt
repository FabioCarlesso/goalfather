package com.carlesso.goalfather.adapter.out.persistence.repository

import com.carlesso.goalfather.adapter.out.persistence.entity.ClubEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.CupEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.MarketEntryEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PlayerEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.PositionEnum
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundReadinessEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundReadinessId
import com.carlesso.goalfather.adapter.out.persistence.entity.SeasonHistoryEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.StandingsEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.StandingsId
import com.carlesso.goalfather.adapter.out.persistence.entity.TransferOfferEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.UserEntity
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * Repositórios Spring Data — interfaces declarativas. Implementação
 * gerada em runtime pelo Spring Data JPA. Nenhum método suspend aqui
 * (Spring Data não suporta diretamente); a ponte para coroutines fica
 * nos PersistenceAdapter via `withContext(Dispatchers.IO)`.
 */
interface ClubJpaRepository : JpaRepository<ClubEntity, Long> {
    /** Clubes ainda sem dono — base do fluxo de seleção (issue #19). */
    fun findAllByOwnerIdIsNull(): List<ClubEntity>

    /**
     * Compare-and-set da versão do clube (issue #89): incrementa só se o banco
     * ainda está em [expected]. Devolve as linhas afetadas — `0` = alguém
     * gravou depois da leitura (ou o clube não existe).
     *
     * Por que um UPDATE explícito em vez de confiar só no `@Version`: o
     * Hibernate só incrementa a versão quando a LINHA do clube muda, e muitos
     * comandos mexem apenas no elenco (desgaste, treino). Sem o bump, duas
     * escritas só-de-elenco não se enxergariam. O UPDATE também segura o lock
     * de linha até o commit, então um concorrente que leu a mesma versão
     * espera e, ao seguir, encontra a versão nova → 0 linhas → conflito.
     *
     * `clearAutomatically`: o UPDATE em massa passa por fora do contexto de
     * persistência; limpar garante que o `findById` seguinte releia a versão
     * nova em vez de uma instância cacheada com a antiga.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ClubEntity c set c.version = c.version + 1 where c.id = :id and c.version = :expected")
    fun compareAndIncrementVersion(@Param("id") id: Long, @Param("expected") expected: Long): Int

    /**
     * Ajustes de UMA coluna (preço do ingresso, foco de treino): UPDATE atômico
     * que não depende de leitura prévia, então não há o que conflitar. Também
     * incrementa a versão — quem leu o clube antes e grava via `save` precisa
     * enxergar que ele mudou. Devolve as linhas afetadas (`0` = não existe).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ClubEntity c set c.ticketPriceCents = :price, c.version = c.version + 1 where c.id = :id")
    fun updateTicketPrice(@Param("id") id: Long, @Param("price") price: Long): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ClubEntity c set c.trainingFocus = :focus, c.version = c.version + 1 where c.id = :id")
    fun updateTrainingFocus(@Param("id") id: Long, @Param("focus") focus: String): Int

    /**
     * Devolve o clube à IA (issue #62) — a direção inversa do claim da issue
     * #19. Compare-and-set pelo DONO, não pela versão: só libera se o clube
     * ainda é de [ownerId], e incrementa a versão para que um `save` baseado
     * numa leitura anterior (com o dono antigo) receba `Conflict` em vez de
     * devolver o clube ao técnico demitido. `0` = já não era dele.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update ClubEntity c set c.ownerId = null, c.version = c.version + 1 " +
            "where c.id = :id and c.ownerId = :ownerId",
    )
    fun releaseOwner(@Param("id") id: Long, @Param("ownerId") ownerId: Long): Int
}

interface UserJpaRepository : JpaRepository<UserEntity, Long> {
    fun findByUsername(username: String): UserEntity?
    fun existsByUsername(username: String): Boolean

    /**
     * Donos de clube = técnicos humanos da liga (issue #20). `OrderById`
     * garante ordem determinística — `pendingUsernames` no 409/UI fica estável
     * entre chamadas (sem `ORDER BY` a ordem das linhas é indefinida).
     */
    fun findAllByClubIdIsNotNullOrderById(): List<UserEntity>
}

interface PlayerJpaRepository : JpaRepository<PlayerEntity, Long> {
    fun findAllByClubId(clubId: Long): List<PlayerEntity>
    fun findAllByClubIdIsNullAndPosition(position: PositionEnum): List<PlayerEntity>
    fun findAllByClubIdIsNull(): List<PlayerEntity>

    /** Busca o elenco de vários clubes numa só query — evita o N+1 do `findAll`. */
    fun findAllByClubIdIn(clubIds: Collection<Long>): List<PlayerEntity>
}

/**
 * O cache (issue #13) é aplicado na camada SÍNCRONA do Spring Data, não nos
 * adapters suspend: o interceptor de cache do Spring não suporta funções
 * `suspend` (lida com o parâmetro `Continuation`). Anotar o método bloqueante
 * do repositório resolve sem gambiarra — o adapter só o envelopa em
 * `withContext(Dispatchers.IO)`. `save`/`deleteById` são sobrescritos só para
 * pendurar o `@CacheEvict`.
 */
interface MarketEntryJpaRepository : JpaRepository<MarketEntryEntity, Long> {
    @Cacheable("market")
    override fun findAll(): List<MarketEntryEntity>

    @CacheEvict("market", allEntries = true)
    override fun <S : MarketEntryEntity> save(entity: S): S

    @CacheEvict("market", allEntries = true)
    override fun deleteById(id: Long)

    /**
     * Delete de uma entidade JÁ carregada — usado no claim (issue #21). Ao
     * contrário de `deleteById` (que faz `findById` interno e vira no-op se a
     * linha sumiu), apagar a instância gerenciada faz o Hibernate emitir
     * `DELETE ... WHERE player_id = ? AND version = ?`; se a linha já foi
     * removida por outro comprador, 0 linhas afetadas → `OptimisticLock...`.
     * Mesmo `@CacheEvict` do `deleteById` para não deixar mercado obsoleto.
     */
    @CacheEvict("market", allEntries = true)
    override fun delete(entity: MarketEntryEntity)
}

/** Copa por temporada (issue #61). */
interface CupJpaRepository : JpaRepository<CupEntity, Int> {
    /** Copa da temporada mais recente — a que está em disputa. */
    fun findTopByOrderBySeasonDesc(): CupEntity?
}

interface RoundJpaRepository : JpaRepository<RoundEntity, Int> {
    /**
     * Data corrente do calendário: a maior `number` da temporada MAIS RECENTE.
     * Ordenar só por `number` estava errado (review do PR #78): a PK é só
     * `number` e a virada reescreve apenas a linha 1, então as datas 2..N da
     * temporada encerrada continuam na tabela — e a "atual" virava a última
     * delas, já `Finished`, travando a liga depois da 1ª temporada.
     */
    fun findTopByOrderBySeasonDescNumberDesc(): RoundEntity?
}

interface RoundReadinessJpaRepository : JpaRepository<RoundReadinessEntity, RoundReadinessId> {
    fun findAllByIdRoundNumber(roundNumber: Int): List<RoundReadinessEntity>

    /**
     * Menor `ready_at` da rodada = instante do primeiro "pronto" (issue #45).
     * `MIN` no banco evita carregar todas as linhas só para achar o mínimo;
     * devolve `null` quando não há nenhuma marcação.
     */
    @Query(
        "select min(r.readyAt) from RoundReadinessEntity r where r.id.roundNumber = :roundNumber",
    )
    fun findFirstReadyAt(@Param("roundNumber") roundNumber: Int): Instant?
}

/**
 * Histórico de temporadas (issue #60). Sem `@Cacheable`: a leitura acontece
 * quando o técnico abre a tela de histórico, não a cada rodada, e o cache só
 * adicionaria um lugar para a temporada recém-gravada não aparecer.
 */
interface SeasonHistoryJpaRepository : JpaRepository<SeasonHistoryEntity, Int> {
    /** Da temporada mais recente para a mais antiga — ordem de exibição. */
    fun findAllByOrderBySeasonDesc(): List<SeasonHistoryEntity>
}

/** Propostas de transferência entre técnicos (issue #63). */
interface TransferOfferJpaRepository : JpaRepository<TransferOfferEntity, Long> {
    /**
     * TODAS as pendentes de um clube (comprador ou vendedor), da mais nova para
     * a mais antiga. Sem limite: são as que pedem ação, e o prazo em rodadas
     * já as mantém poucas — misturá-las com o histórico num `Top50` escondia
     * uma pendente antiga atrás de 50 resolvidas (revisão do PR #94).
     */
    @Query(
        "select o from TransferOfferEntity o " +
            "where (o.buyerClubId = :clubId or o.sellerClubId = :clubId) " +
            "and o.status = '${TransferOfferEntity.PENDING}' order by o.id desc",
    )
    fun findPendingByClub(@Param("clubId") clubId: Long): List<TransferOfferEntity>

    /** Histórico (já resolvidas) de um clube, da mais nova para a mais antiga, limitado por [page]. */
    @Query(
        "select o from TransferOfferEntity o " +
            "where (o.buyerClubId = :clubId or o.sellerClubId = :clubId) " +
            "and o.status <> '${TransferOfferEntity.PENDING}' order by o.id desc",
    )
    fun findResolvedByClub(@Param("clubId") clubId: Long, page: Pageable): List<TransferOfferEntity>

    fun findAllByStatus(status: String): List<TransferOfferEntity>

    fun findAllByPlayerIdAndStatus(playerId: Long, status: String): List<TransferOfferEntity>

    /**
     * Compare-and-set da máquina de estados: grava status, motivo e prazo SÓ se
     * a proposta ainda está `PENDING`. Devolve as linhas afetadas — `0` = outra
     * escrita já a resolveu (aceite, recusa, retirada ou prazo). O UPDATE segura
     * o lock da linha até o commit, então um concorrente que leu `PENDING`
     * espera e, ao seguir, encontra o status novo → 0 linhas.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update TransferOfferEntity o " +
            "set o.status = :status, o.expiryReason = :expiryReason, o.roundsLeft = :roundsLeft " +
            "where o.id = :id and o.status = '${TransferOfferEntity.PENDING}'",
    )
    fun saveIfPending(
        @Param("id") id: Long,
        @Param("status") status: String,
        @Param("expiryReason") expiryReason: String?,
        @Param("roundsLeft") roundsLeft: Int,
    ): Int
}

interface StandingsJpaRepository : JpaRepository<StandingsEntity, StandingsId> {
    /**
     * Tabelas da temporada ativa = maior `season`, uma por divisão (issue
     * #47). Sem parâmetros de propósito: o cache usa `SimpleKey.EMPTY` e a
     * subquery resolve a temporada ativa na mesma ida ao banco.
     */
    @Cacheable("standings")
    @Query(
        "select s from StandingsEntity s " +
            "where s.season = (select max(x.season) from StandingsEntity x) " +
            "order by s.division",
    )
    fun findAllForLatestSeason(): List<StandingsEntity>

    /** Tabelas de uma temporada específica (histórico), ordenadas por divisão. */
    fun findAllBySeasonOrderByDivision(season: Int): List<StandingsEntity>

    @CacheEvict("standings", allEntries = true)
    override fun <S : StandingsEntity> save(entity: S): S
}
