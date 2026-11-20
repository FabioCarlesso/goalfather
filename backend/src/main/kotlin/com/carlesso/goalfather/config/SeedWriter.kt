package com.carlesso.goalfather.config

import com.carlesso.goalfather.adapter.out.persistence.CompetitionCatalogTransaction
import com.carlesso.goalfather.adapter.out.persistence.SavedCountryCatalog
import com.carlesso.goalfather.adapter.out.persistence.TransferWindowJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.entity.ClubEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.MarketEntryEntity
import com.carlesso.goalfather.adapter.out.persistence.mapper.applyBoard
import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.ContinentalEditionJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.CupJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.MarketEntryJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.RoundJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.StandingsJpaRepository
import com.carlesso.goalfather.application.seed.InitialWorld
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.rules.WorldStart
import com.carlesso.goalfather.domain.rules.boardExpectations
import jakarta.persistence.EntityManager
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Traduz o mundo inicial ([InitialWorld] + [WorldStart], domínio puro) para
 * entidades JPA e grava (issues #101 e #131). Dois chamadores: o boot (banco
 * vazio, [DataInitializer]) e o reset do profile `e2e` ([E2eStateReset]).
 *
 * Não verifica se o banco está vazio: quem chama decide. Gravar por cima de
 * um mundo existente violaria as PKs.
 *
 * **Uma transação só** (`@Transactional`): o mundo nasce inteiro ou não nasce.
 * Um boot interrompido no meio deixaria clubes sem catálogo — que o próximo
 * boot trataria como save antigo incompatível.
 *
 * **Em lote** (são ~3.000 jogadores no pack completo): clubes e jogadores vão
 * por `EntityManager.persist`, não por `repository.save`. Com id ATRIBUÍDO
 * (não gerado), o `save` do Spring Data não sabe se a linha é nova e faz um
 * `merge` — um SELECT por jogador antes do INSERT. O `persist` afirma "é
 * nova", e o Hibernate agrupa os INSERTs em batches JDBC
 * (`hibernate.jdbc.batch_size`). O `flush` + `clear` a cada [FLUSH_EVERY]
 * clubes descarrega os batches e solta as entidades já gravadas, para o
 * contexto de persistência não crescer com o mundo inteiro.
 */
@Component
class SeedWriter(
    private val entityManager: EntityManager,
    private val marketRepo: MarketEntryJpaRepository,
    private val roundRepo: RoundJpaRepository,
    private val standingsRepo: StandingsJpaRepository,
    private val cupRepo: CupJpaRepository,
    private val continentalRepo: ContinentalEditionJpaRepository,
    private val catalog: CompetitionCatalogTransaction,
    private val transferWindowRepo: TransferWindowJpaRepository,
    private val countries: SavedCountryCatalog,
    private val json: Json,
) {

    @Transactional
    fun write(world: InitialWorld, start: WorldStart) {
        countries.replaceAll(world.countries)

        // Metas da diretoria da 1ª temporada (issue #62), fixadas já no seed:
        // a meta é função da liga como ela COMEÇA, antes de qualquer compra.
        val boards = boardExpectations(world.year, world.clubs)
        // `chunked` fatia a lista em blocos de FLUSH_EVERY — o lote sem contador manual.
        world.clubs.chunked(FLUSH_EVERY).forEach { chunk ->
            for (club in chunk) {
                entityManager.persist(club.toNewEntity().apply { applyBoard(boards[club.id]) })
                club.squad.forEach { entityManager.persist(it.toEntity(clubId = club.id.value)) }
            }
            entityManager.flush()
            entityManager.clear()
        }

        for (entry in world.market) {
            entityManager.persist(entry.player.toEntity(clubId = null))
            // `save` (com o `@CacheEvict` do repositório), não `persist`: o
            // cache do mercado tem de saber que a lista mudou.
            marketRepo.save(MarketEntryEntity(playerId = entry.player.id.value, price = entry.price.cents))
        }

        // Primeiro encaixe do calendário, tabelas e copas (issues #125/#131):
        // decididos por `startWorld`, só gravados aqui.
        start.firstRound?.let { roundRepo.save(it.toEntity(json)) }
        start.standings.forEach { standingsRepo.save(it.toEntity(json)) }
        start.cups.forEach { cupRepo.save(it.toEntity(json)) }
        // 1ª edição das continentais (issue #132), sorteada pelo seed.
        start.continentals.forEach { continentalRepo.save(it.toEntity(json)) }

        // Janelas de transferência do pack (issue #137).
        transferWindowRepo.saveAll(world.transferWindows.map { it.toEntity() })

        // Por último: catálogo presente = mundo completo (ver DataInitializer).
        catalog.replaceAll(world.competitions)
    }

    /**
     * Clube novo → entidade. Não existe `Club.toEntity` genérico de propósito
     * (ver `ClubMapper`): fora do seed, o clube é atualizado sobre a entidade
     * gerenciada, por causa do lock otimista.
     */
    private fun Club.toNewEntity(): ClubEntity = ClubEntity(
        id = id.value,
        name = name,
        cash = cash.cents,
        stadiumCapacity = stadiumCapacity,
        division = division.value,
        ticketPriceCents = ticketPriceCents.cents,
        country = country.code.value,
    )

    private companion object {
        const val FLUSH_EVERY = 20
    }
}
