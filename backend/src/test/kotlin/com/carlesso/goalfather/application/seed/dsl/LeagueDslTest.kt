package com.carlesso.goalfather.application.seed.dsl

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.rules.PROMOTION_RELEGATION_SPOTS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Cobertura da DSL de seed (issue #10): um teste por builder, validando que
 * o produto imutável corresponde ao descrito. Puro — sem contexto Spring.
 *
 * O isolamento de escopo do `@DslMarker` é garantido em tempo de COMPILAÇÃO
 * (chamar `player(...)` dentro de `round { }` não compila), então não há o
 * que testar em runtime — a própria existência desta suíte compilando já é
 * a prova de que os escopos válidos passam.
 */
class LeagueDslTest {

    @Test
    fun `PlayerBuilder herda overall nos atributos secundarios por padrao`() {
        val p = PlayerBuilder(10, "Renato Silva").apply {
            position = Position.FW; overall = 88; salary = 55_000_00; age = 29
        }.build()

        assertEquals(PlayerId(10), p.id)
        assertEquals("Renato Silva", p.name)
        assertEquals(Position.FW, p.position)
        assertEquals(88, p.overall)
        // pace/shooting/passing/defending herdam o overall quando não setados.
        assertEquals(88, p.pace)
        assertEquals(88, p.shooting)
        assertEquals(88, p.passing)
        assertEquals(88, p.defending)
        assertEquals(100, p.stamina)
    }

    @Test
    fun `PlayerBuilder respeita atributos secundarios explicitos`() {
        val p = PlayerBuilder(1, "Marcos").apply {
            position = Position.GK; overall = 78; defending = 90; pace = 40
        }.build()

        assertEquals(90, p.defending)
        assertEquals(40, p.pace)
        assertEquals(78, p.shooting) // não setado → herda overall
    }

    @Test
    fun `ClubBuilder agrega elenco, caixa e estadio`() {
        val club = ClubBuilder(1, "Goal Father FC").apply {
            cash = 800_000_00
            stadium = 15_000
            player(1, "GK") { position = Position.GK; overall = 78 }
            player(2, "MF") { position = Position.MF; overall = 80 }
        }.build()

        assertEquals(ClubId(1), club.id)
        assertEquals(800_000_00, club.cash)
        assertEquals(15_000, club.stadiumCapacity)
        assertEquals(listOf(PlayerId(1), PlayerId(2)), club.squad.map { it.id })
    }

    @Test
    fun `MarketBuilder produz entradas com preco`() {
        val market = MarketBuilder().apply {
            player(101, "Lucas Vidigal", price = 450_000_00) { position = Position.FW; overall = 81 }
        }.build()

        assertEquals(1, market.size)
        assertEquals(PlayerId(101), market.first().player.id)
        assertEquals(450_000_00, market.first().price)
    }

    @Test
    fun `RoundBuilder resolve nomes, divisao e numera partidas`() {
        val clubs = listOf(
            simpleClub(1, "Casa"),
            simpleClub(6, "Fora"),
            simpleClub(2, "A"),
            simpleClub(5, "B"),
        ).associateBy { it.id.value }

        val round = RoundBuilder(1).apply {
            match(homeId = 1, awayId = 6)
            match(homeId = 2, awayId = 5)
        }.build(season = 2026, clubById = clubs)

        assertEquals(1, round.number)
        assertEquals(2026, round.season)
        assertEquals(RoundStatus.Scheduled, round.status)
        // matchId = rodada × 1000 + divisão × 100 + índice (fórmula do gerador).
        assertEquals(1101L, round.matches[0].matchId)
        assertEquals(1102L, round.matches[1].matchId)
        assertEquals("Casa", round.matches[0].homeClubName)
        assertEquals("Fora", round.matches[0].awayClubName)
        assertEquals(Division.FIRST, round.matches[0].division)
    }

    @Test
    fun `RoundBuilder numera partidas por divisao e rejeita confronto entre divisoes`() {
        val clubs = mapOf(
            1L to simpleClub(1, "D1 A"),
            2L to simpleClub(2, "D1 B"),
            7L to simpleClub(7, "D2 A", division = 2),
            8L to simpleClub(8, "D2 B", division = 2),
        )

        val round = RoundBuilder(1).apply {
            match(homeId = 1, awayId = 2)
            match(homeId = 7, awayId = 8)
        }.build(season = 2026, clubById = clubs)

        // Índice reinicia por divisão: 1101 (div 1) e 1201 (div 2).
        assertEquals(listOf(1101L, 1201L), round.matches.map { it.matchId })
        assertEquals(Division(2), round.matches[1].division)

        assertFailsWith<IllegalArgumentException> {
            RoundBuilder(1).apply { match(homeId = 1, awayId = 7) }
                .build(season = 2026, clubById = clubs)
        }
    }

    @Test
    fun `league monta o agregado completo e a tabela inicial`() {
        val league = league(season = 2026) {
            club(1, "Casa") {
                cash = 100
                stadium = 1_000
                player(1, "P1") { position = Position.GK; overall = 70 }
            }
            club(2, "Fora") {
                cash = 200
                stadium = 2_000
                player(2, "P2") { position = Position.FW; overall = 75 }
            }
            market {
                player(101, "Livre", price = 50) { position = Position.MF; overall = 80 }
            }
            round(1) { match(homeId = 1, awayId = 2) }
        }

        assertEquals(2026, league.season)
        assertEquals(listOf("Casa", "Fora"), league.clubs.map { it.name })
        assertEquals(1, league.market.size)
        assertEquals(1, league.rounds.size)
        assertEquals("Fora", league.rounds.first().matches.first().awayClubName)

        // Divisão única → uma tabela, sem zona de promoção nem rebaixamento.
        val standings = league.initialStandings().single()
        assertEquals(2026, standings.season)
        assertEquals(0, standings.round)
        assertEquals(Division.FIRST, standings.division)
        assertEquals(0, standings.promotionSpots)
        assertEquals(0, standings.relegationSpots)
        assertEquals(listOf(1, 2), standings.rows.map { it.position })
        assertEquals(listOf(ClubId(1), ClubId(2)), standings.rows.map { it.clubId })
        assertTrue(standings.rows.all { it.points == 0 && it.played == 0 })
    }

    @Test
    fun `division agrupa clubes por tier e gera uma tabela por divisao`() {
        val league = league(season = 2026) {
            division(1) {
                club(1, "Elite A") { cash = 1 }
                club(2, "Elite B") { cash = 1 }
            }
            division(2) {
                club(7, "Acesso A") { cash = 1 }
                club(8, "Acesso B") { cash = 1 }
            }
        }

        assertEquals(
            mapOf(1L to Division(1), 2L to Division(1), 7L to Division(2), 8L to Division(2)),
            league.clubs.associate { it.id.value to it.division },
        )

        val tables = league.initialStandings()
        assertEquals(listOf(Division(1), Division(2)), tables.map { it.division })
        // Elite não sobe; última divisão não desce. As demais vagas seguem a regra.
        assertEquals(0, tables[0].promotionSpots)
        assertEquals(PROMOTION_RELEGATION_SPOTS, tables[0].relegationSpots)
        assertEquals(PROMOTION_RELEGATION_SPOTS, tables[1].promotionSpots)
        assertEquals(0, tables[1].relegationSpots)
    }

    /** Clube mínimo para montar mapas do RoundBuilder sem passar por ClubBuilder. */
    private fun simpleClub(id: Long, name: String, division: Int = 1) =
        ClubBuilder(id, name, Division(division)).build()
}
