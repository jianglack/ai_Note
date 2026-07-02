package com.ainote.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Task schedule runner scan performance")
@RequiresDocker
class TaskScheduleRunnerPerformanceIT {

    private static final int SCHEDULE_COUNT = 10_000;
    private static final int DUE_COUNT = 10;
    private static final int MEASURED_RUNS = 30;
    private static final long P95_THRESHOLD_MILLIS = 1_000;

    static final TestDatabaseProperties.Database database =
            TestDatabaseProperties.database("pgvector/pgvector:pg15", "ainote_schedule_perf");

    @Test
    @DisplayName("scans ten thousand schedules for thirty ticks using the due-scan index")
    void scansTenThousandSchedulesForThirtyTicksUsingIndex() throws Exception {
        migrate();
        seedSchedules();

        LocalDateTime now = LocalDateTime.parse("2026-06-30T10:00:00");
        String plan = explainDueScan(now);
        assertThat(plan).contains("idx_task_schedules_next_run");

        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            scanOnce(now);
        }
        for (int i = 0; i < MEASURED_RUNS; i++) {
            durations.add(scanOnce(now));
        }

        Collections.sort(durations);
        long p95 = durations.get((int) Math.ceil(MEASURED_RUNS * 0.95) - 1);
        System.out.println("scheduler-10k-30tick-p95-ms=" + p95);
        System.out.println("scheduler-10k-plan=" + plan.lines().findFirst().orElse(""));
        assertThat(p95)
                .as("10k task schedule due-scan p95, durations=%s, plan=%s", durations, plan)
                .isLessThan(P95_THRESHOLD_MILLIS);
    }

    private static void migrate() {
        Flyway.configure()
                .dataSource(database.jdbcUrl(), database.username(), database.password())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static void seedSchedules() throws Exception {
        try (var connection = DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE task_schedules");
            statement.execute("""
                    INSERT INTO task_schedules (
                        id,
                        user_id,
                        plan_template_json,
                        original_query,
                        trigger_type,
                        enabled,
                        run_count,
                        next_run_at,
                        created_at,
                        updated_at
                    )
                    SELECT
                        'perf-schedule-' || i,
                        'perf-user',
                        '{}'::jsonb,
                        'scheduled task ' || i,
                        'DAILY',
                        TRUE,
                        0,
                        CASE WHEN i <= 10
                             THEN TIMESTAMP '2026-06-30 09:59:00'
                             ELSE TIMESTAMP '2026-07-01 10:00:00'
                        END,
                        TIMESTAMP '2026-06-30 09:00:00',
                        TIMESTAMP '2026-06-30 09:00:00'
                    FROM generate_series(1, 10000) AS i
                    """);
            statement.execute("ANALYZE task_schedules");
        }

        try (var connection = DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM task_schedules")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(SCHEDULE_COUNT);
        }
    }

    private static String explainDueScan(LocalDateTime now) throws Exception {
        try (var connection = DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = off");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    EXPLAIN (ANALYZE, BUFFERS)
                    SELECT id
                    FROM task_schedules
                    WHERE enabled = TRUE
                      AND next_run_at <= ?
                    """)) {
                statement.setTimestamp(1, Timestamp.valueOf(now));
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

    private static long scanOnce(LocalDateTime now) throws Exception {
        long started = System.nanoTime();
        try (var connection = DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id
                     FROM task_schedules
                     WHERE enabled = TRUE
                       AND next_run_at <= ?
                     """)) {
            statement.setTimestamp(1, Timestamp.valueOf(now));
            try (var resultSet = statement.executeQuery()) {
                int count = 0;
                while (resultSet.next()) {
                    count++;
                }
                assertThat(count).isEqualTo(DUE_COUNT);
            }
        }
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }
}
