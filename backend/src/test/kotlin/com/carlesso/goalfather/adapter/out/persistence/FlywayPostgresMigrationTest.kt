package com.carlesso.goalfather.adapter.out.persistence

import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Valida a cadeia de migrations contra um Postgres REAL (achado 4 da review
 * do PR da issue #47). O restante da suíte roda em H2 `MODE=PostgreSQL`, que
 * é permissivo — uma sintaxe H2-only em migration nova só estouraria em
 * produção (profile prod usa Postgres).
 *
 * Além da sintaxe, cobre a SEMÂNTICA da V7 no caminho de upgrade: um banco
 * pré-divisões (migrado até a V6, com dados) precisa sair da V7 com os
 * clubes na divisão 1 e o snapshot de standings copiado para a PK composta.
 *
 * `disabledWithoutDocker = true`: sem Docker local o teste é ignorado; no CI
 * (ubuntu-latest) roda de verdade.
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayPostgresMigrationTest {

    companion object {
        @Container
        @JvmStatic
        private val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    private fun flyway(target: String? = null): Flyway =
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .apply { target?.let { target(it) } }
            .load()

    @Test
    fun `cadeia completa migra em Postgres e a V7 preserva dados pre-divisoes`() {
        // 1. Banco "de produção" pré-divisões: migra até a V6 e povoa.
        flyway(target = "6").migrate()
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { st ->
                st.execute("INSERT INTO clubs (id, name, cash, stadium_capacity) VALUES (1, 'Legado FC', 100, 5000)")
                st.execute("INSERT INTO standings (season, round_number, rows_json) VALUES (2025, 3, '[]')")
            }
        }

        // 2. Upgrade até a ponta (V7+) no Postgres real.
        val result = flyway().migrate()
        assertTrue(result.success, "migrate até a versão mais recente deveria passar")

        // 3. Semântica da V7: clube legado cai na divisão 1; snapshot copiado
        //    para a PK composta com vagas zeradas (divisão única: sem zona).
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { st ->
                val club = st.executeQuery("SELECT division FROM clubs WHERE id = 1")
                assertTrue(club.next(), "clube legado deveria sobreviver à V7")
                assertEquals(1, club.getInt("division"))

                val standings = st.executeQuery(
                    "SELECT division, round_number, promotion_spots, relegation_spots " +
                        "FROM standings WHERE season = 2025",
                )
                assertTrue(standings.next(), "snapshot de standings deveria ser copiado pela V7")
                assertEquals(1, standings.getInt("division"))
                assertEquals(3, standings.getInt("round_number"))
                assertEquals(0, standings.getInt("promotion_spots"))
                assertEquals(0, standings.getInt("relegation_spots"))
            }
        }
    }
}
