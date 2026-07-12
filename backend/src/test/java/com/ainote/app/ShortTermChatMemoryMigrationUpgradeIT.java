package com.ainote.app;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@RequiresDocker
class ShortTermChatMemoryMigrationUpgradeIT {

    static final TestDatabaseProperties.Database database =
            TestDatabaseProperties.database("pgvector/pgvector:pg15", "ainote_short_memory_upgrade");

    @Test
    void upgradesNullAndDuplicateSequencesWithoutDeletingHistory() throws Exception {
        Flyway.configure()
                .dataSource(database.jdbcUrl(), database.username(), database.password())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("59"))
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(
                database.jdbcUrl(), database.username(), database.password());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO user_memories(user_id, message_type, content, sequence_number, created_at)
                    VALUES ('upgrade-user', 'USER', 'm1', 0, NOW()),
                           ('upgrade-user', 'AI', 'm2', 0, NOW() + INTERVAL '1 second'),
                           ('upgrade-user', 'USER', 'm3', NULL, NOW() + INTERVAL '2 seconds'),
                           ('upgrade-user', 'AI', 'm4', NULL, NOW() + INTERVAL '3 seconds')
                    """);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(database.jdbcUrl(), database.username(), database.password())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        flyway.validate();

        try (var connection = DriverManager.getConnection(
                database.jdbcUrl(), database.username(), database.password())) {
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM user_memories WHERE user_id='upgrade-user'"))
                    .isEqualTo(4);
            assertThat(queryLong(connection, """
                    SELECT COUNT(*) FROM user_memories
                    WHERE user_id='upgrade-user' AND sequence_number IS NULL
                    """)).isZero();
            assertThat(queryLong(connection, """
                    SELECT COUNT(*) FROM (
                        SELECT sequence_number FROM user_memories
                        WHERE user_id='upgrade-user'
                        GROUP BY sequence_number HAVING COUNT(*) > 1
                    ) duplicates
                    """)).isZero();
            assertThat(queryLong(connection, """
                    SELECT next_sequence_number FROM chat_memory_heads
                    WHERE user_id='upgrade-user'
                    """)).isEqualTo(4);
        }
    }

    private long queryLong(java.sql.Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }
}
