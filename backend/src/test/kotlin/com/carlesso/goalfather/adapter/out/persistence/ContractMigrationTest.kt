package com.carlesso.goalfather.adapter.out.persistence

import org.flywaydb.core.Flyway
import java.sql.DriverManager
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Semântica da V23 (issue #136) no caminho de upgrade, em H2 `MODE=PostgreSQL`
 * (a sintaxe em Postgres real fica com o `FlywayPostgresMigrationTest`, que
 * roda a cadeia inteira no CI): um save migrado até a V22, com jogadores em
 * elencos e no mercado, sai da V23 com contrato padrão para quem tem clube —
 * terminando no fim de temporada da liga do país — e o livre sem contrato.
 */
class ContractMigrationTest {

    private val url = "jdbc:h2:mem:contract-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"

    private fun flyway(target: String? = null): Flyway =
        Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration")
            .apply { target?.let { target(it) } }
            .load()

    @Test
    fun `V23 preserva os jogadores existentes com contrato padrao`() {
        flyway(target = "22").migrate()
        DriverManager.getConnection(url, "sa", "").use { conn ->
            conn.createStatement().use { st ->
                st.execute("INSERT INTO clubs (id, name, cash, stadium_capacity, country) VALUES (1, 'Brasil FC', 100, 5000, 'BRA')")
                st.execute("INSERT INTO clubs (id, name, cash, stadium_capacity, country) VALUES (2, 'London FC', 100, 5000, 'ENG')")
                // O Brasil já está em 2027; a Inglaterra, em 2026 (2026/27).
                listOf("'bra-serie-a', 2027, 'BRA'", "'eng-premier-league', 2026, 'ENG'").forEach { values ->
                    st.execute(
                        "INSERT INTO standings (competition_id, season, country_code, competition_name, season_label, " +
                            "division, round_number, rows_json) VALUES ($values, 'Liga', 'x', 1, 0, '[]')",
                    )
                }
                listOf("1, 1", "2, 2", "3, NULL").forEach { values ->
                    st.execute(
                        "INSERT INTO players (id, club_id, name, position, overall, pace, shooting, passing, defending, " +
                            "stamina, salary, age, goals, yellow_cards, red_cards) " +
                            "VALUES ($values, 'P', 'MF', 70, 70, 70, 70, 70, 100, 500000, 25, 0, 0, 0)",
                    )
                }
            }
        }

        assertTrue(flyway().migrate().success)

        DriverManager.getConnection(url, "sa", "").use { conn ->
            conn.createStatement().use { st ->
                val rows = st.executeQuery("SELECT id, salary, contract_start, contract_end FROM players ORDER BY id")
                // Brasil: 3 temporadas a partir de 2027 → 31/12/2029, salário preservado.
                assertTrue(rows.next())
                assertEquals(500000, rows.getInt("salary"))
                assertEquals(LocalDate.of(2027, 1, 1), rows.getDate("contract_start").toLocalDate())
                assertEquals(LocalDate.of(2029, 12, 31), rows.getDate("contract_end").toLocalDate())
                // Inglaterra: 3 temporadas a partir de 2026/27 → 31/05/2029.
                assertTrue(rows.next())
                assertEquals(LocalDate.of(2029, 5, 31), rows.getDate("contract_end").toLocalDate())
                // Livre do mercado: sem contrato e sem salário.
                assertTrue(rows.next())
                assertEquals(0, rows.getInt("salary"))
                assertNull(rows.getDate("contract_end"))
            }
        }
    }
}
