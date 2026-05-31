package com.carlesso.goalfather.application.seed.dsl

import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.RoundMatch
import com.carlesso.goalfather.domain.model.RoundStatus
import com.carlesso.goalfather.domain.model.StandingRow
import com.carlesso.goalfather.domain.model.Standings

/**
 * DSL type-safe de seed de liga.
 *
 * Exemplo canônico de Kotlin **lambda-with-receiver + builder mutável →
 * objeto imutável**, no espírito de `http { ... }` (Spring Security) e
 * `routing { get { ... } }` (Ktor). Substitui o `DataInitializer`
 * imperativo (estilo Java) por uma descrição declarativa:
 *
 * ```kotlin
 * val league = league(season = 2026) {
 *     club(1, "Goal Father FC") {
 *         cash = 800_000_00
 *         stadium = 15_000
 *         player(1, "Marcos Figueiredo") { position = Position.GK; overall = 78; salary = 25_000_00; age = 32 }
 *     }
 *     round(1) { match(homeId = 1, awayId = 6) }
 * }
 * ```
 *
 * Por que cada recurso Kotlin aparece aqui:
 * - **`fun X.() -> Unit` (lambda-with-receiver):** dentro do bloco, os
 *   membros do builder ficam disponíveis sem qualificador (`cash = ...`,
 *   `player(...)`). É o que dá a leitura "configuração", não "código".
 * - **`@DslMarker`:** impede vazamento de escopo entre receivers
 *   aninhados. Sem ele, dentro de `round { }` o compilador ainda
 *   enxergaria `player(...)` do `ClubBuilder` externo; com ele, isso vira
 *   erro de compilação (ver [SeedDsl]).
 * - **builder mutável → alvo imutável:** os `*Builder` usam `var`/listas
 *   mutáveis durante a construção e expõem `build()` que devolve `data
 *   class` imutáveis do domínio (`Club`, `Player`, ...). O estado mutável
 *   nunca escapa do builder.
 */

/**
 * `@DslMarker` aplicado a todos os receivers da DSL. O efeito: dentro de um
 * bloco aninhado só o receiver mais interno é acessível de forma implícita.
 * Ex.: chamar `player(...)` dentro de `round(1) { ... }` não compila —
 * exatamente o isolamento de escopo que a issue #10 pede.
 */
@DslMarker
annotation class SeedDsl

/**
 * Produto imutável da DSL. Agrega tudo que o seed precisa persistir:
 * clubes (com elenco), mercado e calendário. A tabela inicial é derivada
 * dos clubes — não há razão para descrevê-la à mão.
 */
data class League(
    val season: Int,
    val clubs: List<Club>,
    val market: List<MarketEntry>,
    val rounds: List<Round>,
) {
    /** Tabela zerada, na ordem de declaração dos clubes. */
    fun initialStandings(): Standings = Standings(
        season = season,
        round = 0,
        rows = clubs.mapIndexed { i, club ->
            StandingRow(position = i + 1, clubId = club.id, clubName = club.name)
        },
    )
}

@SeedDsl
class PlayerBuilder(private val id: Long, private val name: String) {
    var position: Position = Position.MF
    var overall: Int = 70
    var salary: Int = 0
    var age: Int = 25
    var stamina: Int = 100

    // Atributos secundários: `null` significa "herda o overall". Modelar a
    // ausência no tipo (Int?) é mais honesto que um sentinela mágico.
    var pace: Int? = null
    var shooting: Int? = null
    var passing: Int? = null
    var defending: Int? = null

    fun build(): Player = Player(
        id = PlayerId(id),
        name = name,
        position = position,
        overall = overall,
        pace = pace ?: overall,
        shooting = shooting ?: overall,
        passing = passing ?: overall,
        defending = defending ?: overall,
        stamina = stamina,
        salary = salary,
        age = age,
    )
}

@SeedDsl
class ClubBuilder(private val id: Long, private val name: String) {
    var cash: Long = 0
    var stadium: Int = 0

    private val players = mutableListOf<Player>()

    fun player(id: Long, name: String, block: PlayerBuilder.() -> Unit) {
        players += PlayerBuilder(id, name).apply(block).build()
    }

    fun build(): Club = Club(
        id = ClubId(id),
        name = name,
        cash = cash,
        stadiumCapacity = stadium,
        squad = players.toList(),
    )
}

@SeedDsl
class MarketBuilder {
    private val entries = mutableListOf<MarketEntry>()

    fun player(id: Long, name: String, price: Long, block: PlayerBuilder.() -> Unit) {
        entries += MarketEntry(player = PlayerBuilder(id, name).apply(block).build(), price = price)
    }

    fun build(): List<MarketEntry> = entries.toList()
}

@SeedDsl
class RoundBuilder(private val number: Int) {
    private val pairings = mutableListOf<Pair<Long, Long>>()

    fun match(homeId: Long, awayId: Long) {
        pairings += homeId to awayId
    }

    /**
     * Resolve os nomes dos clubes (só conhecidos no nível da liga) e numera
     * as partidas pela mesma fórmula do gerador de calendário
     * (`generateRound`): `número da rodada × 1000 + índice`.
     */
    fun build(season: Int, clubNameById: Map<Long, String>): Round {
        val matches = pairings.mapIndexed { i, (homeId, awayId) ->
            RoundMatch(
                matchId = number * 1000L + i + 1,
                homeClubId = ClubId(homeId),
                awayClubId = ClubId(awayId),
                homeClubName = clubNameById.getValue(homeId),
                awayClubName = clubNameById.getValue(awayId),
            )
        }
        return Round(number = number, season = season, status = RoundStatus.Scheduled, matches = matches)
    }
}

@SeedDsl
class LeagueBuilder(private val season: Int) {
    private val clubs = mutableListOf<Club>()
    private var market: List<MarketEntry> = emptyList()
    private val roundBuilders = mutableListOf<RoundBuilder>()

    fun club(id: Long, name: String, block: ClubBuilder.() -> Unit) {
        clubs += ClubBuilder(id, name).apply(block).build()
    }

    fun market(block: MarketBuilder.() -> Unit) {
        market = MarketBuilder().apply(block).build()
    }

    fun round(number: Int, block: RoundBuilder.() -> Unit) {
        roundBuilders += RoundBuilder(number).apply(block)
    }

    fun build(): League {
        val clubNameById = clubs.associate { it.id.value to it.name }
        return League(
            season = season,
            clubs = clubs.toList(),
            market = market,
            rounds = roundBuilders.map { it.build(season, clubNameById) },
        )
    }
}

/**
 * Ponto de entrada da DSL. `block` é uma extensão de [LeagueBuilder], então
 * o corpo é avaliado com o builder como `this` implícito.
 */
fun league(season: Int, block: LeagueBuilder.() -> Unit): League =
    LeagueBuilder(season).apply(block).build()
