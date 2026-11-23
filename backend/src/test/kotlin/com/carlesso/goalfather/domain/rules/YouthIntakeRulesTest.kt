package com.carlesso.goalfather.domain.rules

import com.carlesso.goalfather.domain.model.ClubId
import com.carlesso.goalfather.domain.model.Countries
import com.carlesso.goalfather.domain.model.Position
import com.carlesso.goalfather.domain.model.SeasonSpan
import com.carlesso.goalfather.domain.model.world.LeagueStrength
import com.carlesso.goalfather.domain.model.world.Reputation
import com.carlesso.goalfather.test.TEST_NAMES
import com.carlesso.goalfather.test.makeClub
import com.carlesso.goalfather.test.makePlayer
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A base de cada virada (issue #139), sem Spring: só a regra pura. */
class YouthIntakeRulesTest {

    private val strength = LeagueStrength(70)
    private val club = makeClub(id = 4, squadSize = 20, overall = 72).copy(country = Countries.SPAIN)

    @Test
    fun `sobem de 2 a 4 garotos de 16 a 18 anos do pais do clube com contrato de base`() {
        for (season in 2027..2060) {
            val youths = youthIntake(club, season, strength, TEST_NAMES)

            assertTrue(youths.size in YOUTH_INTAKE_SIZE, "temporada $season: ${youths.size}")
            youths.forEach { youth ->
                assertTrue(youth.age in YOUTH_INTAKE_AGES, "idade ${youth.age}")
                assertEquals(Countries.SPAIN, youth.nationality)
                val contract = requireNotNull(youth.contract)
                assertEquals(ClubId(4), contract.clubId)
                assertEquals(YOUTH_SALARY_CENTS, contract.salary)
                // Contrato da base: 3 temporadas na janela da liga (a Espanha é ago–mai).
                assertEquals(SeasonSpan.of(Countries.SPAIN.confederation).end(season + 2), contract.end)
            }
        }
    }

    @Test
    fun `nomes saem do pool e nao repetem o elenco`() {
        val taken = club.copy(squad = club.squad.mapIndexed { i, p -> p.copy(name = "Garoto ${i + 1}") })

        val youths = youthIntake(taken, 2027, strength, TEST_NAMES)

        assertTrue(youths.none { y -> taken.squad.any { it.name == y.name } }, "nome repetido: $youths")
        assertEquals(youths.size, youths.map { it.name }.toSet().size)
    }

    @Test
    fun `mesma temporada e clube dao os mesmos garotos, outra temporada da outros`() {
        assertEquals(youthIntake(club, 2027, strength, TEST_NAMES), youthIntake(club, 2027, strength, TEST_NAMES))
        assertNotEquals(youthIntake(club, 2027, strength, TEST_NAMES), youthIntake(club, 2028, strength, TEST_NAMES))
        // Ids da faixa da base, um por vaga — não colidem entre temporadas.
        val ids = (2027..2040).flatMap { s -> youthIntake(club, s, strength, TEST_NAMES).map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `clube humano recebe so ate o teto do elenco`() {
        val full = makeClub(id = 1, squadSize = MAX_SQUAD_SIZE, ownerId = 7)
        val almost = makeClub(id = 1, squadSize = MAX_SQUAD_SIZE - 1, ownerId = 7)

        assertTrue(youthIntake(full, 2027, strength, TEST_NAMES, maxSquadSize = MAX_SQUAD_SIZE).isEmpty())
        assertEquals(1, youthIntake(almost, 2027, strength, TEST_NAMES, maxSquadSize = MAX_SQUAD_SIZE).size)
    }

    @Test
    fun `a posicao do garoto e a mais carente do elenco`() {
        // Elenco de 11 meias: o maior déficit é de zagueiro (8/25 do elenco), e
        // nenhum garoto é meia.
        val midfielders = makeClub(id = 9, squadSize = 11)
        val youths = youthIntake(midfielders, 2027, strength, TEST_NAMES)
        assertEquals(Position.CB, youths.first().position)
        assertTrue(youths.none { it.position == Position.MF }, "${youths.map { it.position }}")

        // Elenco completo sem goleiro: o garoto é goleiro.
        val noKeeper = listOf(Position.CB to 8, Position.MF to 8, Position.FW to 6)
            .flatMap { (position, n) -> List(n) { position } }
            .mapIndexed { i, position -> makePlayer(i + 1L, position = position) }
        assertEquals(Position.GK, neediestPosition(noKeeper))
    }

    @Test
    fun `clube de elenco forte forma garotos melhores`() {
        val strong = makeClub(id = 5, squadSize = 20, overall = 85)
        val weak = makeClub(id = 5, squadSize = 20, overall = 60)
        fun averageYouth(c: com.carlesso.goalfather.domain.model.Club) =
            (2027..2076).flatMap { youthIntake(c, it, strength, TEST_NAMES) }.map { it.overall }.average()

        assertTrue(averageYouth(strong) > averageYouth(weak) + 10, "${averageYouth(strong)} × ${averageYouth(weak)}")
    }

    @Test
    fun `reputacao inverte a conta do gerador de atributos`() {
        // Os 11 melhores com 75 numa liga de 70: 5 acima ⇒ reputação 50 + 25.
        val squad = (1L..15L).map { makePlayer(it, overall = if (it <= 11) 75 else 50) }
        assertEquals(Reputation(75), reputationOf(squad, LeagueStrength(70)))
        // Fora da escala do pack, trunca nas pontas.
        assertEquals(Reputation(1), reputationOf(emptyList(), LeagueStrength(70)))
        assertEquals(Reputation(100), reputationOf((1L..11L).map { makePlayer(it, overall = 99) }, LeagueStrength(40)))
    }

    @Test
    fun `contrato da base respeita o limite do menor de idade`() {
        val youth = youthIntake(club, 2027, strength, TEST_NAMES).first()
        val contract = requireNotNull(youth.contract)
        val seasons = contract.end.year - LocalDate.of(2027, 1, 1).year
        assertTrue(seasons <= MAX_MINOR_CONTRACT_SEASONS, "contrato de $seasons temporadas para um garoto de ${youth.age}")
    }
}
