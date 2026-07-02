package com.ainote.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RAG vector index performance")
@RequiresDocker
class RagVectorIndexPerformanceIT {

    private static final int CHUNK_COUNT = 10_000;
    private static final int DIMENSION = 1024;
    private static final int MEASURED_RUNS = 20;
    private static final long P95_THRESHOLD_MILLIS = 800;

    static final TestDatabaseProperties.Database database =
            TestDatabaseProperties.database("pgvector/pgvector:pg15", "ainote_rag_perf");

    @Test
    @DisplayName("searches ten thousand chunks with HNSW under the release threshold")
    void searchesTenThousandChunksWithHnswUnderThreshold() throws Exception {
        migrate();
        seedChunks();

        String queryVector = vectorLiteral(17);
        String plan = explainSearch(queryVector);
        assertThat(plan).contains("idx_l4j_embeddings_vec");

        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            searchOnce(queryVector);
        }
        for (int i = 0; i < MEASURED_RUNS; i++) {
            durations.add(searchOnce(queryVector));
        }

        Collections.sort(durations);
        long p95 = durations.get((int) Math.ceil(MEASURED_RUNS * 0.95) - 1);
        System.out.println("rag-10k-p95-ms=" + p95);
        System.out.println("rag-10k-plan=" + plan.lines().findFirst().orElse(""));
        assertThat(p95)
                .as("10k local RAG vector search p95, durations=%s, plan=%s", durations, plan)
                .isLessThan(P95_THRESHOLD_MILLIS);
    }

    private static void migrate() {
        Flyway.configure()
                .dataSource(database.jdbcUrl(), database.username(), database.password())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static void seedChunks() throws Exception {
        try (var connection = DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE langchain4j_embeddings");
            statement.execute("""
                    INSERT INTO langchain4j_embeddings (embedding_id, embedding, text, metadata)
                    SELECT gen_random_uuid(),
                           ('[' || array_to_string(
                                ARRAY(
                                    SELECT ((((i * 31) + (d * 17)) % 1000)::double precision / 1000)::text
                                    FROM generate_series(1, 1024) AS d
                                ),
                                ','
                           ) || ']')::vector,
                           'chunk-' || i,
                           json_build_object('userId', 'perf-user', 'noteId', 'perf-note-' || i, 'chunkIndex', i)::json
                    FROM generate_series(1, 10000) AS i
                    """);
            statement.execute("ANALYZE langchain4j_embeddings");
        }

        try (var connection = DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM langchain4j_embeddings")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(CHUNK_COUNT);
        }
    }

    private static String explainSearch(String queryVector) throws Exception {
        try (var connection = DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = off");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    EXPLAIN (ANALYZE, BUFFERS)
                    SELECT embedding_id
                    FROM langchain4j_embeddings
                    ORDER BY embedding <=> CAST(? AS vector)
                    LIMIT 10
                    """)) {
                statement.setString(1, queryVector);
                try (var resultSet = statement.executeQuery()) {
                    StringBuilder plan = new StringBuilder();
                    while (resultSet.next()) {
                        plan.append(resultSet.getString(1)).append('\n');
                    }
                    return plan.toString();
                }
            }
        }
    }

    private static long searchOnce(String queryVector) throws Exception {
        long started = System.nanoTime();
        try (var connection = DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password())) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT embedding_id
                    FROM langchain4j_embeddings
                    ORDER BY embedding <=> CAST(? AS vector)
                    LIMIT 10
                    """)) {
                statement.setString(1, queryVector);
                try (var resultSet = statement.executeQuery()) {
                    int count = 0;
                    while (resultSet.next()) {
                        count++;
                    }
                    assertThat(count).isEqualTo(10);
                }
            }
        }
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private static String vectorLiteral(int seed) {
        StringBuilder builder = new StringBuilder(DIMENSION * 6);
        builder.append('[');
        for (int i = 1; i <= DIMENSION; i++) {
            if (i > 1) {
                builder.append(',');
            }
            builder.append(((seed * 31) + (i * 17)) % 1000 / 1000.0d);
        }
        builder.append(']');
        return builder.toString();
    }
}
