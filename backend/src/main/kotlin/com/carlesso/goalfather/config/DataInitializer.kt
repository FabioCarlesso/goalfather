package com.carlesso.goalfather.config

import com.carlesso.goalfather.adapter.out.persistence.entity.ClubEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.MarketEntryEntity
import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.ClubJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.MarketEntryJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.PlayerJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.RoundJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.StandingsJpaRepository
import com.carlesso.goalfather.application.seed.dsl.DivisionBuilder
import com.carlesso.goalfather.application.seed.dsl.League
import com.carlesso.goalfather.application.seed.dsl.league
import com.carlesso.goalfather.domain.model.Position
import kotlinx.serialization.json.Json
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Configuration

/**
 * Seed inicial — espelha `frontend/src/mocks/seed.ts`.
 *
 * 12 clubes em 2 divisões (issue #47): divisão 1 com o clube do usuário +
 * 5 IA, divisão 2 com 6 clubes IA. 4 entradas iniciais no mercado, rodada 1
 * com 3 pareamentos POR divisão (Berger round-robin) e standings vazias da
 * temporada 2026 (uma tabela por divisão).
 *
 * A descrição do estado vive na **DSL de seed** (`application/seed/dsl`),
 * que produz um [League] imutável; este runner só faz a tradução
 * `domínio → entidades JPA` e persiste. Idempotente: só insere se o DB
 * estiver vazio.
 *
 * Nota: os atributos secundários (pace/shooting/passing/defending) passam a
 * herdar o `overall` por padrão na DSL — antes havia uma variação cosmética
 * por jogador que nada lia (a engine usa só o `overall`). Simplificação
 * deliberada da refatoração (issue #10).
 */
@Configuration
class DataInitializer(
    private val clubRepo: ClubJpaRepository,
    private val playerRepo: PlayerJpaRepository,
    private val marketRepo: MarketEntryJpaRepository,
    private val roundRepo: RoundJpaRepository,
    private val standingsRepo: StandingsJpaRepository,
    private val json: Json,
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        if (clubRepo.count() > 0L) return
        persist(seedLeague())
    }

    /** Estado inicial descrito declarativamente via DSL. */
    private fun seedLeague(): League = league(season = 2026) {
        division(1) {
            club(1, "Goal Father FC") {
                cash = 800_000_00
                stadium = 15_000
                player(1, "Marcos Figueiredo") { position = Position.GK; overall = 78; salary = 25_000_00; age = 32 }
                player(2, "Rodrigo Alves") { position = Position.CB; overall = 75; salary = 18_000_00; age = 27 }
                player(3, "Túlio Mendes") { position = Position.CB; overall = 72; salary = 15_000_00; age = 24 }
                player(4, "Caio Bernardes") { position = Position.CB; overall = 70; salary = 13_000_00; age = 22 }
                player(5, "Pedro Henrique") { position = Position.CB; overall = 73; salary = 16_000_00; age = 26 }
                player(6, "Felipe Costa") { position = Position.MF; overall = 80; salary = 28_000_00; age = 28 }
                player(7, "Diego Lobato") { position = Position.MF; overall = 76; salary = 22_000_00; age = 25 }
                player(8, "Igor Vasques") { position = Position.MF; overall = 74; salary = 19_000_00; age = 23 }
                player(9, "Carlos Almeida") { position = Position.MF; overall = 71; salary = 14_000_00; age = 21 }
                player(10, "Renato Silva") { position = Position.FW; overall = 88; salary = 55_000_00; age = 29 }
                player(11, "João Faria") { position = Position.FW; overall = 79; salary = 26_000_00; age = 26 }
            }

            // Clubes da IA (id 2..6): elenco sintético de 11 jogadores com
            // overall uniforme = força do clube. O bloco `club { }` é código
            // normal, então dá para popular o elenco com um `for` — a DSL não
            // obriga repetição.
            for ((id, name, strength) in AI_CLUBS_DIV1) {
                aiClub(id, name, strength)
            }
        }

        // Divisão 2 (issue #47): 6 clubes IA, elencos um degrau mais fracos.
        division(2) {
            for ((id, name, strength) in AI_CLUBS_DIV2) {
                aiClub(id, name, strength)
            }
        }

        market {
            player(101, "Lucas Vidigal", price = 450_000_00) { position = Position.FW; overall = 81; salary = 32_000_00; age = 25 }
            player(102, "André Pacheco", price = 220_000_00) { position = Position.MF; overall = 77; salary = 23_000_00; age = 24 }
            player(103, "Bruno Aparício", price = 300_000_00) { position = Position.CB; overall = 79; salary = 25_000_00; age = 27 }
            player(104, "Eduardo Ramires", price = 180_000_00) { position = Position.GK; overall = 76; salary = 21_000_00; age = 28 }
        }

        // Rodada 1 — Berger com 6 times por divisão:
        // divisão 1: (1×6), (2×5), (3×4); divisão 2: (7×12), (8×11), (9×10).
        round(1) {
            match(homeId = 1, awayId = 6)
            match(homeId = 2, awayId = 5)
            match(homeId = 3, awayId = 4)
            match(homeId = 7, awayId = 12)
            match(homeId = 8, awayId = 11)
            match(homeId = 9, awayId = 10)
        }
    }

    /** Clube IA padrão: caixa/estádio fixos e elenco sintético de 11 jogadores. */
    private fun DivisionBuilder.aiClub(id: Long, name: String, strength: Int) {
        club(id, name) {
            cash = 500_000_00
            stadium = 12_000
            for (slot in 1..11) {
                player(id * 1000 + slot, "$name Player $slot") {
                    position = positionForSlot(slot)
                    overall = strength
                    salary = 10_000_00
                    age = ageForSlot(slot)
                }
            }
        }
    }

    /**
     * Idades espalhadas entre 21 e 32 (issue #55). Com o elenco inteiro na
     * mesma idade — era 25 para todo mundo — os onze cruzariam a barreira da
     * aposentadoria na MESMA virada de temporada, e o clube trocaria de elenco
     * inteiro de uma vez. Espalhar transforma isso numa renovação gradual, uma
     * ou duas saídas por temporada.
     */
    private fun ageForSlot(slot: Int): Int = 21 + (slot * 5) % 12

    /** Traduz o agregado de domínio para entidades JPA e grava. */
    private fun persist(league: League) {
        for (club in league.clubs) {
            clubRepo.save(
                ClubEntity(
                    id = club.id.value,
                    name = club.name,
                    cash = club.cash,
                    stadiumCapacity = club.stadiumCapacity,
                    division = club.division.value,
                ),
            )
            for (player in club.squad) {
                playerRepo.save(player.toEntity(clubId = club.id.value))
            }
        }

        for (entry in league.market) {
            playerRepo.save(entry.player.toEntity(clubId = null))
            marketRepo.save(MarketEntryEntity(playerId = entry.player.id.value, price = entry.price))
        }

        for (round in league.rounds) {
            roundRepo.save(round.toEntity(json))
        }

        for (standings in league.initialStandings()) {
            standingsRepo.save(standings.toEntity(json))
        }
    }

    private fun positionForSlot(slot: Int): Position = when (slot) {
        1 -> Position.GK
        in 2..5 -> Position.CB
        in 6..9 -> Position.MF
        else -> Position.FW
    }

    private companion object {
        /** Clubes controlados pela IA: (id, nome, overall uniforme do elenco). */
        val AI_CLUBS_DIV1 = listOf(
            Triple(2L, "Atlético Bonsucesso", 76),
            Triple(3L, "Esporte Clube Vargem", 72),
            Triple(4L, "Tupinambás FC", 70),
            Triple(5L, "Independente Sul", 74),
            Triple(6L, "Real Capela", 73),
        )

        /** Divisão 2 (issue #47): mesmos moldes, força um degrau abaixo. */
        val AI_CLUBS_DIV2 = listOf(
            Triple(7L, "Ferroviária da Serra", 68),
            Triple(8L, "Operário do Vale", 66),
            Triple(9L, "Náutico Boa Vista", 67),
            Triple(10L, "Comercial de Ouro Fino", 64),
            Triple(11L, "Juventude Alvorada", 65),
            Triple(12L, "União Primavera", 63),
        )
    }
}
