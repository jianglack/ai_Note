package com.ainote.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.ResultSet;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Flyway replay")
@RequiresDocker
class FlywayReplayIT {

    static final TestDatabaseProperties.Database database =
            TestDatabaseProperties.database("pgvector/pgvector:pg16", "ainote_flyway_replay");

    @Test
    @DisplayName("replays every migration into a fresh pgvector database")
    void replaysEveryMigrationIntoFreshDatabase() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(database.jdbcUrl(), database.username(), database.password())
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
                database.jdbcUrl(),
                database.username(),
                database.password());
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

}
