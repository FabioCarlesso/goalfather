package com.carlesso.goalfather.tools.worldimport.assemble

import com.carlesso.goalfather.adapter.out.worlddata.dto.ClubDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.CompetitionDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.FormatDto
import com.carlesso.goalfather.adapter.out.worlddata.dto.StadiumDto
import com.carlesso.goalfather.tools.worldimport.config.ClubOverride
import com.carlesso.goalfather.tools.worldimport.config.ClubSource
import com.carlesso.goalfather.tools.worldimport.config.CompetitionSource
import com.carlesso.goalfather.tools.worldimport.config.CompetitionsFile
import com.carlesso.goalfather.tools.worldimport.config.CuratedInputs
import com.carlesso.goalfather.tools.worldimport.config.ImportSettings
import com.carlesso.goalfather.tools.worldimport.config.Overrides
import com.carlesso.goalfather.tools.worldimport.config.PlayerOverride
import com.carlesso.goalfather.tools.worldimport.wikidata.CapacityClaim
import com.carlesso.goalfather.tools.worldimport.wikidata.ClubFacts
import com.carlesso.goalfather.tools.worldimport.wikidata.CountryFacts
import com.carlesso.goalfather.tools.worldimport.wikidata.PlayerFacts
import com.carlesso.goalfather.tools.worldimport.wikidata.VenueClaim
import com.carlesso.goalfather.tools.worldimport.wikidata.WikidataSnapshot
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O assembler é puro: dá para testar todas as regras de escolha com um
 * snapshot montado à mão, sem rede e sem Spring.
 */
class PackAssemblerTest {

    private val settings = ImportSettings(
        referenceDate = "2026-07-01",
        minMembershipStart = "2019-01-01",
        minBirthDate = "1984-01-01",
        minSquadSize = 22,
        minGoalkeepers = 2,
        seed = 127,
    )

    private val liga = CompetitionSource(
        competition = CompetitionDto(
            id = "bra-liga",
            name = "Liga",
            country = "BRA",
            tier = 1,
            strength = 70,
            format = FormatDto.RoundRobin(2),
        ),
        reference = "teste",
        clubs = listOf(ClubSource("bra-alfa", name = "Alfa", wikidata = "Q1"), ClubSource("bra-beta", wikidata = "Q2")),
    )
    private val copa = CompetitionSource(
        competition = CompetitionDto(id = "con-copa", name = "Copa", confederation = "CONMEBOL", format = FormatDto.RoundRobin(1)),
        reference = "teste",
        clubs = listOf(ClubSource("bra-alfa"), ClubSource("arg-gama", wikidata = "Q3", country = "ARG")),
    )

    private fun inputs(overrides: Overrides = Overrides(), reputation: Map<String, Int> = REPUTATION) =
        CuratedInputs(CompetitionsFile(settings, listOf(liga, copa)), overrides, reputation)

    private fun club(qid: String, label: String, venues: List<VenueClaim>) =
        ClubFacts(qid, labels = mapOf("pt" to label, "es" to label), venues = venues)

    private fun venue(qid: String, name: String, capacity: Int, preferred: Boolean = false, end: String? = null) =
        VenueClaim(qid, preferred, start = null, end = end, labels = mapOf("pt" to name, "es" to name),
            capacities = listOf(CapacityClaim(capacity, preferred = false, pointInTime = null)))

    private fun player(
        qid: String,
        club: String?,
        start: String = "2024-01-01",
        name: String = "Jogador $qid",
        sport: List<String> = listOf("Q155"),
        citizenships: List<String> = emptyList(),
        positions: List<String> = listOf("Q193592"),
    ) = PlayerFacts(
        qid = qid,
        clubQid = club,
        membershipStart = start,
        birthDate = LocalDate.of(1998, 5, 10),
        sportCountries = sport,
        citizenships = citizenships,
        positions = positions,
        labels = mapOf("en" to name),
    )

    private fun snapshot(memberships: List<PlayerFacts>, playersById: Map<String, PlayerFacts> = emptyMap()) =
        WikidataSnapshot(
            countries = listOf(
                CountryFacts("BRA", "Q155", "Q155", "CONMEBOL", "BR", null, "Brasil"),
                CountryFacts("ARG", "Q414", "Q414", "CONMEBOL", "AR", null, "Argentina"),
                CountryFacts("ITA", "Q38", "Q38", "UEFA", "IT", null, "Itália"),
                // Inglaterra e Escócia: o P17 (Reino Unido) é ambíguo e não pode virar país.
                CountryFacts("ENG", "Q21", "Q145", "UEFA", null, "GB-ENG", "Inglaterra"),
                CountryFacts("SCO", "Q22", "Q145", "UEFA", null, "GB-SCT", "Escócia"),
            ),
            positionRoots = mapOf(
                "Q201330" to setOf("GK"),
                "Q336286" to setOf("DF"),
                "Q193592" to setOf("MF"),
                "Q280658" to setOf("FW"),
                "Q-ala" to setOf("DF", "MF"),
            ),
            clubs = mapOf(
                "Q1" to club(
                    "Q1",
                    label = "Alfa Futebol Clube",
                    venues = listOf(
                        venue("Q901", "Estádio Velho", 10_000, end = "2010-01-01T00:00:00Z"),
                        venue("Q902", "Arena Alfa", 40_000, preferred = true),
                        venue("Q903", "Estádio Reserva", 5_000),
                        venue("Q900", "Capacidade digitada errado", 60, preferred = true),
                    ),
                ),
                "Q2" to club("Q2", label = "Beta EC", venues = listOf(venue("Q904", "Beta Park", 20_000))),
                "Q3" to club("Q3", label = "Club Gama", venues = emptyList()),
            ),
            memberships = memberships,
            playersById = playersById,
            retrievedAt = Instant.parse("2026-11-13T10:00:00Z"),
        )

    private fun assembled(result: AssemblyResult): AssemblyResult.Assembled {
        assertIs<AssemblyResult.Assembled>(result, "esperava Assembled, veio $result")
        return result
    }

    private val stadiumOverride = Overrides(clubs = mapOf("arg-gama" to ClubOverride(stadium = StadiumDto("Estadio Gama", 15_000))))

    @Test
    fun `monta clubes competicoes e paises com as regras de escolha`() {
        val result = assembled(PackAssembler(inputs(stadiumOverride), snapshot(emptyList())).assemble())
        val pack = result.pack

        val alfa = pack.clubsByCompetition.getValue("bra-liga").first()
        assertEquals("Alfa", alfa.name, "nome de exibição curado")
        assertEquals(StadiumDto("Arena Alfa", 40_000), alfa.stadium, "estádio atual, rank preferido, capacidade plausível")
        assertEquals(90, alfa.reputation)
        assertEquals("Beta EC", pack.clubsByCompetition.getValue("bra-liga")[1].name, "sem nome curado, o rótulo do Wikidata")

        // A copa referencia o clube da liga só pelo id e define o argentino.
        assertEquals(listOf(ClubDto("bra-alfa"), pack.clubsByCompetition.getValue("con-copa")[1]), pack.clubsByCompetition.getValue("con-copa"))
        assertEquals("ARG", pack.clubsByCompetition.getValue("con-copa")[1].country)

        assertEquals(listOf("ARG", "BRA"), pack.countries.map { it.code }, "só países usados, em ordem")
        assertTrue(pack.squads.values.all { squad -> squad.size == 22 && squad.count { it.position == "GK" } >= 2 })
        assertEquals(3 * 22, result.report.generatedPlayers)
    }

    @Test
    fun `jogador atual em dois clubes fica no vinculo mais recente`() {
        val memberships = listOf(
            player("Q100", "Q1", start = "2020-01-01", name = "Duplo"),
            player("Q100", "Q2", start = "2025-07-01", name = "Duplo"),
        )

        val pack = assembled(PackAssembler(inputs(stadiumOverride), snapshot(memberships)).assemble()).pack

        assertTrue(pack.squads.getValue("bra-alfa").none { it.name == "Duplo" })
        assertEquals("duplo", pack.squads.getValue("bra-beta").single { it.name == "Duplo" }.id)
    }

    @Test
    fun `nacionalidade esportiva primeiro e cidadania como segunda`() {
        val memberships = listOf(
            player("Q101", "Q1", name = "Ítalo-brasileiro", sport = listOf("Q155"), citizenships = listOf("Q155", "Q38")),
            player("Q102", "Q1", name = "Inglês", sport = listOf("Q21"), citizenships = listOf("Q145")),
            player("Q103", "Q1", name = "Britânico sem P1532", sport = emptyList(), citizenships = listOf("Q145")),
        )

        val result = assembled(PackAssembler(inputs(stadiumOverride), snapshot(memberships)).assemble())
        val squad = result.pack.squads.getValue("bra-alfa")

        val italo = squad.single { it.name == "Ítalo-brasileiro" }
        assertEquals("BRA", italo.nationality)
        assertEquals("ITA", italo.secondNationality)
        assertEquals("ENG", squad.single { it.name == "Inglês" }.nationality)
        assertNull(squad.firstOrNull { it.name == "Britânico sem P1532" }, "Reino Unido é ambíguo: descartado")
        assertEquals(1, result.report.dropped["nacionalidade sem seleção conhecida"])
        assertTrue(result.pack.countries.any { it.code == "ITA" }, "país só da segunda nacionalidade também entra")
    }

    @Test
    fun `posicao pela raiz mais citada e descarte sem posicao`() {
        val memberships = listOf(
            player("Q110", "Q1", name = "Lateral", positions = listOf("Q-ala", "Q336286")),
            player("Q111", "Q1", name = "Sem posição", positions = emptyList()),
            player("Q112", "Q1", name = "Goleiro", positions = listOf("Q201330")),
        )

        val result = assembled(PackAssembler(inputs(stadiumOverride), snapshot(memberships)).assemble())
        val squad = result.pack.squads.getValue("bra-alfa")

        assertEquals("DF", squad.single { it.name == "Lateral" }.position)
        assertEquals("GK", squad.first().position)
        assertEquals("Goleiro", squad.first().name, "reais antes dos gerados na mesma posição")
        assertEquals(1, result.report.dropped["sem posição"])
    }

    @Test
    fun `overrides excluem movem e corrigem jogadores`() {
        val overrides = stadiumOverride.copy(
            players = mapOf(
                "Q120" to PlayerOverride(exclude = true),
                "Q121" to PlayerOverride(club = "bra-beta", displayName = "Nome Simulado"),
                "Q122" to PlayerOverride(club = "arg-gama", position = "FW"),
            ),
        )
        val memberships = listOf(player("Q120", "Q1", name = "Saiu"), player("Q121", "Q1", name = "Mudou"))
        val byId = mapOf("Q122" to player("Q122", null, name = "Contratado"))

        val pack = assembled(PackAssembler(inputs(overrides), snapshot(memberships, byId)).assemble()).pack

        assertTrue(pack.squads.values.flatten().none { it.name == "Saiu" })
        assertEquals("Nome Simulado", pack.squads.getValue("bra-beta").single { it.name == "Mudou" }.displayName)
        assertEquals("FW", pack.squads.getValue("arg-gama").single { it.name == "Contratado" }.position)
    }

    @Test
    fun `homonimos ganham o ano de nascimento no id`() {
        val memberships = listOf(
            player("Q130", "Q1", name = "João Silva"),
            player("Q131", "Q2", name = "João Silva").copy(birthDate = LocalDate.of(2001, 1, 1)),
        )

        val pack = assembled(PackAssembler(inputs(stadiumOverride), snapshot(memberships)).assemble()).pack

        assertEquals("joao-silva-1998", pack.squads.getValue("bra-alfa").single { it.name == "João Silva" }.id)
        assertEquals("joao-silva-2001", pack.squads.getValue("bra-beta").single { it.name == "João Silva" }.id)
    }

    @Test
    fun `curadoria incompleta falha listando todos os problemas`() {
        val result = PackAssembler(inputs(reputation = REPUTATION - "bra-beta"), snapshot(emptyList())).assemble()

        val failed = assertIs<AssemblyResult.Failed>(result)
        assertEquals(
            listOf(
                "clube 'bra-beta' sem reputação em club-reputation.yaml",
                "clube 'arg-gama' (Q3) sem estádio com capacidade no Wikidata: use overrides.yaml",
            ),
            failed.errors,
        )
    }

    @Test
    fun `mesma entrada gera exatamente o mesmo pack`() {
        val memberships = (200..230).map { player("Q$it", if (it % 2 == 0) "Q1" else "Q2", name = "Jogador $it") }

        val first = PackAssembler(inputs(stadiumOverride), snapshot(memberships)).assemble()
        val second = PackAssembler(inputs(stadiumOverride), snapshot(memberships.reversed())).assemble()

        assertEquals(first, second, "a ordem das linhas do Wikidata não pode mudar o resultado")
    }

    companion object {
        val REPUTATION = mapOf("bra-alfa" to 90, "bra-beta" to 50, "arg-gama" to 70)
    }
}
