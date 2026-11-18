package com.carlesso.goalfather.application.seed

import com.carlesso.goalfather.adapter.out.worlddata.FileWorldDataProvider
import com.carlesso.goalfather.application.port.out.PackLoadResult
import com.carlesso.goalfather.domain.model.CompetitionFormat
import com.carlesso.goalfather.domain.model.CompetitionId
import com.carlesso.goalfather.domain.model.CompetitionKind
import com.carlesso.goalfather.domain.model.Division
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.leagueOf
import com.carlesso.goalfather.domain.model.world.WorldPack
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.domain.model.sumOfMoney
import com.carlesso.goalfather.domain.rules.squadMarketValue
import java.time.LocalDate
import com.carlesso.goalfather.domain.rules.TICKET_PRICE_RANGE
import com.carlesso.goalfather.domain.rules.marketListingPrice
import com.carlesso.goalfather.domain.rules.marketReferenceValue
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O mundo do MVP montado a partir do pack REAL (`data/world/v1`), sem Spring
 * (issue #131): competições, clubes, elencos, ids e determinismo. Roda com
 * diretório de trabalho em `backend/`, como os testes do importador.
 */
class WorldSeedTest {

    private companion object {
        /** Carregado uma vez para a classe: ler ~160 arquivos por teste seria desperdício. */
        val pack: WorldPack = load("../data/world/v1")
        val world: InitialWorld = pack.toInitialWorld()

        fun load(dir: String): WorldPack {
            val result = runBlocking { FileWorldDataProvider(Path.of(dir)).load() }
            assertIs<PackLoadResult.Loaded>(result, "pack inválido: $result")
            return result.pack
        }

        val MVP_COMPETITIONS = listOf(
            "bra-serie-a", "bra-serie-b", "bra-copa-do-brasil", "eng-premier-league",
            "esp-la-liga", "ita-serie-a", "ger-bundesliga", "fra-ligue-1",
        )
    }

    @Test
    fun `catalogo tem as 8 competicoes nacionais do MVP e a Libertadores hospedada pelo Brasil`() {
        assertEquals(MVP_COMPETITIONS + "conmebol-libertadores", world.competitions.map { it.id.value })
        assertEquals(2026, world.year)
        val libertadores = world.competitions.last()
        assertEquals(CompetitionKind.Continental, libertadores.kind)
        assertEquals("BRA", libertadores.country.code.value)
        assertEquals(SeasonSpan.CALENDAR_YEAR, libertadores.span)
    }

    @Test
    fun `a Libertadores 2026 nasce sorteada com os 32 do pack e sem compatriotas no grupo`() {
        val edition = world.continentals.single()
        assertEquals(2026, edition.season)
        assertEquals(32, edition.clubs.size)
        assertEquals(6, edition.clubs.count { it.country.value == "BRA" })
        edition.groups.forEach { group -> assertEquals(4, group.clubs.map { it.country }.distinct().size, group.name) }
        // Sede da final: o maior estádio entre os participantes.
        assertEquals("Estadio Monumental U", edition.finalVenue?.name)
        // A 1ª edição entra no plano do mundo já em 2026.
        assertEquals(edition, world.start().continentals.single())
    }

    @Test
    fun `clubes so da Libertadores entram depois dos de liga, sem liga e com elenco`() {
        val onlyContinental = world.clubs.drop(136)
        assertEquals(26, onlyContinental.size)
        assertTrue(onlyContinental.none { it.country.code.value == "BRA" })
        assertTrue(onlyContinental.all { world.competitions.leagueOf(it) == null })
        assertTrue(onlyContinental.all { it.squad.size >= 11 && it.squad.any { p -> p.position == Position.GK } })
    }

    @Test
    fun `as 7 ligas tem 20-20-20-20-20-18-18 clubes`() {
        val sizes = world.clubs.groupingBy { world.competitions.leagueOf(it)?.id?.value ?: "sem liga" }.eachCount()
        assertEquals(
            mapOf(
                "bra-serie-a" to 20, "bra-serie-b" to 20, "eng-premier-league" to 20, "esp-la-liga" to 20,
                "ita-serie-a" to 20, "ger-bundesliga" to 18, "fra-ligue-1" to 18, "sem liga" to 26,
            ),
            sizes,
        )
        // 136 de liga + 26 que só jogam a Libertadores (issue #132).
        assertEquals(162, world.clubs.size)
    }

    @Test
    fun `copa do brasil tem os 40 clubes de A e B e o formato 2026 recortado`() {
        val copa = pack.competitions.single { it.id == CompetitionId("bra-copa-do-brasil") }
        val serieA = pack.competitions.single { it.id == CompetitionId("bra-serie-a") }
        val serieB = pack.competitions.single { it.id == CompetitionId("bra-serie-b") }
        assertEquals(serieA.clubIds + serieB.clubIds, copa.clubIds)

        val format = assertIs<CompetitionFormat.Knockout>(copa.competition.format)
        assertEquals(40, format.entrants)
        assertEquals(listOf(16, 32, 16, 8, 4, 2), format.stages.map { it.teams })
        assertEquals(listOf(16, 24, 0, 0, 0, 0), format.stages.map { it.newEntrants })
        assertEquals(listOf(1, 2, 2, 2, 2, 1), format.stages.map { it.legs.matches })

        val game = world.competitions.single { it.id == copa.id }
        assertEquals(CompetitionKind.NationalCup, game.kind)
        // Os 40 participantes estão no save: são exatamente os clubes brasileiros.
        assertEquals(40, world.clubs.count { it.country.code.value == "BRA" })
    }

    @Test
    fun `regras reais de cada competicao`() {
        val byId = world.competitions.associateBy { it.id.value }
        for (id in listOf("bra-serie-a", "bra-serie-b")) {
            val league = byId.getValue(id)
            assertEquals(9, league.competition.foreignPlayerLimit, id)
            assertEquals(false, league.pausesOnFifaWindows, "$id não para nas datas FIFA")
            assertEquals(SeasonSpan.CALENDAR_YEAR, league.span)
            assertEquals(CompetitionFormat.RoundRobin(legs = 2), league.competition.format)
        }
        assertEquals(CompetitionKind.League(Division(2)), byId.getValue("bra-serie-b").kind)
        assertEquals(9, byId.getValue("bra-copa-do-brasil").competition.foreignPlayerLimit)
        assertTrue(byId.getValue("bra-copa-do-brasil").pausesOnFifaWindows)
        for (id in MVP_COMPETITIONS.filterNot { it.startsWith("bra-") }) {
            val league = byId.getValue(id)
            assertNull(league.competition.foreignPlayerLimit, "$id: sem limite no MVP")
            assertTrue(league.pausesOnFifaWindows, "$id para nas datas FIFA")
            assertEquals(SeasonSpan.AUGUST_TO_MAY, league.span)
            assertEquals("2026/27", league.seasonOf(world.year).label)
        }
        assertEquals("2026", byId.getValue("bra-serie-a").seasonOf(world.year).label)
    }

    @Test
    fun `todo clube tem ao menos 11 jogadores e goleiro`() {
        world.clubs.forEach { club ->
            assertTrue(club.squad.size >= 11, "${club.name}: ${club.squad.size} jogadores")
            assertTrue(club.squad.any { it.position == Position.GK }, "${club.name} sem goleiro")
        }
    }

    @Test
    fun `ids de clube e de jogador sao unicos, inclusive o mercado`() {
        assertEquals(world.clubs.size, world.clubs.map { it.id }.toSet().size)
        assertEquals((1L..world.clubs.size).toList(), world.clubs.map { it.id.value })
        val players = world.clubs.flatMap { it.squad } + world.market.map { it.player }
        assertEquals(players.size, players.map { it.id }.toSet().size)
        // Convenção `clube × 1000 + slot`: o id do jogador diz de que clube ele saiu.
        world.clubs.forEach { club -> club.squad.forEach { assertEquals(club.id.value, it.id.value / PLAYER_ID_BLOCK) } }
    }

    @Test
    fun `duas execucoes do seed sobre o mesmo pack dao o mesmo mundo`() {
        assertEquals(world, load("../data/world/v1").toInitialWorld())
    }

    @Test
    fun `economia inicial sai das regras e escala com a reputacao`() {
        world.clubs.forEach { club ->
            assertTrue(club.cash.isPositive, "${club.name} sem caixa")
            assertTrue(club.ticketPriceCents in TICKET_PRICE_RANGE, "${club.name}: ingresso ${club.ticketPriceCents}")
            assertTrue(club.stadiumCapacity >= 1_000, "${club.name}: estádio do pack")
            assertTrue(club.squad.all { it.salary.isPositive })
        }
        val byName = world.clubs.associateBy { it.name }
        // Mesma liga, reputações opostas: o grande nasce mais rico e cobra mais caro.
        val big = byName.getValue("Flamengo")
        val small = byName.getValue("Mirassol")
        assertTrue(big.cash > small.cash)
        assertTrue(big.ticketPriceCents > small.ticketPriceCents)
    }

    @Test
    fun `mercado inicial tem livres gerados com preco de tabela`() {
        assertEquals(16, world.market.size)
        world.market.forEach { entry ->
            assertEquals(marketListingPrice(marketReferenceValue(entry.player)), entry.price)
            assertTrue(entry.player.age in 20..32)
        }
        assertEquals(setOf(Position.GK, Position.CB, Position.MF, Position.FW), world.market.map { it.player.position }.toSet())
    }

    @Test
    fun `pack reduzido do e2e monta 4 competicoes e 16 clubes`() {
        val e2e = load("../data/world/e2e/v1").toInitialWorld()
        assertEquals(listOf("bra-serie-a", "bra-serie-b", "bra-copa-do-brasil", "eng-premier-league"), e2e.competitions.map { it.id.value })
        assertEquals(16, e2e.clubs.size)
        val sizes = e2e.clubs.groupingBy { e2e.competitions.leagueOf(it)?.id?.value ?: "sem liga" }.eachCount()
        assertEquals(mapOf("bra-serie-a" to 6, "bra-serie-b" to 6, "eng-premier-league" to 4), sizes)
    }

    @Test
    fun `a escalacao automatica de todo clube tem um goleiro so`() {
        // Os 11 primeiros do elenco são o time automático (`Club.startingLineup`);
        // o pack lista os goleiros primeiro, e o seed reordena (issue #131).
        for (club in world.clubs) {
            val starters = club.squad.take(11)
            assertEquals(1, starters.count { it.position == Position.GK }, club.name)
            assertEquals(club.squad.map { it.id }.sortedBy { it.value }, club.squad.map { it.id }, "ids na ordem do elenco: ${club.name}")
        }
    }

    @Test
    fun `ligas do catalogo carregam a forca do pack e copas nao (issue 135)`() {
        val byId = world.competitions.associateBy { it.id.value }
        assertEquals(LeagueStrength(70), byId.getValue("bra-serie-a").strength)
        assertEquals(LeagueStrength(62), byId.getValue("bra-serie-b").strength)
        assertEquals(LeagueStrength(78), byId.getValue("eng-premier-league").strength)
        assertNull(byId.getValue("bra-copa-do-brasil").strength)
        assertNull(byId.getValue("conmebol-libertadores").strength)
    }

    @Test
    fun `valores do mundo inicial na ordem de grandeza real em USD (issue 135)`() {
        val today = LocalDate.of(world.year, 7, 1)
        val squadValues = world.clubs.map { club ->
            club.squad.sumOfMoney { squadMarketValue(it, club, world.competitions, today) }.cents / 100
        }
        // O elenco mais caro (um grande da Premier) vale perto de US$ 1 bi; o
        // mais barato (Série B), alguns milhões — a ordem de grandeza real.
        assertTrue(squadValues.max() in 500_000_000L..2_000_000_000L, "maior elenco: US$ ${squadValues.max()}")
        assertTrue(squadValues.min() in 1_000_000L..20_000_000L, "menor elenco: US$ ${squadValues.min()}")
    }

    @Test
    fun `todo clube da Serie A consegue comprar o livre mais barato do mercado inicial`() {
        // Até a #136 derivar salários (e, com eles, o caixa inicial) do valor,
        // o caixa segue na escala antiga: um pequeno da Série B não compra
        // ninguém. Esta trava garante que o mercado não nasce inacessível para
        // a liga padrão do onboarding — o E2E de compra depende disso.
        for (initial in listOf(world, load("../data/world/e2e/v1").toInitialWorld())) {
            val cheapest = initial.market.minOf { it.price }
            val poorest = initial.clubs.filter { initial.competitions.leagueOf(it)?.id?.value == "bra-serie-a" }.minBy { it.cash }
            assertTrue(cheapest <= poorest.cash, "${poorest.name} tem ${poorest.cash}, o livre mais barato custa $cheapest")
        }
    }
}
