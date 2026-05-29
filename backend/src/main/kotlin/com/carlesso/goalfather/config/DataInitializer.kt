package com.carlesso.goalfather.config

import com.carlesso.goalfather.adapter.out.persistence.entity.PositionEnum
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundStatusEnum
import com.carlesso.goalfather.adapter.out.persistence.entity.PlayerEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.ClubEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.MarketEntryEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.RoundEntity
import com.carlesso.goalfather.adapter.out.persistence.entity.StandingsEntity
import com.carlesso.goalfather.adapter.out.persistence.mapper.toEntity
import com.carlesso.goalfather.adapter.out.persistence.repository.ClubJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.MarketEntryJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.PlayerJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.RoundJpaRepository
import com.carlesso.goalfather.adapter.out.persistence.repository.StandingsJpaRepository
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Configuration

/**
 * Seed inicial — espelha `frontend/src/mocks/seed.ts`.
 *
 * 6 clubes (1 humano + 5 IA), elenco do clube do usuário com nomes,
 * 4 entradas iniciais no mercado, rodada 1 com 3 pareamentos (Berger
 * round-robin), e standings vazias da temporada 2026.
 *
 * Idempotente: só insere se o DB estiver vazio.
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

        val mySquad = listOf(
            mkPlayer(1, "Marcos Figueiredo", Position.GK, 78, 25_000_00, 32),
            mkPlayer(2, "Rodrigo Alves", Position.CB, 75, 18_000_00, 27),
            mkPlayer(3, "Túlio Mendes", Position.CB, 72, 15_000_00, 24),
            mkPlayer(4, "Caio Bernardes", Position.CB, 70, 13_000_00, 22),
            mkPlayer(5, "Pedro Henrique", Position.CB, 73, 16_000_00, 26),
            mkPlayer(6, "Felipe Costa", Position.MF, 80, 28_000_00, 28),
            mkPlayer(7, "Diego Lobato", Position.MF, 76, 22_000_00, 25),
            mkPlayer(8, "Igor Vasques", Position.MF, 74, 19_000_00, 23),
            mkPlayer(9, "Carlos Almeida", Position.MF, 71, 14_000_00, 21),
            mkPlayer(10, "Renato Silva", Position.FW, 88, 55_000_00, 29),
            mkPlayer(11, "João Faria", Position.FW, 79, 26_000_00, 26),
        )

        // Clube do usuário (id=1) com elenco completo
        clubRepo.save(ClubEntity(
            id = 1, name = "Goal Father FC", cash = 800_000_00,
            stadiumCapacity = 15_000,
        ))
        for (player in mySquad) {
            playerRepo.save(player.toEntity(clubId = 1L))
        }

        // Clubes da IA (id=2..6) — sem elenco persistido (engine usa força agregada).
        // PlayRoundService chama clubRepo.findById(awayClubId) que retorna club com
        // squad vazia para IA. startingLineup() retorna Lineup vazio; teamStrength
        // cai no default 60.0. Para variar, configuramos cash diferente; força é
        // tratada pela engine como média do elenco quando existe.
        // Para clubes IA terem força distinta, criamos squads sintéticas pequenas.
        val aiClubs = listOf(
            Triple(2L, "Atlético Bonsucesso", 76),
            Triple(3L, "Esporte Clube Vargem", 72),
            Triple(4L, "Tupinambás FC", 70),
            Triple(5L, "Independente Sul", 74),
            Triple(6L, "Real Capela", 73),
        )
        for ((id, name, strength) in aiClubs) {
            clubRepo.save(ClubEntity(id = id, name = name, cash = 500_000_00, stadiumCapacity = 12_000))
            // 11 jogadores sintéticos com overall = strength
            for (i in 1..11) {
                val playerId = id * 1000 + i
                playerRepo.save(PlayerEntity(
                    id = playerId, clubId = id, name = "$name Player $i",
                    position = positionForSlot(i),
                    overall = strength, pace = strength, shooting = strength,
                    passing = strength, defending = strength, stamina = 100,
                    salary = 10_000_00, age = 25, goals = 0, injured = false,
                ))
            }
        }

        // Mercado inicial
        val marketPlayers = listOf(
            mkPlayer(101, "Lucas Vidigal", Position.FW, 81, 32_000_00, 25) to 450_000_00L,
            mkPlayer(102, "André Pacheco", Position.MF, 77, 23_000_00, 24) to 220_000_00L,
            mkPlayer(103, "Bruno Aparício", Position.CB, 79, 25_000_00, 27) to 300_000_00L,
            mkPlayer(104, "Eduardo Ramires", Position.GK, 76, 21_000_00, 28) to 180_000_00L,
        )
        for ((player, price) in marketPlayers) {
            playerRepo.save(player.toEntity(clubId = null))
            marketRepo.save(MarketEntryEntity(playerId = player.id.value, price = price))
        }

        // Rodada 1 — Berger com 6 times: (1×6), (2×5), (3×4)
        val matches = listOf(
            roundMatch(1001, 1, 6, "Goal Father FC", "Real Capela"),
            roundMatch(1002, 2, 5, "Atlético Bonsucesso", "Independente Sul"),
            roundMatch(1003, 3, 4, "Esporte Clube Vargem", "Tupinambás FC"),
        )
        val round1 = Round(number = 1, season = 2026, status = RoundStatus.Scheduled, matches = matches)
        roundRepo.save(RoundEntity(
            number = 1, season = 2026, status = RoundStatusEnum.Scheduled,
            matchesJson = json.encodeToString(ListSerializer(RoundMatch.serializer()), matches),
        ))

        // Standings inicial
        val initialRows = listOf(1L, 2L, 3L, 4L, 5L, 6L).mapIndexed { i, id ->
            val name = if (id == 1L) "Goal Father FC"
                else aiClubs.first { it.first == id }.second
            StandingRow(position = i + 1, clubId = ClubId(id), clubName = name)
        }
        val standings = Standings(season = 2026, round = 0, rows = initialRows)
        standingsRepo.save(StandingsEntity(
            season = 2026, round = 0,
            rowsJson = json.encodeToString(ListSerializer(StandingRow.serializer()), initialRows),
        ))

        // Referenciar Round/Standings para o compiler manter os imports
        @Suppress("UNUSED_EXPRESSION")
        run { round1.matches.size; standings.rows.size }
    }

    private fun mkPlayer(id: Long, name: String, pos: Position, ovr: Int, salary: Int, age: Int): Player {
        // Pequena variação nos atributos secundários para realismo.
        val v = { ovr + ((id % 7).toInt() - 3) }
        return Player(
            id = PlayerId(id), name = name, position = pos,
            overall = ovr,
            pace = v(), shooting = v(), passing = v(), defending = v(),
            stamina = 100, salary = salary, age = age,
        )
    }

    private fun positionForSlot(slot: Int): PositionEnum = when (slot) {
        1 -> PositionEnum.GK
        in 2..5 -> PositionEnum.CB
        in 6..9 -> PositionEnum.MF
        else -> PositionEnum.FW
    }

    private fun roundMatch(matchId: Long, homeId: Long, awayId: Long, homeName: String, awayName: String) =
        RoundMatch(
            matchId = matchId,
            homeClubId = ClubId(homeId),
            awayClubId = ClubId(awayId),
            homeClubName = homeName,
            awayClubName = awayName,
        )
}
