package com.wingedsheep.gameserver.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager

/**
 * Proves the V1 migration is valid PostgreSQL and the schema supports the account/deck/stats
 * round-trip — including the win-count query backing [MatchResultRepository.countWinsForUser].
 *
 * Self-skips when Docker is unavailable, so CI/dev boxes without Docker still pass.
 */
class FlywayMigrationTest : FunSpec({

    val dockerAvailable = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)

    test("V1 migration applies and supports account/deck/stats round-trips").config(enabled = dockerAvailable) {
        val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine"))
        postgres.start()
        try {
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery(
                        """
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_name IN ('users','login_tokens','decks','match_results','match_participants')
                        """.trimIndent()
                    ).use { rs ->
                        rs.next()
                        rs.getInt(1) shouldBe 5
                    }

                    st.execute("INSERT INTO users(id, email, display_name) VALUES (1, 'a@test.com', 'a')")
                    st.execute("INSERT INTO decks(user_id, name, format, data) VALUES (1, 'My Deck', 'STANDARD', '{}')")
                    st.execute("INSERT INTO match_results(id, game_id) VALUES (10, 'g1')")
                    st.execute("INSERT INTO match_participants(match_id, user_id, player_name, won) VALUES (10, 1, 'a', true)")
                    st.execute("INSERT INTO match_participants(match_id, user_id, player_name, won) VALUES (10, NULL, 'guest', false)")

                    st.executeQuery("SELECT count(*) FROM match_participants WHERE user_id = 1 AND won = true").use { rs ->
                        rs.next()
                        rs.getInt(1) shouldBe 1
                    }
                    st.executeQuery("SELECT count(*) FROM decks WHERE user_id = 1").use { rs ->
                        rs.next()
                        rs.getInt(1) shouldBe 1
                    }
                }
            }
        } finally {
            postgres.stop()
        }
    }
})
