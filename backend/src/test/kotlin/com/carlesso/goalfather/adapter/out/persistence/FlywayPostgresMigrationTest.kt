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
    fun `cadeia completa migra em Postgres preservando dados pre-divisoes e pre-fadiga`() {
        // 1. Banco "de produção" pré-divisões: migra até a V6 e povoa.
        flyway(target = "6").migrate()
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { st ->
                st.execute("INSERT INTO clubs (id, name, cash, stadium_capacity) VALUES (1, 'Legado FC', 100, 5000)")
                st.execute("INSERT INTO standings (season, round_number, rows_json) VALUES (2025, 3, '[]')")
                // Dois jogadores legados: um íntegro e um lesionado — a V8 troca
                // o booleano eterno por duração em rodadas (issue #54).
                st.execute(
                    "INSERT INTO players (id, club_id, name, position, overall, pace, shooting, " +
                        "passing, defending, stamina, salary, age, goals, yellow_cards, red_cards, injured) " +
                        "VALUES (1, 1, 'Sadio', 'MF', 70, 70, 70, 70, 70, 100, 1000, 25, 0, 0, 0, FALSE)",
                )
                st.execute(
                    "INSERT INTO players (id, club_id, name, position, overall, pace, shooting, " +
                        "passing, defending, stamina, salary, age, goals, yellow_cards, red_cards, injured) " +
                        "VALUES (2, 1, 'Machucado', 'FW', 75, 75, 75, 75, 75, 60, 2000, 28, 3, 1, 0, TRUE)",
                )
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

                // 4. Semântica da V8: `injured` vira duração em rodadas. Quem
                //    estava lesionado herda 1 rodada; os demais ficam aptos.
                val players = st.executeQuery(
                    "SELECT id, injured_for_rounds FROM players ORDER BY id",
                )
                assertTrue(players.next(), "jogador legado deveria sobreviver à V8")
                assertEquals(0, players.getInt("injured_for_rounds"), "jogador íntegro fica apto")
                assertTrue(players.next())
                assertEquals(1, players.getInt("injured_for_rounds"), "lesionado herda 1 rodada")
            }
        }
    }
}
