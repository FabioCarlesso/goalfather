package com.carlesso.goalfather.config

import com.carlesso.goalfather.adapter.out.persistence.entity.ClubEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.MarketEntryEntity
import com.carlesso.goalfather.adapter.out.persistence.mapper.applyBoard
import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.ClubJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.CupJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.MarketEntryJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.PlayerJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.RoundJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.StandingsJpaRepository
import com.carlesso.goalfather.application.seed.dsl.League
import com.carlesso.goalfather.domain.rules.boardExpectations
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component

/**
 * Traduz o agregado de seed ([League], domínio puro) para entidades JPA e
 * grava. Extraído do [DataInitializer] (issue #101) para ter dois chamadores
 * sem duplicar o mapeamento: o boot (DB vazio) e o reset do profile `e2e`.
 *
 * Não verifica se o DB está vazio: quem chama decide. Gravar por cima de uma
 * liga existente violaria as PKs da seed.
 */
@Component
class SeedWriter(
    private val clubRepo: ClubJpaRepository,
    private val playerRepo: PlayerJpaRepository,
    private val marketRepo: MarketEntryJpaRepository,
    private val roundRepo: RoundJpaRepository,
    private val standingsRepo: StandingsJpaRepository,
    private val cupRepo: CupJpaRepository,
    private val json: Json,
) {

    fun write(league: League) {
        // Metas da diretoria da 1ª temporada (issue #62), fixadas já no seed:
        // a meta é função da liga como ela COMEÇA, antes de qualquer compra.
        val boards = boardExpectations(league.season, league.clubs)
        for (club in league.clubs) {
            clubRepo.save(
                ClubEntity(
                    id = club.id.value,
                    name = club.name,
                    cash = club.cash.cents,
                    stadiumCapacity = club.stadiumCapacity,
                    division = club.division.value,
                    country = club.country.code.value,
                ).apply { applyBoard(boards[club.id]) },
            )
            for (player in club.squad) {
                playerRepo.save(player.toEntity(clubId = club.id.value))
            }
        }

        for (entry in league.market) {
            playerRepo.save(entry.player.toEntity(clubId = null))
            marketRepo.save(MarketEntryEntity(playerId = entry.player.id.value, price = entry.price.cents))
        }

        for (round in league.rounds) {
            roundRepo.save(round.toEntity(json))
        }

        for (standings in league.initialStandings()) {
            standingsRepo.save(standings.toEntity(json))
        }

        league.initialCup()?.let { cupRepo.save(it.toEntity(json)) }
    }
}
