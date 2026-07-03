package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryMigrationSqlTest {

    @Test
    void legacySemanticMemoryBackfillMigrationPreservesGovernanceIntent() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V57__backfill_legacy_semantic_memories.sql"));

        assertThat(sql).contains("status = COALESCE(status, 'active')");
        assertThat(sql).contains("source = 'legacy_ai_extracted'");
        assertThat(sql).contains("memory_type = LOWER(category)");
        assertThat(sql).contains("content_hash = md5");
        assertThat(sql).contains("\"legacy\": true");
        assertThat(sql).contains("WHERE metadata_json IS NULL");
    }
}
