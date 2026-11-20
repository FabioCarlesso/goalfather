package com.carlesso.goalfather.application.seed.dsl

import com.carlesso.goalfather.test.testContract
import com.carlesso.goalfather.domain.model.Club
import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Country
import com.carlesso.goalfather.domain.model.Cup
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.MarketEntry
import com.carlesso.goalfather.domain.model.Player
import com.carlesso.goalfather.domain.model.PlayerId
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.Round
import com.carlesso.goalfather.domain.model.Standings
import com.carlesso.goalfather.domain.model.cents
import com.carlesso.goalfather.domain.rules.startWorld
import com.carlesso.goalfather.test.brazilCatalog
import kotlin.random.Random

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
 *     division(1) {
 *         club(1, "Goal Father FC") {
 *             cash = 160_000_00
 *             stadium = 15_000
 *             player(1, "Marcos Figueiredo") { position = Position.GK; overall = 78; salary = 5_000_00; age = 32 }
 *         }
 *     }
 *     division(2) { club(7, "Ferroviária da Serra") { /* ... */ } }
 *     market { player(101, "Lucas Vidigal", price = 90_000_00) { /* ... */ } }
 * }
 * ```
 *
 * Por que cada recurso Kotlin aparece aqui:
 * - **`fun X.() -> Unit` (lambda-with-receiver):** dentro do bloco, os
 *   membros do builder ficam disponíveis sem qualificador (`cash = ...`,
 *   `player(...)`). É o que dá a leitura "configuração", não "código".
 * - **`@DslMarker`:** impede vazamento de escopo entre receivers
 *   aninhados. Sem ele, dentro de `division { }` o compilador ainda
 *   enxergaria `market(...)` do `LeagueBuilder` externo; com ele, isso vira
 *   erro de compilação (ver [SeedDsl]).
 * - **builder mutável → alvo imutável:** os `*Builder` usam `var`/listas
 *   mutáveis durante a construção e expõem `build()` que devolve `data
 *   class` imutáveis do domínio (`Club`, `Player`, ...). O estado mutável
 *   nunca escapa do builder.
 */

/**
 * `@DslMarker` aplicado a todos os receivers da DSL. O efeito: dentro de um
 * bloco aninhado só o receiver mais interno é acessível de forma implícita.
 * Ex.: chamar `market { ... }` dentro de `division(1) { ... }` não compila —
 * exatamente o isolamento de escopo que a issue #10 pede.
 */
@DslMarker
annotation class SeedDsl

/**
 * Produto imutável da DSL. Agrega tudo que o seed precisa persistir:
 * clubes (com elenco, cada um na sua divisão) e mercado. Tabelas, copa e
 * o primeiro encaixe do calendário são DERIVADOS dos clubes pelas mesmas
 * regras que o jogo usa depois — não há razão para descrevê-los à mão.
 */
data class League(
    val season: Int,
    val clubs: List<Club>,
    val market: List<MarketEntry>,
) {
    // #131 integrador: ponte da frente B para o seed antigo continuar de pé
    // enquanto a frente A (seed pelo pack + catálogo gravado) não entra — o
    // mundo do seed vira o do `startWorld` sobre o catálogo interino do Brasil.
    private val world by lazy { // A copa de teste comporta de 4 a 32 clubes; com menos, o formato não fecha
    // com os inscritos e o mundo nasce sem copa (como no `drawNationalCup`).
    startWorld(season, clubs, brazilCatalog(legs = 2, cupClubs = clubs.size.coerceIn(4, 32))) }

    /** Tabelas zeradas — uma por liga do catálogo, com as faixas das regras de vaga. */
    fun initialStandings(): List<Standings> = world.standings

    /** Copa da temporada inicial, sorteada pela MESMA regra e seed da virada. */
    fun initialCup(): Cup? = world.cups.firstOrNull()

    /** O primeiro encaixe da temporada inicial, tirado do MESMO plano que o jogo usa depois. */
    fun firstRound(): Round? = world.firstRound
}

@SeedDsl
class PlayerBuilder(private val id: Long, private val name: String) {
    var position: Position = Position.MF
    var overall: Int = 70
    /**
     * Dinheiro na DSL é o literal em centavos de USD (issue #122): a DSL é a
     * borda de ENTRADA do seed, e `salary = 5_000_00` lê melhor que
     * `5_000_00.cents`. O `build()` converte para `Money` — dali para dentro
     * o domínio só vê o tipo.
     */
    var salary: Int = 0
    var age: Int = 25
    var stamina: Int = 100

    // Atributos secundários: `null` significa "herda o overall". Modelar a
    // ausência no tipo (Int?) é mais honesto que um sentinela mágico.
    var pace: Int? = null
    var shooting: Int? = null
    var passing: Int? = null
    var defending: Int? = null

    // Nacionalidade (issue #123). Default = país do seed; só o estrangeiro declara.
    var nationality: Country = Countries.DEFAULT
    var secondNationality: Country? = null

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
        // Contrato de teste (issue #136): a DSL só declara o salário.
        employment = testContract(salary.cents),
        age = age,
        nationality = nationality,
        secondNationality = secondNationality,
    )
}

@SeedDsl
class ClubBuilder(
    private val id: Long,
    private val name: String,
    private val division: Division = Division.FIRST,
) {
    var cash: Long = 0
    var stadium: Int = 0
    var country: Country = Countries.DEFAULT

    private val players = mutableListOf<Player>()

    fun player(id: Long, name: String, block: PlayerBuilder.() -> Unit) {
        players += PlayerBuilder(id, name).apply(block).build()
    }

    fun build(): Club = Club(
        id = ClubId(id),
        name = name,
        cash = cash.cents,
        stadiumCapacity = stadium,
        squad = players.toList(),
        division = division,
        country = country,
    )
}

/**
 * Agrupa clubes de uma divisão (issue #47). Só repassa a divisão ao
 * [ClubBuilder] — o produto continua sendo a lista plana de clubes, cada um
 * sabendo seu tier.
 */
@SeedDsl
class DivisionBuilder(private val division: Division) {
    private val clubs = mutableListOf<Club>()

    fun club(id: Long, name: String, block: ClubBuilder.() -> Unit) {
        clubs += ClubBuilder(id, name, division).apply(block).build()
    }

    fun build(): List<Club> = clubs.toList()
}

@SeedDsl
class MarketBuilder {
    private val entries = mutableListOf<MarketEntry>()

    fun player(id: Long, name: String, price: Long, block: PlayerBuilder.() -> Unit) {
        entries += MarketEntry(player = PlayerBuilder(id, name).apply(block).build(), price = price.cents)
    }

    fun build(): List<MarketEntry> = entries.toList()
}

@SeedDsl
class LeagueBuilder(private val season: Int) {
    private val clubs = mutableListOf<Club>()
    private var market: List<MarketEntry> = emptyList()

    /** Clube declarado direto na liga entra na elite (divisão 1). */
    fun club(id: Long, name: String, block: ClubBuilder.() -> Unit) {
        clubs += ClubBuilder(id, name).apply(block).build()
    }

    /** Bloco de divisão (issue #47): clubes declarados aqui entram no tier dado. */
    fun division(tier: Int, block: DivisionBuilder.() -> Unit) {
        clubs += DivisionBuilder(Division(tier)).apply(block).build()
    }

    fun market(block: MarketBuilder.() -> Unit) {
        market = MarketBuilder().apply(block).build()
    }

    fun build(): League = League(season = season, clubs = clubs.toList(), market = market)
}

/**
 * Ponto de entrada da DSL. `block` é uma extensão de [LeagueBuilder], então
 * o corpo é avaliado com o builder como `this` implícito.
 */
fun league(season: Int, block: LeagueBuilder.() -> Unit): League =
    LeagueBuilder(season).apply(block).build()
