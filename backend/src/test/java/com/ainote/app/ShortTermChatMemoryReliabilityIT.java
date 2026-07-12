package com.ainote.app;

import com.ainote.app.memory.ReliableChatMemoryStore;
import com.ainote.app.model.ChatHistoryPage;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.memory.chat-history.model-window-max-messages=128",
        "app.memory.chat-history.compaction-enabled=false",
        "app.rag.query-rewriting.enabled=false"
})
@ActiveProfiles("test")
@RequiresDocker
class ShortTermChatMemoryReliabilityIT {

    private static final int HISTORY_ROWS = 10_000;
    private static final int MODEL_WINDOW_LIMIT = 128;
    private static final int CONCURRENT_TURNS = 32;
    private static final int READ_ITERATIONS = 60;
    private static final String HISTORY_USER = "task8-history-user";
    private static final String CONCURRENT_USER = "task8-concurrent-user";

    static final TestDatabaseProperties.Database database =
            TestDatabaseProperties.database("pgvector/pgvector:pg15", "ainote_short_memory_reliability");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ReliableChatMemoryStore memoryStore;
    @Autowired private AiService aiService;
    @Autowired private UserMemoryRepository memoryRepository;
    @Autowired private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::jdbcUrl);
        registry.add("spring.datasource.username", database::username);
        registry.add("spring.datasource.password", database::password);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.neo4j.enabled", () -> "false");
    }

    @Test
    void passesCapacityConcurrencyAndHistorySeparationGates() throws Exception {
        insertHistoryRows();

        for (int i = 0; i < 5; i++) memoryStore.getMessages(HISTORY_USER);
        List<Long> loadLatenciesMs = new ArrayList<>(READ_ITERATIONS);
        int loadedMessages = 0;
        for (int i = 0; i < READ_ITERATIONS; i++) {
            long started = System.nanoTime();
            loadedMessages = memoryStore.getMessages(HISTORY_USER).size();
            loadLatenciesMs.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }

        HistoryScan historyScan = scanAllVisibleHistory();
        ConcurrentWriteResult concurrent = runConcurrentWrites();
        long p50 = percentile(loadLatenciesMs, 0.50);
        long p95 = percentile(loadLatenciesMs, 0.95);
        boolean gate = loadedMessages <= MODEL_WINDOW_LIMIT
                && historyScan.totalRows() == HISTORY_ROWS
                && historyScan.duplicateIds() == 0
                && concurrent.persistedRows() == CONCURRENT_TURNS * 2
                && concurrent.duplicateSequences() == 0
                && concurrent.sequenceContinuous()
                && p95 < 100;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", "short-term-memory-reliability-v1");
        report.put("status", gate ? "PASSED" : "FAILED");
        report.put("qualityGatePassed", gate);
        report.put("database", "PostgreSQL 15 temporary integration database");
        report.put("historyRows", HISTORY_ROWS);
        report.put("modelWindowLimit", MODEL_WINDOW_LIMIT);
        report.put("modelLoadedMessages", loadedMessages);
        report.put("uiHistoryRowsScanned", historyScan.totalRows());
        report.put("uiHistoryPages", historyScan.pages());
        report.put("uiHistoryDuplicateIds", historyScan.duplicateIds());
        report.put("modelWindowReadIterations", READ_ITERATIONS);
        report.put("modelWindowReadP50Ms", p50);
        report.put("modelWindowReadP95Ms", p95);
        report.put("concurrentTurns", CONCURRENT_TURNS);
        report.put("expectedConcurrentRows", CONCURRENT_TURNS * 2);
        report.put("persistedConcurrentRows", concurrent.persistedRows());
        report.put("duplicateSequences", concurrent.duplicateSequences());
        report.put("sequenceContinuous", concurrent.sequenceContinuous());
        report.put("concurrentWriteDurationMs", concurrent.durationMs());
        Path reportPath = Path.of("target", "short-term-memory-eval",
                "short-term-memory-reliability-report.json");
        Files.createDirectories(reportPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        assertThat(gate).as("short-term memory reliability report: %s", report).isTrue();
    }

    private void insertHistoryRows() {
        LocalDateTime base = LocalDateTime.of(2026, 7, 10, 12, 0);
        jdbcTemplate.batchUpdate("""
                INSERT INTO user_memories(
                    user_id, message_type, content, sequence_number, trimmed_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                statement.setString(1, HISTORY_USER);
                statement.setString(2, index % 2 == 0 ? "USER" : "AI");
                statement.setString(3, "history-message-" + index);
                statement.setInt(4, index);
                if (index < HISTORY_ROWS - 200) {
                    statement.setTimestamp(5, Timestamp.valueOf(base.plusSeconds(index)));
                } else {
                    statement.setTimestamp(5, null);
                }
                statement.setTimestamp(6, Timestamp.valueOf(base.plusSeconds(index)));
            }

            @Override
            public int getBatchSize() { return HISTORY_ROWS; }
        });
        jdbcTemplate.update("""
                INSERT INTO chat_memory_heads(user_id, next_sequence_number, last_compaction_enqueued_sequence)
                VALUES (?, ?, -1)
                """, HISTORY_USER, HISTORY_ROWS);
    }

    private HistoryScan scanAllVisibleHistory() {
        String cursor = null;
        int total = 0;
        int pages = 0;
        HashSet<String> ids = new HashSet<>();
        int duplicateIds = 0;
        do {
            ChatHistoryPage page = aiService.getChatHistoryPage(HISTORY_USER, 200, cursor);
            pages++;
            for (var item : page.getItems()) {
                total++;
                if (!ids.add(item.getId())) duplicateIds++;
            }
            cursor = page.getNextCursor();
            if (!page.isHasMore()) break;
        } while (pages <= 100);
        return new HistoryScan(total, pages, duplicateIds);
    }

    private ConcurrentWriteResult runConcurrentWrites() throws Exception {
        var executor = Executors.newFixedThreadPool(CONCURRENT_TURNS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_TURNS);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        long started = System.nanoTime();
        try {
            for (int i = 0; i < CONCURRENT_TURNS; i++) {
                int turn = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    aiService.saveChatTurn(CONCURRENT_USER, "question-" + turn, "answer-" + turn);
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        var rows = memoryRepository.findAllByUserIdOrderByCreatedAtAsc(CONCURRENT_USER);
        List<Integer> sequences = rows.stream().map(row -> row.getSequenceNumber()).toList();
        int duplicates = sequences.size() - new HashSet<>(sequences).size();
        boolean continuous = sequences.equals(IntStream.range(0, CONCURRENT_TURNS * 2).boxed().toList());
        return new ConcurrentWriteResult(
                rows.size(), duplicates, continuous,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    private long percentile(List<Long> values, double quantile) {
        List<Long> sorted = values.stream().sorted().toList();
        int index = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * quantile) - 1);
        return sorted.get(Math.max(0, index));
    }

    private record HistoryScan(int totalRows, int pages, int duplicateIds) {}
    private record ConcurrentWriteResult(
            int persistedRows,
            int duplicateSequences,
            boolean sequenceContinuous,
            long durationMs) {}
}
