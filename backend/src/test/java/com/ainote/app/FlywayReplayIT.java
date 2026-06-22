package com.ainote.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.ResultSet;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Flyway replay")
class FlywayReplayIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("ainote_flyway_replay")
            .withUsername("ainote")
            .withPassword("ainote");

    @Test
    @DisplayName("replays every migration into a fresh pgvector database")
    void replaysEveryMigrationIntoFreshDatabase() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();

        var result = flyway.migrate();
        flyway.validate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThan(0);
        assertTaskPlansErrorMessageExists();
    }

    private static void assertTaskPlansErrorMessageExists() throws Exception {
        try (var connection = DriverManager.getConnection(
                jdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
             var statement = connection.prepareStatement("""
                     SELECT data_type
                     FROM information_schema.columns
                     WHERE table_name = 'task_plans'
                       AND column_name = 'error_message'
                     """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("data_type")).isEqualTo("text");
            }
        }
    }

    private static String jdbcUrl() {
        String url = postgres.getJdbcUrl();
        return url.contains("?") ? url + "&stringtype=unspecified" : url + "?stringtype=unspecified";
    }
}
